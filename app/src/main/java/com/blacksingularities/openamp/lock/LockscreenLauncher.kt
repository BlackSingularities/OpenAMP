package com.blacksingularities.openamp.lock

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.blacksingularities.openamp.Graph
import com.blacksingularities.openamp.R
import com.blacksingularities.openamp.i18n.tr

/**
 * Własny ekran blokady: po wygaszeniu ekranu podczas grania serwis publikuje cichy komunikat
 * z „full-screen intent”, dzięki czemu system uruchamia [LockscreenActivity] nad blokadą.
 * Komunikat jest usuwany zaraz po otwarciu ekranu (i sam znika po kilku sekundach).
 */
object LockscreenLauncher {
    private const val CHANNEL = "openamp_lockscreen"
    private const val ID = 4711

    /** Android 14+ wymaga osobnej zgody na wyświetlanie na pełnym ekranie. */
    fun canShow(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 34) return true
        return context.getSystemService(NotificationManager::class.java)?.canUseFullScreenIntent() == true
    }

    fun permissionIntent(context: Context): Intent? {
        if (Build.VERSION.SDK_INT < 34) return null
        return Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun show(context: Context) {
        if (!Graph.settings.value.lockScreen || !canShow(context)) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, tr("Ekran blokady"), NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null)
                enableVibration(false)
                enableLights(false)
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            },
        )
        val pi = PendingIntent.getActivity(
            context, 7,
            Intent(context, LockscreenActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(tr("Ekran blokady OpenAMP"))
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setFullScreenIntent(pi, true)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setTimeoutAfter(8_000)
            .build()
        runCatching { nm.notify(ID, n) }
    }

    fun cancel(context: Context) {
        context.getSystemService(NotificationManager::class.java)?.cancel(ID)
    }
}
