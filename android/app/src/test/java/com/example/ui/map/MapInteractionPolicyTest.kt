package com.example.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class MapInteractionPolicyTest {

    private fun policyResult(methodName: String, vararg args: Boolean): String {
        val policyClass = runCatching {
            Class.forName("com.example.ui.map.MapInteractionPolicy")
        }.getOrNull()
        assertNotNull("Map interaction policy must be available", policyClass)
        val instance = policyClass!!.getField("INSTANCE").get(null)
        val method = policyClass.getMethod(
            methodName,
            *Array(args.size) { Boolean::class.javaPrimitiveType }
        )
        return method.invoke(instance, *args.toTypedArray()).toString()
    }

    @Test
    fun offlineMapShowsExplicitOfflineFallback() {
        assertEquals("OFFLINE", policyResult("surfaceStatus", false, false, false))
    }

    @Test
    fun onlineMapTimeoutShowsLoadFailureInsteadOfEndlessBlankSurface() {
        assertEquals("LOAD_FAILED", policyResult("surfaceStatus", false, true, true))
    }

    @Test
    fun loadedMapWinsAfterConnectivityChanges() {
        assertEquals("READY", policyResult("surfaceStatus", true, false, true))
    }

    @Test
    fun locationRequestWaitsForFixAndThenFocusesOrReportsTimeout() {
        assertEquals("WAITING_FOR_FIX", policyResult("locationStatus", false, true, false))
        assertEquals("READY", policyResult("locationStatus", true, true, false))
        assertEquals("FIX_UNAVAILABLE", policyResult("locationStatus", false, false, true))
    }
}
