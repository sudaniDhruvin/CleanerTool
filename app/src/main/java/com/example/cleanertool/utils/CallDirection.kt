package com.example.cleanertool.utils

/**
 * Represents the direction of a phone call so we can display contextual UI
 * messages after the call has finished.
 */
enum class CallDirection {
    INCOMING,
    OUTGOING,
    UNKNOWN;

    fun displayLabel(): String? = when (this) {
        INCOMING -> "Incoming call"
        OUTGOING -> "Outgoing call"
        UNKNOWN -> null
    }
}

