package com.audiolan.app.domain.model

sealed class StreamError {
    data class NetworkUnreachable(val host: String, val port: Int) : StreamError()
    data class AudioInitFailed(val reason: String) : StreamError()
    data object AudioPermissionDenied : StreamError()
    data object MediaProjectionDenied : StreamError()
    data class UdpBindFailed(val port: Int) : StreamError()
    data class VbanPacketMalformed(val reason: String) : StreamError()
    data class Unknown(val throwable: Throwable) : StreamError()

    fun toUserMessage(): String = when (this) {
        is NetworkUnreachable -> "Could not reach $host:$port"
        is AudioInitFailed -> "Audio init failed: $reason"
        AudioPermissionDenied -> "Microphone permission denied"
        MediaProjectionDenied -> "Screen capture permission denied"
        is UdpBindFailed -> "Port $port is already in use"
        is VbanPacketMalformed -> "Received malformed VBAN packet"
        is Unknown -> "Unexpected error: ${throwable.message ?: "unknown"}"
    }
}
