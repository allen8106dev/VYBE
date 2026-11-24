package com.allen.vybe

import com.allen.vybe.auth.authorizationErrorMessage
import org.junit.Assert.*
import org.junit.Test

class GoogleAuthorizationTest {
    @Test fun configurationFailureNamesAndroidRegistrationInsteadOfInventingClientIdFile() {
        val message = authorizationErrorMessage(10)
        assertTrue(message.contains("com.allen.vybe")); assertTrue(message.contains("SHA-1")); assertTrue(message.contains("status 10"))
    }
    @Test fun cancellationAndNetworkFailureStayDistinct() {
        assertTrue(authorizationErrorMessage(16).contains("cancelled"))
        assertTrue(authorizationErrorMessage(12501).contains("cancelled"))
        assertTrue(authorizationErrorMessage(7).contains("network"))
        assertFalse(authorizationErrorMessage(7).contains("rejected"))
    }
    @Test fun unrecognizedAndMissingStatusDoNotPretendToKnowTheCause() {
        assertTrue(authorizationErrorMessage(999).contains("status 999"))
        assertTrue(authorizationErrorMessage(null).contains("did not complete"))
        assertFalse(authorizationErrorMessage(null).contains("status 10"))
    }
}
