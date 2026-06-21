package com.s3id3l.voicecapture

import android.app.Application
import android.content.Context

class VoiceCaptureApp : Application() {

    override fun onCreate() {
        super.onCreate()
        installCrashHandler()
    }

    private fun installCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            // Persist crash for next launch
            runCatching {
                getSharedPreferences("crash_log", Context.MODE_PRIVATE).edit()
                    .putString("last_crash", buildString {
                        appendLine("Thread: ${thread.name}")
                        appendLine("Error: ${throwable.javaClass.simpleName}: ${throwable.message}")
                        throwable.stackTrace.take(8).forEach { appendLine("  at $it") }
                    })
                    .putLong("crash_time", System.currentTimeMillis())
                    .apply()
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    companion object {
        fun getLastCrash(context: Context): String? {
            val prefs = context.getSharedPreferences("crash_log", Context.MODE_PRIVATE)
            val crashTime = prefs.getLong("crash_time", 0L)
            if (crashTime == 0L) return null
            val ageMin = (System.currentTimeMillis() - crashTime) / 60000
            return if (ageMin < 60) prefs.getString("last_crash", null) else null
        }

        fun clearCrash(context: Context) {
            context.getSharedPreferences("crash_log", Context.MODE_PRIVATE).edit().clear().apply()
        }
    }
}
