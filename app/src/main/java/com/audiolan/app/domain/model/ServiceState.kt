package com.audiolan.app.domain.model

sealed class ServiceState {
    data object Idle : ServiceState()
    data object Starting : ServiceState()
    data object Running : ServiceState()
    data object Stopping : ServiceState()
    data class Error(val message: String) : ServiceState()
}
