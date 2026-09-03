package com.pablobertino.tagmap

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/** Guarda el último crash en disco para mostrarlo en el próximo arranque (sin necesidad de adb). */
object CrashReporter {
    private const val FILE = "last_crash.txt"

    fun install(context: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            runCatching {
                val sw = StringWriter()
                e.printStackTrace(PrintWriter(sw))
                File(context.filesDir, FILE).writeText("${BuildConfig.VERSION_NAME} · hilo ${thread.name}\n$sw")
            }
            previous?.uncaughtException(thread, e)
        }
    }

    fun consume(context: Context): String? {
        val f = File(context.filesDir, FILE)
        if (!f.exists()) return null
        val text = f.readText()
        f.delete()
        return text
    }
}
