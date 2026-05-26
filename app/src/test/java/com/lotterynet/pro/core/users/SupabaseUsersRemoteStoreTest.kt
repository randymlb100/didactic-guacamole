package com.lotterynet.pro.core.users

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun `direct users state fetch returns raw users payload`() {
        val client = SupabaseUsersStateClient(
            requestFetcher = { method, path, _, _, _ ->
                assertEquals("GET", method)
                assertTrue(path.contains("/rest/v1/lotterynet_users_state"))
                assertTrue(path.contains("scope=eq.global"))
                200 to JSONArray().put(
                    JSONObject().put(
                        "payload",
                        JSONObject()
                            .put("admins", JSONArray())
                            .put("cajeros", JSONArray()),
                    )
                ).toString()
            }
        )

        val payload = JSONObject(client.fetchUsersPayload() ?: error("missing payload"))

        assertEquals(0, payload.getJSONArray("admins").length())
        assertEquals(0, payload.getJSONArray("cajeros").length())
    }

    @Test
    fun `direct users state fetch returns null when row is missing`() {
        val client = SupabaseUsersStateClient(
            requestFetcher = { _, _, _, _, _ -> 200 to "[]" }
        )

        assertNull(client.fetchUsersPayload())
    }

    @Test
    fun `direct users state upsert posts global scope`() {
        var capturedBody: String? = null
        val client = SupabaseUsersStateClient(
            requestFetcher = { method, path, body, _, _ ->
                assertEquals("POST", method)
                assertEquals("/rest/v1/lotterynet_users_state?on_conflict=scope", path)
                capturedBody = body
                201 to ""
            }
        )

        client.upsertUsersPayload("{\"admins\":[],\"cajeros\":[]}")

        val row = JSONObject(capturedBody ?: error("missing request body"))
        assertEquals("global", row.getString("scope"))
        assertEquals(0, row.getJSONObject("payload").getJSONArray("admins").length())
        assertEquals(0, row.getJSONObject("payload").getJSONArray("cajeros").length())
    }

    @Test
    fun `fetch prefers direct Supabase state before legacy fallbacks`() {
        clearUsersPayloadMemoryCache()
        val payload = resolveUsersPayloadFetch(
            fetchLegacy = { "{\"source\":\"legacy\"}" },
            fetchDirect = { "{\"source\":\"direct\"}" },
            fetchRender = { "{\"source\":\"render\"}" },
        )

        assertEquals("direct", JSONObject(payload ?: error("missing payload")).getString("source"))
    }

    @Test
    fun `users payload memory cache avoids repeated remote fetches`() {
        clearUsersPayloadMemoryCache()
        var directCalls = 0

        val first = resolveUsersPayloadFetch(
            fetchLegacy = {
                "{\"source\":\"legacy\"}"
            },
            fetchDirect = {
                directCalls += 1
                "{\"source\":\"direct\"}"
            },
            fetchRender = { "{\"source\":\"render\"}" },
        )
        val second = resolveUsersPayloadFetch(
            fetchLegacy = {
                "{\"source\":\"legacy-2\"}"
            },
            fetchDirect = {
                directCalls += 1
                "{\"source\":\"direct-2\"}"
            },
            fetchRender = { "{\"source\":\"render-2\"}" },
        )

        assertEquals("direct", JSONObject(first ?: error("missing first payload")).getString("source"))
        assertEquals("direct", JSONObject(second ?: error("missing second payload")).getString("source"))
        assertEquals(1, directCalls)
        clearUsersPayloadMemoryCache()
    }

    @Test
    fun `save writes every available users mirror so another server sees cashier mode`() {
        val calls = mutableListOf<String>()

        persistUsersPayload(
            saveLegacy = { calls += "legacy" },
            saveDirect = { calls += "direct" },
            saveRender = { calls += "render" },
        )

        assertEquals(listOf("direct", "legacy", "render"), calls)
    }

    @Test
    fun `save succeeds when direct fails but render mirror accepts users payload`() {
        val calls = mutableListOf<String>()

        persistUsersPayload(
            saveLegacy = {
                calls += "legacy"
                throw IllegalStateException("legacy unavailable")
            },
            saveDirect = {
                calls += "direct"
                throw IllegalStateException("direct unavailable")
            },
            saveRender = { calls += "render" },
        )

        assertEquals(listOf("direct", "legacy", "render"), calls)
    }
}
