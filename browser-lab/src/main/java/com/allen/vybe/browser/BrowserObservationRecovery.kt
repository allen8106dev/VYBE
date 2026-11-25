package com.allen.vybe.browser

/** A background WebView may delay JavaScript callbacks while continuing media playback. */
internal class BrowserObservationRecovery {
    private var consecutiveVisibleTimeouts = 0

    fun timedOut(hostVisible: Boolean): Boolean {
        if (!hostVisible) {
            consecutiveVisibleTimeouts = 0
            return false
        }
        consecutiveVisibleTimeouts++
        return consecutiveVisibleTimeouts >= 2
    }

    fun observed() {
        consecutiveVisibleTimeouts = 0
    }

    fun becameVisible() {
        consecutiveVisibleTimeouts = 0
    }
}
