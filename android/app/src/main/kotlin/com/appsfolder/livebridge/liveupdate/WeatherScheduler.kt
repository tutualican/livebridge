package com.appsfolder.livebridge.liveupdate

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class WeatherRefreshWorker(
    appContext: Context,
    params: WorkerParameters
) : Worker(appContext, params) {
    override fun doWork(): Result {
        val prefs = ConverterPrefs(applicationContext)
        if (!prefs.getConverterEnabled() || !prefs.getWeatherLiveUpdateEnabled()) {
            return Result.success()
        }
        val snapshot = WeatherRepository.fetchBlocking(applicationContext, prefs)
            ?: return Result.retry()
        WeatherLiveUpdate.publish(applicationContext, prefs, snapshot)
        return Result.success()
    }
}

internal object WeatherScheduler {
    private const val WORK_NAME = "livebridge_weather_refresh"

    fun sync(context: Context, prefs: ConverterPrefs = ConverterPrefs(context)) {
        val manager = WorkManager.getInstance(context)
        if (!prefs.getConverterEnabled() || !prefs.getWeatherLiveUpdateEnabled()) {
            manager.cancelUniqueWork(WORK_NAME)
            WeatherLiveUpdate.cancel(context)
            return
        }
        val request = PeriodicWorkRequest.Builder(
            WeatherRefreshWorker::class.java,
            30,
            TimeUnit.MINUTES
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        manager.enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
        WeatherRepository.refreshAsync(context, prefs)
    }
}
