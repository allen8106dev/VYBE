package com.allen.vybe

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SearchAndAuthorizationStateTest {
    @Test fun cancelledAuthorizationAndOfflineSearchHaveRecoverableStates() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as MuseApplication
        val store = ViewModelStore()
        lateinit var vm: MuseViewModel
        instrumentation.runOnMainSync {
            vm = ViewModelProvider(store, ViewModelProvider.AndroidViewModelFactory(app))[MuseViewModel::class.java]
            vm.disconnect(); vm.authorizing()
            assertTrue(vm.account.value.loading)
            vm.authFailed()
            assertFalse(vm.account.value.connected); assertFalse(vm.account.value.loading)
            assertNotNull(vm.account.value.error)
            vm.search("music")
        }
        try {
            val blocked = withTimeout(10000) { vm.search.first { it.query == "music" && !it.loading } }
            assertTrue(blocked.error.orEmpty().contains("Connect"))
            instrumentation.runOnMainSync { vm.resetSearch() }
            assertFalse(vm.search.value.submitted)
        } finally { instrumentation.runOnMainSync { store.clear() } }
    }
}
