package com.audiolan.app.util

import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import timber.log.Timber

open class ReleaseTree(
    private val filesDir: File,
) : Timber.Tree() {
    private val logFile = File(filesDir, "logs/audiolan.log")
    private val maxSizeBytes = 1 * 1024 * 1024

    init {
        logFile.parentFile?.mkdirs()
    }

    override fun isLoggable(tag: String?, priority: Int): Boolean = priority >= Log.WARN

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        if (!isLoggable(tag, priority)) {
            return
        }
        try {
            rotate()
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
            val level = when (priority) {
                Log.WARN -> "W"
                Log.ERROR -> "E"
                Log.ASSERT -> "A"
                else -> "?"
            }
            val line = buildString {
                append(timestamp)
                append(' ')
                append(level)
                append('/')
                append(tag ?: "AudioLAN")
                append(": ")
                append(message)
                if (t != null) {
                    append('\n')
                    append(Log.getStackTraceString(t))
                }
                append('\n')
            }
            logFile.appendText(line, Charsets.UTF_8)
        } catch (_: Exception) {
            // Never crash in the logging layer.
        }
    }

    private fun rotate() {
        if (logFile.exists() && logFile.length() > maxSizeBytes) {
            logFile.delete()
            logFile.createNewFile()
        }
    }
}
