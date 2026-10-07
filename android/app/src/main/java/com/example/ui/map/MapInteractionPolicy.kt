package com.example.ui.map

object MapInteractionPolicy {
    enum class SurfaceStatus { LOADING, READY, OFFLINE, LOAD_FAILED }
    enum class LocationStatus { IDLE, WAITING_FOR_FIX, READY, FIX_UNAVAILABLE }

    @JvmStatic
    fun surfaceStatus(
        isLoaded: Boolean,
        networkAvailable: Boolean,
        loadTimedOut: Boolean
    ): SurfaceStatus = when {
        isLoaded -> SurfaceStatus.READY
        !networkAvailable -> SurfaceStatus.OFFLINE
        loadTimedOut -> SurfaceStatus.LOAD_FAILED
        else -> SurfaceStatus.LOADING
    }

    @JvmStatic
    fun locationStatus(
        hasFix: Boolean,
        requestInProgress: Boolean,
        requestTimedOut: Boolean
    ): LocationStatus = when {
        hasFix -> LocationStatus.READY
        requestTimedOut -> LocationStatus.FIX_UNAVAILABLE
        requestInProgress -> LocationStatus.WAITING_FOR_FIX
        else -> LocationStatus.IDLE
    }
}
