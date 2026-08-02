package com.lotterynet.pro.core.master

import com.lotterynet.pro.core.remote.SupabaseEdgeClient
import com.sun.net.httpserver.HttpServer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicReference

class SupabaseMasterConfigRemoteStoreTest {
    @Test
    fun `master config upsert sends fresh bearer token`() {
        clearMasterMemoryCache()
        val capturedAuth = AtomicReference<String>()
        val capturedPath = AtomicReference<String>()
        val capturedBody = AtomicReference<JSONObject>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            capturedAuth.set(exchange.requestHeaders.getFirst("Authorization"))
            capturedPath.set(exchange.requestURI.path)
            val body = exchange.requestBody.use { input ->
                input.readBytes().toString(Charsets.UTF_8)
            }
            capturedBody.set(JSONObject(body))
            val response = """{"ok":true}""".toByteArray(Charsets.UTF_8)
            exchange.sendResponseHeaders(200, response.size.toLong())
            exchange.responseBody.use { it.write(response) }
        }
        server.start()
        try {
            val token = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ1In0.signature"
            val store = SupabaseMasterConfigRemoteStore(
                edgeClient = SupabaseEdgeClient(
                    baseUrl = "http://127.0.0.1:${server.address.port}",
                    apiKey = "anon-key",
                ),
                bearerTokenProvider = { token },
            )

            store.upsertJsonValue("sys_test", """{"enabled":true}""")

            assertEquals("/functions/v1/update-master-config", capturedPath.get())
            assertEquals("Bearer $token", capturedAuth.get())
            assertEquals("sys_test", capturedBody.get().getString("key"))
            assertEquals(true, capturedBody.get().getJSONObject("payload").getBoolean("enabled"))
        } finally {
            server.stop(0)
            clearMasterMemoryCache()
        }
    }

    @Test
    fun `master config concurrent fetches are coalesced into one network request`() {
        clearMasterMemoryCache()
        val requestCount = AtomicInteger(0)
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            requestCount.incrementAndGet()
            Thread.sleep(250)
            val response = """{"ok":true,"payload":{"mode":"safe"}}""".toByteArray(Charsets.UTF_8)
            exchange.sendResponseHeaders(200, response.size.toLong())
            exchange.responseBody.use { it.write(response) }
        }
        server.start()
        try {
            val store = SupabaseMasterConfigRemoteStore(
                edgeClient = SupabaseEdgeClient(
                    baseUrl = "http://127.0.0.1:${server.address.port}",
                    apiKey = "anon-key",
                    connectTimeoutMs = 2_500,
                    readTimeoutMs = 3_500,
                ),
            )
            val startGate = CountDownLatch(1)
            val executor = Executors.newFixedThreadPool(2)
            try {
                val futureA = executor.submit<Any?> {
                    startGate.await(2, TimeUnit.SECONDS)
                    store.fetchValue("system_modes:admin-1")
                }
                val futureB = executor.submit<Any?> {
                    startGate.await(2, TimeUnit.SECONDS)
                    store.fetchValue("system_modes:admin-1")
                }
                startGate.countDown()

                assertEquals("safe", (futureA.get(5, TimeUnit.SECONDS) as JSONObject).getString("mode"))
                assertEquals("safe", (futureB.get(5, TimeUnit.SECONDS) as JSONObject).getString("mode"))
                assertEquals(1, requestCount.get())
            } finally {
                executor.shutdownNow()
            }
        } finally {
            server.stop(0)
            clearMasterMemoryCache()
        }
    }

    @Test
    fun `master config concurrent fetches stay coalesced when bearer token rotates`() {
        clearMasterMemoryCache()
        val requestCount = AtomicInteger(0)
        val tokenCalls = AtomicInteger(0)
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            requestCount.incrementAndGet()
            Thread.sleep(250)
            val response = """{"ok":true,"payload":{"mode":"safe"}}""".toByteArray(Charsets.UTF_8)
            exchange.sendResponseHeaders(200, response.size.toLong())
            exchange.responseBody.use { it.write(response) }
        }
        server.start()
        try {
            val store = SupabaseMasterConfigRemoteStore(
                edgeClient = SupabaseEdgeClient(
                    baseUrl = "http://127.0.0.1:${server.address.port}",
                    apiKey = "anon-key",
                    connectTimeoutMs = 2_500,
                    readTimeoutMs = 3_500,
                ),
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
                    store.fetchValue("system_modes:admin-1")
                }
                val futureB = executor.submit<Any?> {
                    startGate.await(2, TimeUnit.SECONDS)
                    store.fetchValue("system_modes:admin-1")
                }
                startGate.countDown()

                assertEquals("safe", (futureA.get(5, TimeUnit.SECONDS) as JSONObject).getString("mode"))
                assertEquals("safe", (futureB.get(5, TimeUnit.SECONDS) as JSONObject).getString("mode"))
                assertEquals(1, requestCount.get())
            } finally {
                executor.shutdownNow()
            }
        } finally {
            server.stop(0)
            clearMasterMemoryCache()
        }
    }

    @Test
    fun `master config refresh value reuses the recent server answer instead of refetching immediately`() {
        clearMasterMemoryCache()
        val requestCount = AtomicInteger(0)
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            requestCount.incrementAndGet()
            val response = """{"ok":true,"payload":{"mode":"safe"}}""".toByteArray(Charsets.UTF_8)
            exchange.sendResponseHeaders(200, response.size.toLong())
            exchange.responseBody.use { it.write(response) }
        }
        server.start()
        try {
            val store = SupabaseMasterConfigRemoteStore(
                edgeClient = SupabaseEdgeClient(
                    baseUrl = "http://127.0.0.1:${server.address.port}",
                    apiKey = "anon-key",
                ),
            )

            assertEquals("safe", (store.refreshValue("system_modes:admin-1") as JSONObject).getString("mode"))
            assertEquals("safe", (store.refreshValue("system_modes:admin-1") as JSONObject).getString("mode"))
            assertEquals(2, requestCount.get())
        } finally {
            server.stop(0)
            clearMasterMemoryCache()
        }
    }
}
