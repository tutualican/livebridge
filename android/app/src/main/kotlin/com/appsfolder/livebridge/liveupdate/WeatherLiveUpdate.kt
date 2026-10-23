package com.appsfolder.livebridge.liveupdate

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.text.format.DateFormat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.appsfolder.livebridge.MainActivity
import com.appsfolder.livebridge.R
import java.util.Date

/**
 * Weather live update that is only visible while the device is locked. The snapshot is
 * refreshed by [WeatherRefreshWorker] and cached so the notification can be restored
 * immediately after the screen turns off.
 */
internal object WeatherLiveUpdate {
    private const val CHANNEL_ID = "livebridge_weather"
    private const val NOTIFICATION_ID = 41133
    private const val MAX_SNAPSHOT_AGE_MS = 3 * 60 * 60 * 1000L

    fun publish(
        context: Context,
        prefs: ConverterPrefs,
        snapshot: WeatherSnapshot
    ) {
        if (!shouldRun(prefs)) {
            cancel(context)
            return
        }
        if (!isLocked(context)) {
            cancel(context)
            return
        }
        post(context, snapshot)
    }

    fun syncLockState(context: Context, prefs: ConverterPrefs) {
        if (!shouldRun(prefs)) {
            cancel(context)
            return
        }
        if (!isLocked(context)) {
            cancel(context)
            return
        }
        val snapshot = WeatherSnapshot.fromJson(prefs.getWeatherSnapshotRaw()) ?: run {
            WeatherRepository.refreshAsync(context, prefs)
            return
        }
        post(context, snapshot)
        if (System.currentTimeMillis() - snapshot.updatedAtMs > MAX_SNAPSHOT_AGE_MS) {
            WeatherRepository.refreshAsync(context, prefs)
        }
    }

    fun cancel(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    private fun shouldRun(prefs: ConverterPrefs): Boolean =
        prefs.getConverterEnabled() && prefs.getWeatherLiveUpdateEnabled()

    private fun isLocked(context: Context): Boolean {
        val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            ?: return false
        return keyguard.isKeyguardLocked
    }

    private fun post(context: Context, snapshot: WeatherSnapshot) {
        ensureChannel(context)
        val condition = WeatherConditions.description(context, snapshot.weatherCode)
        val title = snapshot.city ?: NativeAppStrings.text(
            context,
            "Weather",
            "Погода",
            "Clima",
            "Wetter",
            "Hava durumu"
        )
        val summary = if (snapshot.maxC != null && snapshot.minC != null) {
            "$condition · ${snapshot.maxC}° / ${snapshot.minC}°"
        } else {
            condition
        }
        val details = buildList {
            snapshot.apparentC?.let { apparent ->
                add(
                    NativeAppStrings.text(
                        context,
                        "Feels like $apparent°",
                        "Ощущается как $apparent°",
                        "Sensación $apparent°",
                        "Gefühlt $apparent°",
                        "Hissedilen $apparent°"
                    )
                )
            }
            if (snapshot.updatedAtMs > 0L) {
                val time = DateFormat.getTimeFormat(context)
                    .format(Date(snapshot.updatedAtMs))
                add(
                    NativeAppStrings.text(
                        context,
                        "Updated $time",
                        "Обновлено $time",
                        "Actualizado $time",
                        "Aktualisiert $time",
                        "Güncellendi $time"
                    )
                )
            }
        }
        val bigText = (listOf(summary) + details).joinToString(" · ")

        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_weather)
            .setContentTitle(title)
            .setContentText(summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setLocalOnly(
                WearOsLiveUpdatesPolicy.isLocalOnly(
                    Build.VERSION.SDK_INT,
                    ConverterPrefs(context).getWearOsLiveUpdatesEnabled()
                )
            )
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setShortCriticalText("${snapshot.temperatureC}°")
            .setRequestPromotedOngoing(true)

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
        } catch (_: SecurityException) {
            // Notification permission was revoked.
        }
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val name = NativeAppStrings.text(
            context,
            "Weather",
            "Погода",
            "Clima",
            "Wetter",
            "Hava durumu"
        )
        val description = NativeAppStrings.text(
            context,
            "Shows the current weather on the lock screen",
            "Показывает текущую погоду на экране блокировки",
            "Muestra el clima actual en la pantalla de bloqueo",
            "Zeigt das aktuelle Wetter auf dem Sperrbildschirm",
            "Kilit ekranında güncel hava durumunu gösterir"
        )
        val existing = manager.getNotificationChannel(CHANNEL_ID)
        if (existing != null && existing.name == name && existing.description == description) {
            return
        }
        val channel = existing ?: NotificationChannel(
            CHANNEL_ID,
            name,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            setShowBadge(false)
            setSound(null, null)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        channel.name = name
        channel.description = description
        manager.createNotificationChannel(channel)
    }
}
