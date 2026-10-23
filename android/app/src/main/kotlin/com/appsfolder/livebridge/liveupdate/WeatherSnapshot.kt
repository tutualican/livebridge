package com.appsfolder.livebridge.liveupdate

import android.content.Context
import org.json.JSONObject

internal data class WeatherSnapshot(
    val city: String?,
    val temperatureC: Int,
    val apparentC: Int?,
    val weatherCode: Int,
    val maxC: Int?,
    val minC: Int?,
    val updatedAtMs: Long
) {
    fun toJson(): String = JSONObject().apply {
        put("city", city)
        put("temperature_c", temperatureC)
        put("apparent_c", apparentC)
        put("weather_code", weatherCode)
        put("max_c", maxC)
        put("min_c", minC)
        put("updated_at_ms", updatedAtMs)
    }.toString()

    companion object {
        fun fromJson(raw: String?): WeatherSnapshot? {
            if (raw.isNullOrBlank()) {
                return null
            }
            return runCatching {
                val root = JSONObject(raw)
                WeatherSnapshot(
                    city = root.optString("city").takeIf { it.isNotBlank() },
                    temperatureC = root.getInt("temperature_c"),
                    apparentC = root.optInt("apparent_c", Int.MIN_VALUE)
                        .takeIf { it != Int.MIN_VALUE },
                    weatherCode = root.optInt("weather_code", 0),
                    maxC = root.optInt("max_c", Int.MIN_VALUE)
                        .takeIf { it != Int.MIN_VALUE },
                    minC = root.optInt("min_c", Int.MIN_VALUE)
                        .takeIf { it != Int.MIN_VALUE },
                    updatedAtMs = root.optLong("updated_at_ms", 0L)
                )
            }.getOrNull()
        }
    }
}

internal object WeatherConditions {
    fun description(context: Context, code: Int): String = when (code) {
        0 -> NativeAppStrings.text(context, "Clear", "Ясно", "Despejado", "Klar", "Açık")
        1 -> NativeAppStrings.text(context, "Mainly clear", "Преимущественно ясно", "Mayormente despejado", "Überwiegend klar", "Az bulutlu")
        2 -> NativeAppStrings.text(context, "Partly cloudy", "Переменная облачность", "Parcialmente nublado", "Teilweise bewölkt", "Parçalı bulutlu")
        3 -> NativeAppStrings.text(context, "Overcast", "Пасмурно", "Cubierto", "Bedeckt", "Kapalı")
        45, 48 -> NativeAppStrings.text(context, "Fog", "Туман", "Niebla", "Nebel", "Sisli")
        in 51..57 -> NativeAppStrings.text(context, "Drizzle", "Морось", "Llovizna", "Nieselregen", "Çisenti")
        61, 62, 63, 64, 65, 66, 67, 80, 81, 82 ->
            NativeAppStrings.text(context, "Rain", "Дождь", "Lluvia", "Regen", "Yağmurlu")
        71, 72, 73, 74, 75, 76, 77, 85, 86 ->
            NativeAppStrings.text(context, "Snow", "Снег", "Nieve", "Schnee", "Karlı")
        95, 96, 99 -> NativeAppStrings.text(context, "Thunderstorm", "Гроза", "Tormenta", "Gewitter", "Fırtına")
        else -> NativeAppStrings.text(context, "Weather", "Погода", "Clima", "Wetter", "Hava durumu")
    }
}
