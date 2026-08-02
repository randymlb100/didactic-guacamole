package com.lotterynet.pro.core.sync

import com.sun.net.httpserver.HttpServer
import com.lotterynet.pro.core.remote.SupabaseEdgeException
import com.lotterynet.pro.core.remote.SupabaseEdgeFailureReason
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.net.InetSocketAddress

class NativeTicketRemoteStoreTest {
    @Test
    fun `ticket fetch and upsert use bearer token while updated-at stays lightweight anonymous`() {
        clearTicketUpdatedAtMemoryCache()
        val requests = mutableListOf<CapturedRequest>()
        val server = ticketServer(requests)
        server.start()
        try {
            val token = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ1In0.signature"
            val store = NativeTicketRemoteStore(
                baseUrl = "http://127.0.0.1:${server.address.port}",
                apiKey = "anon-key",
                bearerTokenProvider = { token },
            )

            store.fetchSnapshot("admin-1")
            store.upsertSnapshot("admin-1", tickets = emptyList(), deletedIds = emptySet())
            assertEquals("2026-06-04T12:00:00Z", store.fetchUpdatedAtFresh("admin-1"))

            assertEquals(listOf("fetch", "upsert", "updated-at"), requests.map { it.body.getString("action") })
            assertTrue(requests.all { it.path == "/functions/v1/get-ticket-list" })
            assertEquals(listOf("Bearer $token", "Bearer $token", "Bearer anon-key"), requests.map { it.authorization })
            assertEquals(listOf(true, true, false), requests.map { it.body.getBoolean("includeOfficialStamp") })
            assertEquals(false, requests.first().body.getBoolean("preferSnapshot"))
            assertEquals(false, requests.first().body.getBoolean("processPendingPrizes"))
        } finally {
            server.stop(0)
            clearTicketUpdatedAtMemoryCache()
        }
    }

    @Test
    fun `concurrent ticket snapshot fetches share one network request`() {
        clearTicketUpdatedAtMemoryCache()
        val requestCount = AtomicInteger(0)
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            requestCount.incrementAndGet()
            Thread.sleep(250)
            val responseJson = JSONObject()
                .put("ok", true)
                .put("payload", JSONObject().put("tickets", emptyList<String>()).put("deletedIds", emptyList<String>()))
                .put("source", "authoritative")
                .put("completeScope", true)
            val response = responseJson.toString().toByteArray(Charsets.UTF_8)
            exchange.sendResponseHeaders(200, response.size.toLong())
            exchange.responseBody.use { it.write(response) }
        }
        server.start()
        try {
            val store = NativeTicketRemoteStore(
                baseUrl = "http://127.0.0.1:${server.address.port}",
                apiKey = "anon-key",
            )
            val startGate = CountDownLatch(1)
            val executor = Executors.newFixedThreadPool(2)
            try {
                val futureA = executor.submit<Any?> {
                    startGate.await(2, TimeUnit.SECONDS)
                    store.fetchSnapshot("admin-1")
                }
                val futureB = executor.submit<Any?> {
                    startGate.await(2, TimeUnit.SECONDS)
                    store.fetchSnapshot("admin-1")
                }
                startGate.countDown()

                assertEquals(0, (futureA.get(5, TimeUnit.SECONDS) as NativeTicketRemoteSnapshot).tickets.size)
                assertEquals(0, (futureB.get(5, TimeUnit.SECONDS) as NativeTicketRemoteSnapshot).tickets.size)
                assertEquals(1, requestCount.get())
            } finally {
                executor.shutdownNow()
            }
        } finally {
            server.stop(0)
            clearTicketUpdatedAtMemoryCache()
        }
    }

    @Test
    fun `concurrent ticket snapshot fetches stay coalesced when bearer token rotates`() {
        clearTicketUpdatedAtMemoryCache()
        val requestCount = AtomicInteger(0)
        val tokenCalls = AtomicInteger(0)
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            requestCount.incrementAndGet()
            Thread.sleep(250)
            val responseJson = JSONObject()
                .put("ok", true)
                .put("payload", JSONObject().put("tickets", emptyList<String>()).put("deletedIds", emptyList<String>()))
                .put("source", "authoritative")
                .put("completeScope", true)
            val response = responseJson.toString().toByteArray(Charsets.UTF_8)
            exchange.sendResponseHeaders(200, response.size.toLong())
            exchange.responseBody.use { it.write(response) }
        }
        server.start()
        try {
            val store = NativeTicketRemoteStore(
                baseUrl = "http://127.0.0.1:${server.address.port}",
                apiKey = "anon-key",
                bearerTokenProvider = {
                    if (tokenCalls.incrementAndGet() % 2 == 0) {
                        "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ0b2tlbi0yIn0.signature"
                    } else {
                        "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ0b2tlbi0xIn0.signature"
                    }
                },
            )
            val startGate = CountDownLatch(1)
            val executor = Executors.newFixedThreadPool(2)
            try {
                val futureA = executor.submit<Any?> {
                    startGate.await(2, TimeUnit.SECONDS)
                    store.fetchSnapshot("admin-1")
                }
                val futureB = executor.submit<Any?> {
                    startGate.await(2, TimeUnit.SECONDS)
                    store.fetchSnapshot("admin-1")
                }
                startGate.countDown()

                assertEquals(0, (futureA.get(5, TimeUnit.SECONDS) as NativeTicketRemoteSnapshot).tickets.size)
                assertEquals(0, (futureB.get(5, TimeUnit.SECONDS) as NativeTicketRemoteSnapshot).tickets.size)
                assertEquals(1, requestCount.get())
            } finally {
                executor.shutdownNow()
            }
        } finally {
            server.stop(0)
            clearTicketUpdatedAtMemoryCache()
        }
    }

    @Test
    fun `sequential ticket snapshot fetches reuse the recent snapshot window`() {
        clearTicketUpdatedAtMemoryCache()
        val requestCount = AtomicInteger(0)
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            requestCount.incrementAndGet()
            val responseJson = JSONObject()
                .put("ok", true)
                .put("payload", JSONObject().put("tickets", emptyList<String>()).put("deletedIds", emptyList<String>()))
                .put("source", "authoritative")
                .put("completeScope", true)
            val response = responseJson.toString().toByteArray(Charsets.UTF_8)
            exchange.sendResponseHeaders(200, response.size.toLong())
            exchange.responseBody.use { it.write(response) }
        }
        server.start()
        try {
            val store = NativeTicketRemoteStore(
                baseUrl = "http://127.0.0.1:${server.address.port}",
                apiKey = "anon-key",
            )

            val first = store.fetchSnapshot("admin-1")
            val second = store.fetchSnapshot("admin-1")

            assertEquals(0, first.tickets.size)
            assertEquals(0, second.tickets.size)
            assertEquals(1, requestCount.get())
        } finally {
            server.stop(0)
            clearTicketUpdatedAtMemoryCache()
        }
    }

    @Test
    fun `basic updated-at remains anonymous compatible without bearer provider`() {
        clearTicketUpdatedAtMemoryCache()
        val requests = mutableListOf<CapturedRequest>()
        val server = ticketServer(requests)
        server.start()
        try {
            val store = NativeTicketRemoteStore(
                baseUrl = "http://127.0.0.1:${server.address.port}",
                apiKey = "anon-key",
            )

            assertEquals("2026-06-04T12:00:00Z", store.fetchUpdatedAtFresh("admin-1"))

            val request = requests.single()
            assertEquals("updated-at", request.body.getString("action"))
            assertEquals("Bearer anon-key", request.authorization)
            assertEquals(false, request.body.getBoolean("includeOfficialStamp"))
        } finally {
            server.stop(0)
            clearTicketUpdatedAtMemoryCache()
        }
    }

    @Test
    fun `fresh updated-at reuses the recent value instead of hitting the server twice`() {
        clearTicketUpdatedAtMemoryCache()
        val requestCount = AtomicInteger(0)
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            requestCount.incrementAndGet()
            val response = """{"ok":true,"updatedAt":"2026-06-04T12:00:00Z"}""".toByteArray(Charsets.UTF_8)
            exchange.sendResponseHeaders(200, response.size.toLong())
            exchange.responseBody.use { it.write(response) }
        }
        server.start()
        try {
            val store = NativeTicketRemoteStore(
                baseUrl = "http://127.0.0.1:${server.address.port}",
                apiKey = "anon-key",
            )

            assertEquals("2026-06-04T12:00:00Z", store.fetchUpdatedAtFresh("admin-1"))
            assertEquals("2026-06-04T12:00:00Z", store.fetchUpdatedAtFresh("admin-1"))
            assertEquals(1, requestCount.get())
        } finally {
            server.stop(0)
            clearTicketUpdatedAtMemoryCache()
        }
    }

    @Test
    fun `literal null owner keys do not call ticket list edge`() {
        clearTicketUpdatedAtMemoryCache()
        val requests = mutableListOf<CapturedRequest>()
        val server = ticketServer(requests)
        server.start()
        try {
            val store = NativeTicketRemoteStore(
                baseUrl = "http://127.0.0.1:${server.address.port}",
                apiKey = "anon-key",
            )

            assertEquals(null, store.fetchUpdatedAtFresh("null"))
            assertEquals(emptyList<Any>(), store.fetchSnapshot(" undefined ").tickets)

            assertEquals(emptyList<CapturedRequest>(), requests)
        } finally {
            server.stop(0)
            clearTicketUpdatedAtMemoryCache()
        }
    }

    @Test
    fun `ticket upsert retries once with refreshed bearer token when server rejects stale session`() {
        clearTicketUpdatedAtMemoryCache()
        val requests = mutableListOf<CapturedRequest>()
        val server = ticketServer(requests, rejectFirstAuthenticatedRequest = true)
        server.start()
        try {
            val staleToken = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJvbGQifQ.signature"
            val freshToken = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJuZXcifQ.signature"
            val store = NativeTicketRemoteStore(
                baseUrl = "http://127.0.0.1:${server.address.port}",
                apiKey = "anon-key",
                bearerTokenProvider = { staleToken },
                bearerTokenRefresher = { freshToken },
            )

            store.upsertSnapshot("admin-1", tickets = emptyList(), deletedIds = emptySet())

            assertEquals(listOf("Bearer $staleToken", "Bearer $freshToken"), requests.map { it.authorization })
            assertEquals(listOf("upsert", "upsert"), requests.map { it.body.getString("action") })
        } finally {
            server.stop(0)
            clearTicketUpdatedAtMemoryCache()
        }
    }

    @Test
    fun `bounded ticket read is authoritative authenticated and reports completeness`() {
        clearTicketUpdatedAtMemoryCache()
        val requests = mutableListOf<CapturedRequest>()
        val server = ticketServer(requests)
        server.start()
        try {
            val token = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ1In0.signature"
            val store = NativeTicketRemoteStore(
                baseUrl = "http://127.0.0.1:${server.address.port}",
                apiKey = "anon-key",
                bearerTokenProvider = { token },
            )

            val snapshot = store.fetchSnapshot(
                ownerKey = "ADM-163C38",
                fromDate = "2026-06-18",
                toDate = "2026-06-18",
                limit = 1000,
            )

            val request = requests.single()
            assertEquals("fetch", request.body.getString("action"))
            assertEquals("2026-06-18", request.body.getString("fromDate"))
            assertEquals("2026-06-18", request.body.getString("toDate"))
            assertEquals(1000, request.body.getInt("limit"))
            assertFalse(request.body.getBoolean("preferSnapshot"))
            assertTrue(request.body.getBoolean("includeOfficialStamp"))
            assertEquals("Bearer $token", request.authorization)
            assertEquals("authoritative", snapshot.source)
            assertTrue(snapshot.completeScope)
        } finally {
            server.stop(0)
            clearTicketUpdatedAtMemoryCache()
        }
    }

    @Test
    fun `bounded ticket read without user jwt fails before network instead of using snapshot`() {
        clearTicketUpdatedAtMemoryCache()
        val requests = mutableListOf<CapturedRequest>()
        val server = ticketServer(requests)
        server.start()
        try {
            val store = NativeTicketRemoteStore(
                baseUrl = "http://127.0.0.1:${server.address.port}",
                apiKey = "anon-key",
            )

            val error = try {
                store.fetchSnapshot(
                    ownerKey = "ADM-163C38",
                    fromDate = "2026-06-18",
                    toDate = "2026-06-18",
                    limit = 1000,
                )
                fail("Expected an authenticated bounded-read failure")
                null
            } catch (failure: SupabaseEdgeException) {
                failure
            }

            assertEquals(SupabaseEdgeFailureReason.AUTH_REQUIRED, error?.reason)
            assertTrue(requests.isEmpty())
        } finally {
            server.stop(0)
            clearTicketUpdatedAtMemoryCache()
        }
    }

    private fun ticketServer(
        requests: MutableList<CapturedRequest>,
        rejectFirstAuthenticatedRequest: Boolean = false,
    ): HttpServer {
        return HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { exchange ->
                val body = exchange.requestBody.use { input ->
                    input.readBytes().toString(Charsets.UTF_8)
                }
                val json = JSONObject(body.ifBlank { "{}" })
                requests += CapturedRequest(
                    path = exchange.requestURI.path,
                    authorization = exchange.requestHeaders.getFirst("Authorization"),
                    body = json,
                )
                if (rejectFirstAuthenticatedRequest && requests.size == 1) {
                    val response = JSONObject()
                        .put("ok", false)
                        .put("message", "Sesion invalida.")
                        .toString()
                        .toByteArray(Charsets.UTF_8)
                    exchange.sendResponseHeaders(401, response.size.toLong())
                    exchange.responseBody.use { it.write(response) }
                    return@createContext
                }
                val responseJson = when (json.optString("action")) {
                    "updated-at" -> JSONObject()
                        .put("ok", true)
                        .put("updatedAt", "2026-06-04T12:00:00Z")
                    "fetch" -> JSONObject()
                        .put("ok", true)
                        .put("payload", JSONObject().put("tickets", emptyList<String>()).put("deletedIds", emptyList<String>()))
                        .put("source", "authoritative")
                        .put("completeScope", true)
                    else -> JSONObject().put("ok", true)
                }
                val response = responseJson.toString().toByteArray(Charsets.UTF_8)
                exchange.sendResponseHeaders(200, response.size.toLong())
                exchange.responseBody.use { it.write(response) }
            }
        }
    }

    private data class CapturedRequest(
        val path: String,
        val authorization: String?,
        val body: JSONObject,
    )
}
