package com.blacksingularities.openamp

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.ScrollView
import android.widget.TextView
import java.io.File

/**
 * Zapis ostatniego crasha do pliku i pokazanie go przy następnym uruchomieniu (przed interfejsem),
 * żeby dało się skopiować stack trace bez adb.
 */
object CrashLog {
    private fun file(c: Context) = File(c.filesDir, "last_crash.txt")

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            runCatching {
                val info = "OpenAMP ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}), Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}\nWątek: ${thread.name}\n\n"
                file(app).writeText(info + e.stackTraceToString())
            }
            previous?.uncaughtException(thread, e)
        }
    }

    /** Błędy odtwarzania (bez zamykania aplikacji) – dopisywane do osobnego pliku, ostatnie ~50 wpisów. */
    fun logPlayback(context: Context, what: String, e: Throwable) {
        runCatching {
            val f = File(context.filesDir, "playback_errors.txt")
            val entry = "${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.ROOT).format(java.util.Date())}  $what\n${e.stackTraceToString().lineSequence().take(12).joinToString("\n")}\n\n"
            val old = if (f.exists()) f.readText().split("\n\n").filter { it.isNotBlank() }.takeLast(49) else emptyList()
            f.writeText(old.joinToString("\n\n", postfix = if (old.isEmpty()) "" else "\n\n") + entry)
        }
    }

    /** Pokazuje zapisany crash; [onDone] wywoływane po zamknięciu okna. Zwraca true, gdy okno się pojawiło. */
    fun showIfAny(activity: Activity, onDone: () -> Unit): Boolean {
        val f = file(activity)
        val text = runCatching { if (f.exists()) f.readText() else null }.getOrNull() ?: return false
        f.delete()
        val view = ScrollView(activity).apply {
            addView(TextView(activity).apply {
                this.text = text
                textSize = 11f
                setTextIsSelectable(true)
                setPadding(40, 24, 40, 24)
            })
        }
        AlertDialog.Builder(activity)
            .setTitle("OpenAMP – ostatni błąd")
            .setView(view)
            .setPositiveButton("Kopiuj") { _, _ ->
                activity.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("OpenAMP crash", text))
            }
            .setNeutralButton("Udostępnij") { _, _ ->
                activity.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), null))
            }
            .setNegativeButton("Zamknij", null)
            .setOnDismissListener { onDone() }
            .show()
        return true
    }
}
