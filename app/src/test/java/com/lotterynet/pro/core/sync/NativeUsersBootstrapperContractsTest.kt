package com.lotterynet.pro.core.sync

import com.lotterynet.pro.core.users.UsersRemoteStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeUsersBootstrapperContractsTest {

    @Test
    fun `bootstrap skips remote when local users exist and force refresh is false`() {
        assertFalse(shouldFetchRemoteUsers(hasLocalUsers = true, forceRemoteRefresh = false))
    }

    @Test
    fun `bootstrap fetches remote when local users exist and force refresh is true`() {
        assertTrue(shouldFetchRemoteUsers(hasLocalUsers = true, forceRemoteRefresh = true))
    }

    @Test
    fun `bootstrap fetches remote when local cache is empty`() {
        assertTrue(shouldFetchRemoteUsers(hasLocalUsers = false, forceRemoteRefresh = false))
    }

    @Test
    fun `login user remote store can receive master published payload`() {
        val store = RecordingUsersRemoteStore()
        val payload = """{"admins":[{"user":"admin1","credChangedAt":9}],"cajeros":[]}"""

        store.upsertUsersPayload(payload)

        assertEquals(payload, store.fetchUsersPayload())
    }

    private class RecordingUsersRemoteStore : UsersRemoteStore {
        private var payload: String? = null

        override fun fetchUsersPayload(): String? = payload

        override fun upsertUsersPayload(payloadJson: String) {
            payload = payloadJson
        }
    }
}
