package com.lotterynet.pro.core.users

import com.lotterynet.pro.core.remote.SupabaseEdgeClient
import com.lotterynet.pro.core.remote.SupabaseEdgeException
import com.lotterynet.pro.core.remote.SupabaseEdgeFailureReason
import com.lotterynet.pro.core.model.ActiveSession
import com.lotterynet.pro.core.model.UserAccount
import com.lotterynet.pro.core.model.UserRole
import com.sun.net.httpserver.HttpServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicReference

class UserPasswordBackendClientTest {

    @Test
    fun `password change payload names actor and target`() {
        val payload = buildChangeUserPasswordPayload(
            session = ActiveSession(
                userId = "master",
                username = "master",
                role = UserRole.MASTER,
                banca = null,
                territory = null,
            ),
            target = UserAccount(id = "adm-1", user = "admin01", role = UserRole.ADMIN),
            newPassword = "clave123",
        )

        assertEquals("master", payload.getString("actorId"))
        assertEquals("master", payload.getString("actorUser"))
        assertEquals("master", payload.getString("actorRole"))
        assertEquals("adm-1", payload.getString("targetId"))
        assertEquals("admin01", payload.getString("targetUser"))
        assertEquals("admin", payload.getString("targetRole"))
        assertEquals("clave123", payload.getString("newPassword"))
    }

    @Test
    fun `password change sends fresh bearer token to edge function`() {
        val capturedAuth = AtomicReference<String>()
        val capturedPath = AtomicReference<String>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            capturedAuth.set(exchange.requestHeaders.getFirst("Authorization"))
            capturedPath.set(exchange.requestURI.path)
            val response = """{"targetUser":"admin01","targetId":"adm-1","authUpdated":true}"""
                .toByteArray(Charsets.UTF_8)
            exchange.sendResponseHeaders(200, response.size.toLong())
            exchange.responseBody.use { it.write(response) }
        }
        server.start()
        try {
            val token = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ1In0.signature"
            val client = UserPasswordBackendClient(
                edgeClient = SupabaseEdgeClient(
                    baseUrl = "http://127.0.0.1:${server.address.port}",
                    apiKey = "anon-key",
                ),
                bearerTokenProvider = { token },
            )

            val result = client.changePassword(
                session = ActiveSession(
                    userId = "master",
                    username = "master",
                    role = UserRole.MASTER,
                    banca = null,
                    territory = null,
                ),
                target = UserAccount(id = "adm-1", user = "admin01", role = UserRole.ADMIN),
                newPassword = "clave123",
            )

            assertEquals(true, result.authUpdated)
            assertEquals("/functions/v1/change-user-password", capturedPath.get())
            assertEquals("Bearer $token", capturedAuth.get())
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `password change fails clearly when jwt is missing`() {
        val error = runCatching {
            UserPasswordBackendClient().changePassword(
                session = ActiveSession(
                    userId = "master",
                    username = "master",
                    role = UserRole.MASTER,
                    banca = null,
                    territory = null,
                ),
                target = UserAccount(id = "adm-1", user = "admin01", role = UserRole.ADMIN),
                newPassword = "clave123",
            )
        }.exceptionOrNull()

        assertTrue(error is SupabaseEdgeException)
        assertEquals(SupabaseEdgeFailureReason.AUTH_REQUIRED, (error as SupabaseEdgeException).reason)
        assertEquals(
            "Sesion del servidor requerida. Inicia sesion con internet para continuar.",
            error.userMessage,
        )
    }
}
