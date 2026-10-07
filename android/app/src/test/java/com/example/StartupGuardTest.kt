package com.example

import kotlin.jvm.functions.Function0
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class StartupGuardTest {

    private fun invokeGuard(action: () -> Unit): Boolean {
        val guardClass = runCatching { Class.forName("com.example.StartupGuard") }.getOrNull()
        assertNotNull("Startup guard must be available", guardClass)
        val instance = guardClass!!.getField("INSTANCE").get(null)
        val method = guardClass.getMethod("run", Function0::class.java)
        return method.invoke(instance, action) as Boolean
    }

    private fun invokeConfiguredGuard(projectId: String, action: () -> Unit): Boolean {
        val guardClass = Class.forName("com.example.StartupGuard")
        val instance = guardClass.getField("INSTANCE").get(null)
        val method = guardClass.getMethod("runIfConfigured", String::class.java, Function0::class.java)
        return method.invoke(instance, projectId, action) as Boolean
    }

    @Test
    fun startupActionFailureIsContainedInsteadOfCrashingActivity() {
        assertEquals(false, invokeGuard { error("Firebase is not initialized") })
    }

    @Test
    fun successfulStartupActionReportsSuccess() {
        assertEquals(true, invokeGuard { })
    }

    @Test
    fun placeholderFirebaseProjectIsNeverSubscribed() {
        var called = false
        assertEquals(
            false,
            invokeConfiguredGuard("norilsk-transit-placeholder") { called = true }
        )
        assertEquals(false, called)
    }
}
