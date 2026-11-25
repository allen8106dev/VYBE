package com.allen.vybe.browser

import org.junit.Assert.*
import org.junit.Test

class BrowserObservationRecoveryTest {
    @Test fun backgroundTimeoutsNeverTriggerRendererRecovery() {
        val recovery = BrowserObservationRecovery()

        repeat(10) { assertFalse(recovery.timedOut(hostVisible = false)) }

        assertFalse(recovery.timedOut(hostVisible = true))
        assertTrue(recovery.timedOut(hostVisible = true))
    }

    @Test fun successfulObservationAndForegroundReturnResetTimeoutCount() {
        val recovery = BrowserObservationRecovery()
        assertFalse(recovery.timedOut(hostVisible = true))
        recovery.observed()
        assertFalse(recovery.timedOut(hostVisible = true))
        recovery.becameVisible()
        assertFalse(recovery.timedOut(hostVisible = true))
    }
}
