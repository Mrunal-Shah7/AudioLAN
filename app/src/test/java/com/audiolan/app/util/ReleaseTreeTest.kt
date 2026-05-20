package com.audiolan.app.util

import android.util.Log
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ReleaseTreeTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun loggableOnlyForWarnAndAbove() {
        val tree = TestReleaseTree(temporaryFolder.root)

        assertFalse(tree.canLog(Log.DEBUG))
        assertFalse(tree.canLog(Log.INFO))
        assertTrue(tree.canLog(Log.WARN))
        assertTrue(tree.canLog(Log.ERROR))
    }

    @Test
    fun warnLogsAreWrittenToReleaseFile() {
        val tree = TestReleaseTree(temporaryFolder.root)

        tree.write(Log.WARN, "test", "warning", null)

        val logFile = File(temporaryFolder.root, "logs/audiolan.log")
        assertTrue(logFile.exists())
        assertTrue(logFile.readText().contains("warning"))
    }

    private class TestReleaseTree(filesDir: File) : ReleaseTree(filesDir) {
        fun canLog(priority: Int): Boolean = isLoggable("test", priority)

        fun write(priority: Int, tag: String?, message: String, throwable: Throwable?) {
            log(priority, tag, message, throwable)
        }
    }
}
