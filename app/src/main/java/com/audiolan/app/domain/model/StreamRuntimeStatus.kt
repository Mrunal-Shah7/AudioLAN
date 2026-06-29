package com.audiolan.app.domain.model

sealed interface StreamRuntimeStatus {
    data object Connecting : StreamRuntimeStatus
    data object Active : StreamRuntimeStatus
    data class Error(val message: String) : StreamRuntimeStatus
}
