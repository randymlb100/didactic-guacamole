package com.lotterynet.pro.core.users

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONArray
import org.json.JSONObject

class SupabaseUsersRemoteStoreTest {

    @Test
    fun `users save does not fail after edge success`() {
        assertFalse(
            shouldFailUsersPayloadSave(
                edgeSaved = true,
            ),
        )
    }

    @Test
    fun `users save fails when edge function fails`() {
        assertTrue(
            shouldFailUsersPayloadSave(
                edgeSaved = false,
            ),
        )
    }

    @Test
    fun `users state function payload wraps action and json payload`() {
        val request = buildUsersStateFunctionPayload("upsert", "{\"admins\":[],\"cajeros\":[]}")

        assertEquals("upsert", request.getString("action"))
        assertEquals(0, request.getJSONObject("payload").getJSONArray("admins").length())
        assertEquals(0, request.getJSONObject("payload").getJSONArray("cajeros").length())
    }

    @Test
    fun `users state function payload marks explicit commission overrides`() {
        val request = buildUsersStateFunctionPayload(
            "upsert",
            "{\"admins\":[],\"cajeros\":[]}",
            commissionOverrideKeys = setOf(" CAJ-1 ", "cajero01", ""),
        )

        val keys = request.getJSONArray("commissionOverrideKeys")
        assertEquals(2, keys.length())
        assertEquals("caj-1", keys.getString(0))
        assertEquals("cajero01", keys.getString(1))
    }

    @Test
    fun `master recharge fund request carries exact amount and target bank`() {
        val request = buildMasterRechargeFundPayload(
            accountId = "adm-1",
            enabled = true,
            amount = 4_037.0,
        )

        assertEquals("update-recharge-fund", request.getString("action"))
        assertEquals("adm-1", request.getString("accountId"))
        assertTrue(request.getBoolean("enabled"))
        assertEquals(4_037.0, request.getDouble("amount"), 0.0)
    }

    @Test
    fun `render fetch returns raw users payload`() {
        val client = RenderUsersRemoteClient(
            requestSender = { method, path, _, _, _ ->
                assertEquals("GET", method)
                assertEquals("/users-state", path)
                200 to JSONObject()
                    .put("payload", JSONObject().put("admins", JSONArray()).put("cajeros", JSONArray()))
                    .toString()
            }
        )

        val payload = JSONObject(client.fetchUsersPayload() ?: error("missing payload"))

        assertEquals(0, payload.getJSONArray("admins").length())
        assertEquals(0, payload.getJSONArray("cajeros").length())
    }

    @Test
    fun `render upsert posts payload wrapper`() {
        var capturedBody: String? = null
        val client = RenderUsersRemoteClient(
            requestSender = { method, path, body, _, _ ->
                assertEquals("POST", method)
                assertEquals("/users-state", path)
                capturedBody = body
                200 to "{\"ok\":true}"
            }
        )

        client.upsertUsersPayload("{\"admins\":[],\"cajeros\":[]}")

        val request = JSONObject(capturedBody ?: error("missing request body"))
        assertEquals(0, request.getJSONObject("payload").getJSONArray("admins").length())
        assertEquals(0, request.getJSONObject("payload").getJSONArray("cajeros").length())
    }

    @Test
    fun `fetch prefers edge users state before render fallback`() {
        clearUsersPayloadMemoryCache()
        val payload = resolveUsersPayloadFetch(
            fetchLegacy = { "{\"source\":\"legacy\"}" },
            fetchRender = { "{\"source\":\"render\"}" },
        )

        assertEquals("legacy", JSONObject(payload ?: error("missing payload")).getString("source"))
    }

    @Test
    fun `fetch falls back to render users state when edge is unavailable`() {
        clearUsersPayloadMemoryCache()
        val payload = resolveUsersPayloadFetch(
            fetchLegacy = { null },
            fetchRender = { "{\"source\":\"render\"}" },
        )

        assertEquals("render", JSONObject(payload ?: error("missing payload")).getString("source"))
    }

    @Test
    fun `users payload memory cache avoids repeated remote fetches`() {
        clearUsersPayloadMemoryCache()
        var edgeCalls = 0

        val first = resolveUsersPayloadFetch(
            fetchLegacy = {
                edgeCalls += 1
                "{\"source\":\"legacy\"}"
            },
            fetchRender = { "{\"source\":\"render\"}" },
        )
        val second = resolveUsersPayloadFetch(
            fetchLegacy = {
                edgeCalls += 1
                "{\"source\":\"legacy-2\"}"
            },
            fetchRender = { "{\"source\":\"render-2\"}" },
        )

        assertEquals("legacy", JSONObject(first ?: error("missing first payload")).getString("source"))
        assertEquals("legacy", JSONObject(second ?: error("missing second payload")).getString("source"))
        assertEquals(1, edgeCalls)
        clearUsersPayloadMemoryCache()
    }

    @Test
    fun `save writes edge users state and render mirror so another server sees cashier mode`() {
        val calls = mutableListOf<String>()

        persistUsersPayload(
            saveLegacy = { calls += "legacy" },
            saveRender = { calls += "render" },
        )

        assertEquals(listOf("legacy", "render"), calls)
    }

    @Test
    fun `save succeeds when edge fails but render mirror accepts users payload`() {
        val calls = mutableListOf<String>()

        persistUsersPayload(
            saveLegacy = {
                calls += "legacy"
                throw IllegalStateException("legacy unavailable")
            },
            saveRender = { calls += "render" },
        )

        assertEquals(listOf("legacy", "render"), calls)
    }
}
