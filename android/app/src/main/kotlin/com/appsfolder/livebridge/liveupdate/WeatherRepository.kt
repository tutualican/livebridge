package com.appsfolder.livebridge.liveupdate

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.LocationManager
import android.util.Log
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

internal object WeatherRepository {
    private const val TAG = "LiveBridgeWeather"
    private const val LOCATION_MAX_AGE_MS = 6 * 60 * 60 * 1000L
    private const val WEATHER_ENDPOINT = "https://api.open-meteo.com/v1/forecast"
    private const val IP_LOCATION_ENDPOINT = "https://ipwho.is/"
    private val executor = Executors.newSingleThreadExecutor()
    private val refreshing = AtomicBoolean(false)

    fun refreshAsync(context: Context, prefs: ConverterPrefs) {
        if (!refreshing.compareAndSet(false, true)) {
            return
        }
        val appContext = context.applicationContext
        executor.execute {
            try {
                val snapshot = fetchBlocking(appContext, prefs)
                if (snapshot != null) {
                    WeatherLiveUpdate.publish(appContext, prefs, snapshot)
                }
            } catch (error: Throwable) {
                Log.w(TAG, "Weather refresh failed", error)
            } finally {
                refreshing.set(false)
            }
        }
    }

    fun fetchBlocking(context: Context, prefs: ConverterPrefs): WeatherSnapshot? {
        val location = resolveLocation(context, prefs) ?: return null
        val url = buildString {
            append(WEATHER_ENDPOINT)
            append("?latitude=").append(formatCoordinate(location.latitude))
            append("&longitude=").append(formatCoordinate(location.longitude))
            append("&current=temperature_2m,apparent_temperature,weather_code")
            append("&daily=temperature_2m_max,temperature_2m_min")
            append("&timezone=auto&forecast_days=1")
        }
        val root = readJson(url) ?: return null
        val current = root.optJSONObject("current") ?: return null
        val temperature = current.optDouble("temperature_2m", Double.NaN)
        if (temperature.isNaN()) {
            return null
        }
        val daily = root.optJSONObject("daily")
        val snapshot = WeatherSnapshot(
            city = location.city,
            temperatureC = temperature.roundToInt(),
            apparentC = current.optDouble("apparent_temperature", Double.NaN)
                .takeUnless { it.isNaN() }
                ?.roundToInt(),
            weatherCode = current.optInt("weather_code", 0),
            maxC = daily?.optJSONArray("temperature_2m_max")
                ?.optDouble(0, Double.NaN)
                ?.takeUnless { it.isNaN() }
                ?.roundToInt(),
            minC = daily?.optJSONArray("temperature_2m_min")
                ?.optDouble(0, Double.NaN)
                ?.takeUnless { it.isNaN() }
                ?.roundToInt(),
            updatedAtMs = System.currentTimeMillis()
        )
        prefs.setWeatherSnapshotRaw(snapshot.toJson())
        return snapshot
    }

    private fun resolveLocation(context: Context, prefs: ConverterPrefs): WeatherLocation? {
        cachedLocation(prefs)?.let { if (isFresh(it)) return it }
        if (hasLocationPermission(context)) {
            deviceLocation(context)?.let { location ->
                val city = location.city ?: reverseGeocode(context, location.latitude, location.longitude)
                val resolved = location.copy(city = city)
                prefs.setWeatherLocationRaw(resolved.toJson())
                return resolved
            }
        }
        cachedLocation(prefs)?.let { return it }
        return ipLocation(prefs)
    }

    private fun cachedLocation(prefs: ConverterPrefs): WeatherLocation? =
        WeatherLocation.fromJson(prefs.getWeatherLocationRaw())

    private fun isFresh(location: WeatherLocation): Boolean =
        System.currentTimeMillis() - location.updatedAtMs <= LOCATION_MAX_AGE_MS

    private fun hasLocationPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    private fun deviceLocation(context: Context): WeatherLocation? {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return null
        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        )
        for (provider in providers) {
            val location = runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
            if (location != null) {
                return WeatherLocation(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    city = null,
                    updatedAtMs = System.currentTimeMillis()
                )
            }
        }
        return null
    }

    private fun reverseGeocode(context: Context, latitude: Double, longitude: Double): String? {
        return runCatching {
            @Suppress("DEPRECATION")
            Geocoder(context, Locale.getDefault())
                .getFromLocation(latitude, longitude, 1)
                ?.firstOrNull()
                ?.let { it.locality ?: it.subAdminArea ?: it.adminArea }
                ?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    private fun ipLocation(prefs: ConverterPrefs): WeatherLocation? {
        val root = readJson(IP_LOCATION_ENDPOINT) ?: return null
        if (!root.optBoolean("success", false)) {
            return null
        }
        val latitude = root.optDouble("latitude", Double.NaN)
        val longitude = root.optDouble("longitude", Double.NaN)
        if (latitude.isNaN() || longitude.isNaN()) {
            return null
        }
        val location = WeatherLocation(
            latitude = latitude,
            longitude = longitude,
            city = root.optString("city").takeIf { it.isNotBlank() },
            updatedAtMs = System.currentTimeMillis()
        )
        prefs.setWeatherLocationRaw(location.toJson())
        return location
    }

    private fun readJson(url: String): JSONObject? {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 12_000
                requestMethod = "GET"
                setRequestProperty("User-Agent", "LiveBridge-Personal")
            }
            if (connection.responseCode !in 200..299) {
                return null
            }
            connection.inputStream.bufferedReader().use { JSONObject(it.readText()) }
        } catch (error: Throwable) {
            Log.w(TAG, "HTTP request failed: $url", error)
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun formatCoordinate(value: Double): String =
        String.format(Locale.US, "%.4f", value)
}

internal data class WeatherLocation(
    val latitude: Double,
    val longitude: Double,
    val city: String?,
    val updatedAtMs: Long
) {
    fun toJson(): String = JSONObject().apply {
        put("latitude", latitude)
        put("longitude", longitude)
        put("city", city)
        put("updated_at_ms", updatedAtMs)
    }.toString()

    companion object {
        fun fromJson(raw: String?): WeatherLocation? {
            if (raw.isNullOrBlank()) {
                return null
            }
            return runCatching {
                val root = JSONObject(raw)
                WeatherLocation(
                    latitude = root.getDouble("latitude"),
                    longitude = root.getDouble("longitude"),
                    city = root.optString("city").takeIf { it.isNotBlank() },
                    updatedAtMs = root.optLong("updated_at_ms", 0L)
                )
            }.getOrNull()
        }
    }
}
