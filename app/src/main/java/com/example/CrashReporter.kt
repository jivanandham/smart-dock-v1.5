package com.example

import android.app.Application
import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Minimal on-device crash capture. Saves the last crash to a private file so the next launch can
 * offer to share it. For production, also add Firebase Crashlytics (see CHANGES.md).
 */
object CrashReporter {
    private const val FILE = "last_crash.txt"

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                val sw = StringWriter()
                error.printStackTrace(PrintWriter(sw))
                val version = try {
                    appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName
                } catch (_: Exception) {
                    "?"
                }
                val header = "Smart Dock $version • Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) • " +
                    "${Build.MANUFACTURER} ${Build.MODEL}\n" +
                    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()) + "\n\n"
                File(appContext.filesDir, FILE).writeText(header + sw.toString().take(20_000))
            } catch (_: Exception) {
            }
            previous?.uncaughtException(thread, error)
        }
    }

    fun pendingReport(context: Context): String? {
        val f = File(context.filesDir, FILE)
        return if (f.exists()) f.readText() else null
    }

    fun clear(context: Context) {
        File(context.filesDir, FILE).delete()
    }
}

class SmartDockApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)
        com.example.premium.PremiumManager.init(this)
    }
}
