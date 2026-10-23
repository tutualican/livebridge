package com.appsfolder.livebridge.liveupdate

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.appsfolder.livebridge.MainActivity
import com.appsfolder.livebridge.R

/**
 * Shows the current charging state as an ongoing promoted notification so the system can
 * render it as a Live Update while the device is plugged in.
 */
internal object ChargingStatusNotifier {
    private const val CHANNEL_ID = "livebridge_charging_status"
    private const val NOTIFICATION_ID = 41132

    fun sync(context: Context, prefs: ConverterPrefs = ConverterPrefs(context)) {
        if (!shouldRun(context, prefs)) {
            cancel(context)
            return
        }
        val batteryIntent = try {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        } catch (_: Throwable) {
            null
        }
        syncFromBatteryIntent(context, prefs, batteryIntent)
    }

    fun syncFromBatteryIntent(
        context: Context,
        prefs: ConverterPrefs,
        intent: Intent?
    ) {
        if (!shouldRun(context, prefs)) {
            cancel(context)
            return
        }
        val plugged = intent
            ?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
            ?.let { it != 0 }
            ?: isPluggedFallback(context)
        val percent = intent?.let(::readPercent) ?: readCapacity(context)
        if (!ChargingStatusPolicy.shouldShow(plugged, percent)) {
            cancel(context)
            return
        }
        val remaining = context
            .getSystemService(Context.BATTERY_SERVICE)
            ?.let { (it as BatteryManager).computeChargeTimeRemaining() }
            ?: -1L
        post(context, percent, remaining)
    }

    fun cancel(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    private fun shouldRun(context: Context, prefs: ConverterPrefs): Boolean {
        return prefs.getConverterEnabled() &&
            prefs.getChargingLiveUpdateEnabled() &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    private fun readPercent(intent: Intent): Int {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) {
            return ChargingStatusPolicy.UNKNOWN_PERCENT
        }
        return ((level * 100f) / scale).toInt().coerceIn(0, 100)
    }

    private fun readCapacity(context: Context): Int {
        val manager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            ?: return ChargingStatusPolicy.UNKNOWN_PERCENT
        val value = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        return if (value in 0..100) value else ChargingStatusPolicy.UNKNOWN_PERCENT
    }

    private fun isPluggedFallback(context: Context): Boolean {
        val manager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            ?: return false
        return manager.isCharging
    }

    private fun post(context: Context, percent: Int, remainingMs: Long) {
        ensureChannel(context)
        val fullyCharged = ChargingStatusPolicy.isFullyCharged(percent)
        val title = if (fullyCharged) {
            NativeAppStrings.text(
                context,
                "Charging complete",
                "Зарядка завершена",
                "Carga completa",
                "Laden abgeschlossen",
                "Şarj tamamlandı"
            )
        } else {
            NativeAppStrings.text(
                context,
                "Charging",
                "Зарядка",
                "Cargando",
                "Wird geladen",
                "Şarj oluyor"
            )
        }
        val hourUnit = NativeAppStrings.text(context, "h", "ч", "h", "Std.", "sa")
        val minuteUnit = NativeAppStrings.text(context, "min", "мин", "min", "Min.", "dk")
        val remaining = if (fullyCharged) {
            null
        } else {
            ChargingStatusPolicy.formatRemaining(remainingMs, hourUnit, minuteUnit)
        }
        val percentLabel = if (percent >= 0) "$percent%" else null
        val text = listOfNotNull(percentLabel, remaining).joinToString(" · ")
            .ifBlank {
                NativeAppStrings.text(
                    context,
                    "Plugged in",
                    "Подключено к зарядке",
                    "Conectado",
                    "Angeschlossen",
                    "Fişe takılı"
                )
            }

        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_charging)
            .setContentTitle(title)
            .setContentText(text)
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

        if (percent >= 0) {
            builder.setProgress(100, percent, false)
            builder.setShortCriticalText("$percent%")
            builder.setRequestPromotedOngoing(true)
        }

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
        } catch (_: SecurityException) {
            // Notification permission was revoked between the check and the post.
        }
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val name = NativeAppStrings.text(
            context,
            "Charging status",
            "Статус зарядки",
            "Estado de carga",
            "Ladestatus",
            "Şarj durumu"
        )
        val description = NativeAppStrings.text(
            context,
            "Shows the battery level while the device is charging",
            "Показывает уровень батареи во время зарядки",
            "Muestra el nivel de batería mientras se carga",
            "Zeigt den Akkustand während des Ladens",
            "Cihaz şarj olurken pil seviyesini gösterir"
        )
        val existing = manager.getNotificationChannel(CHANNEL_ID)
        if (existing != null && existing.name == name && existing.description == description) {
            return
        }
        val channel = existing ?: NotificationChannel(
            CHANNEL_ID,
            name,
            NotificationManager.IMPORTANCE_DEFAULT
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
