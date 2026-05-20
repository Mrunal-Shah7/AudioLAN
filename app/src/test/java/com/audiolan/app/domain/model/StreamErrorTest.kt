package com.audiolan.app.domain.model

import org.junit.Assert.assertTrue
import org.junit.Test

class StreamErrorTest {
    @Test
    fun networkUnreachableMessageContainsHostAndPort() {
        val message = StreamError.NetworkUnreachable("192.168.1.1", 6980).toUserMessage()

        assertNonBlank(message)
        assertTrue(message.contains("192.168.1.1"))
        assertTrue(message.contains("6980"))
    }

    @Test
    fun audioInitFailureMessageContainsReason() {
        val message = StreamError.AudioInitFailed("No microphone").toUserMessage()

        assertNonBlank(message)
        assertTrue(message.contains("No microphone"))
    }

    @Test
    fun audioPermissionDeniedMessageMentionsPermission() {
        val message = StreamError.AudioPermissionDenied.toUserMessage()

        assertNonBlank(message)
        assertTrue(message.contains("permission", ignoreCase = true))
    }

    @Test
    fun mediaProjectionDeniedMessageMentionsPermissionOrCapture() {
        val message = StreamError.MediaProjectionDenied.toUserMessage()

        assertNonBlank(message)
        assertTrue(
            message.contains("permission", ignoreCase = true) ||
                message.contains("capture", ignoreCase = true),
        )
    }

    @Test
    fun udpBindFailedMessageContainsPort() {
        val message = StreamError.UdpBindFailed(6980).toUserMessage()

        assertNonBlank(message)
        assertTrue(message.contains("6980"))
    }

    @Test
    fun malformedPacketMessageMentionsVbanOrMalformed() {
        val message = StreamError.VbanPacketMalformed("bad magic").toUserMessage()

        assertNonBlank(message)
        assertTrue(
            message.contains("VBAN", ignoreCase = true) ||
                message.contains("malformed", ignoreCase = true),
        )
    }

    @Test
    fun unknownMessageContainsThrowableMessage() {
        val message = StreamError.Unknown(RuntimeException("test")).toUserMessage()

        assertNonBlank(message)
        assertTrue(message.contains("test"))
    }

    private fun assertNonBlank(message: String) {
        assertTrue(message.isNotBlank())
    }
}
