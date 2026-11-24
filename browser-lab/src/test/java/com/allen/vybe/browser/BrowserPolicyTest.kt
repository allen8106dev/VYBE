package com.allen.vybe.browser

import org.junit.Assert.*
import org.junit.Test

class BrowserPolicyTest {
    @Test fun rejectsDangerousNavigationSchemesAndCredentials() {
        listOf("javascript:alert(1)", "file:///sdcard/x", "content://x", "intent://x", "http://example.com", "https://user:password@example.com", "https://example.com:8443").forEach { assertFalse(it, BrowserPolicy.allows(it)) }
    }
    @Test fun youtubeAndLoginAreOutsideLaboratory() {
        listOf("https://youtube.com/watch?v=1", "https://m.youtube.com/", "https://youtu.be/1", "https://x.googlevideo.com/a", "https://accounts.google.com/").forEach { assertFalse(it, BrowserPolicy.allows(it)) }
    }
    @Test fun domainChecksDoNotMatchUnrelatedSuffixes() {
        assertTrue(BrowserPolicy.allows("https://notyoutube.com/"))
        assertTrue(BrowserPolicy.allows("https://developer.android.com/"))
        assertTrue(BrowserPolicy.allows("https://muse.invalid/lab"))
        assertFalse(BrowserPolicy.blocks("nottracker.muse.invalid"))
        assertTrue(BrowserPolicy.blocks("tracker.muse.invalid"))
        assertTrue(BrowserPolicy.blocks("sub.tracker.muse.invalid"))
    }
    @Test fun malformedInputFailsClosed() { listOf("", "https://", "https://exa mple.org", "//example.com").forEach { assertFalse(BrowserPolicy.allows(it)) } }
}
