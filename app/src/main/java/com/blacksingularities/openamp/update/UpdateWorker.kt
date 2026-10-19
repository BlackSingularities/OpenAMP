package com.blacksingularities.openamp.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.blacksingularities.openamp.Graph
import com.blacksingularities.openamp.MainActivity
import com.blacksingularities.openamp.R
import com.blacksingularities.openamp.i18n.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/** Codzienne sprawdzenie nowej wersji w tle; o nowym wydaniu informuje powiadomienie. */
class UpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val info = runCatching { withContext(Dispatchers.IO) { parseRelease(UpdateManager.httpGet(UPDATE_API)) } }
            .getOrElse { return Result.retry() }
        Graph.settings.update { it.copy(lastUpdateCheck = System.currentTimeMillis()) }
        if (isNewer(info.version, UpdateManager.currentVersion)) notifyAvailable(applicationContext, info)
        return Result.success()
    }

    companion object {
        private const val NAME = "update-check"
        private const val CHANNEL = "updates"
        private const val NOTIFICATION_ID = 4712

        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<UpdateWorker>(24, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setInitialDelay(1, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, req)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(NAME)
        }

        /** Powiadomienie o nowej wersji – raz dla każdego numeru wydania. */
        fun notifyAvailable(context: Context, info: ReleaseInfo) {
            val prefs = context.getSharedPreferences("openamp_update", Context.MODE_PRIVATE)
            if (prefs.getString("notified", null) == info.version) return
            if (Build.VERSION.SDK_INT >= 33 &&
                context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) return
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            nm.createNotificationChannel(NotificationChannel(CHANNEL, tr("Aktualizacje"), NotificationManager.IMPORTANCE_DEFAULT))
            val open = Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_UPDATE, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            val pending = PendingIntent.getActivity(context, 1, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val n = android.app.Notification.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(tr("Nowa wersja OpenAMP"))
                .setContentText(tr("Dostępna jest wersja {0}. Dotknij, aby zaktualizować.", info.version))
                .setStyle(android.app.Notification.BigTextStyle().bigText(
                    tr("Dostępna jest wersja {0}. Dotknij, aby zaktualizować.", info.version) +
                        (if (info.notes.isNotBlank()) "\n\n" + info.notes.take(400) else ""),
                ))
                .setContentIntent(pending)
                .setAutoCancel(true)
                .build()
            runCatching { nm.notify(NOTIFICATION_ID, n) }
            prefs.edit().putString("notified", info.version).apply()
        }
    }
}
