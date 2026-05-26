package com.lotterynet.pro.core.storage

import com.lotterynet.pro.core.model.DeletedUserRef
import com.lotterynet.pro.core.model.DeletedUsersState
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class LocalUsersDeletedRepositoryTest {

    @Test
    fun `old cashier tombstone does not remove a newly created cashier with reused username`() {
        val payload = """
            {
              "admins":[{"id":"ADM-1","user":"admin01","banca":"Banca Nueva","credChangedAt":2000}],
              "cajeros":[{"id":"CAJ-NEW","user":"bn01","adminId":"ADM-1","banca":"Banca Nueva","credChangedAt":2000}]
            }
        """.trimIndent()
        val deleted = DeletedUsersState(
            cashiers = listOf(
                DeletedUserRef(
                    id = "CAJ-OLD",
                    user = "bn01",
                    adminId = "ADM-OLD",
                    banca = "Banca Vieja",
                    deletedAtEpochMs = 1000,
                )
            )
        )

        val filtered = applyDeletedUsersToPayload(payload, deleted)

        assertEquals(1, JSONObject(filtered).optJSONArray("cajeros")?.length())
    }

    @Test
    fun `newer cashier tombstone still removes the deleted cashier from sync payload`() {
        val payload = """
            {
              "admins":[{"id":"ADM-1","user":"admin01","banca":"Banca Nueva","credChangedAt":1000}],
              "cajeros":[{"id":"CAJ-1","user":"bn01","adminId":"ADM-1","banca":"Banca Nueva","credChangedAt":1000}]
            }
        """.trimIndent()
        val deleted = DeletedUsersState(
            cashiers = listOf(
                DeletedUserRef(
                    id = "CAJ-1",
                    user = "bn01",
                    adminId = "ADM-1",
                    banca = "Banca Nueva",
                    deletedAtEpochMs = 2000,
                )
            )
        )

        val filtered = applyDeletedUsersToPayload(payload, deleted)

        assertEquals(0, JSONObject(filtered).optJSONArray("cajeros")?.length())
    }

    @Test
    fun `old unrelated tombstone does not override a newer matching cashier tombstone`() {
        val payload = """
            {
              "admins":[{"id":"ADM-1","user":"admin01","banca":"Banca Nueva","credChangedAt":3000}],
              "cajeros":[{"id":"CAJ-1","user":"bn01","adminId":"ADM-1","banca":"Banca Nueva","credChangedAt":3000}]
            }
        """.trimIndent()
        val deleted = DeletedUsersState(
            cashiers = listOf(
                DeletedUserRef(
                    id = "CAJ-OTHER",
                    user = "otro01",
                    deletedAtEpochMs = 1000,
                ),
                DeletedUserRef(
                    id = "CAJ-1",
                    user = "bn01",
                    adminId = "ADM-1",
                    banca = "Banca Nueva",
                    deletedAtEpochMs = 4000,
                ),
            )
        )

        val filtered = applyDeletedUsersToPayload(payload, deleted)

        assertEquals(0, JSONObject(filtered).optJSONArray("cajeros")?.length())
    }
}
