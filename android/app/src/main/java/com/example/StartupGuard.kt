package com.example

/** Contains optional SDK startup work so a missing or placeholder backend cannot crash launch. */
object StartupGuard {
    @JvmStatic
    fun run(action: () -> Unit): Boolean = try {
        action()
        true
    } catch (_: RuntimeException) {
        false
    }

    @JvmStatic
    fun runIfConfigured(projectId: String?, action: () -> Unit): Boolean {
        val configured = !projectId.isNullOrBlank() &&
            !projectId.contains("placeholder", ignoreCase = true)
        return configured && run(action)
    }
}
