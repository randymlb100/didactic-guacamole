package com.lotterynet.pro.core.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class SupabaseEdgeClientTest {
    @Test
    fun `function path always targets edge functions`() {
        val client = SupabaseEdgeClient(
            baseUrl = "https://example.supabase.co",
            apiKey = "test-key",
        )

        assertEquals("functions/v1/create-ticket", client.functionPath("create-ticket"))
        assertEquals("functions/v1/void-ticket", client.functionPath("/void-ticket/"))
    }

    @Test
    fun `authorization header prefers supabase auth token for edge calls`() {
        assertEquals("Bearer user-jwt", authorizationHeader("anon-key", "user-jwt"))
        assertEquals("Bearer anon-key", authorizationHeader("anon-key", null))
        assertEquals("Bearer anon-key", authorizationHeader("anon-key", ""))
    }

    @Test
    fun `server first operations require a real auth jwt`() {
        assertFalse(isLikelySupabaseJwt("anon-key"))
        assertTrue(isLikelySupabaseJwt("eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjMifQ.signature"))
    }

    @Test
    fun `missing auth jwt is classified as session issue not account block`() {
        val error = runCatching {
            requireSupabaseUserJwt(null)
        }.exceptionOrNull()

        assertTrue(error is SupabaseEdgeException)
        assertTrue(isSupabaseAuthRequired(error))
        assertEquals(
            "Sesion del servidor requerida. Inicia sesion con internet para continuar.",
            (error as SupabaseEdgeException).userMessage,
        )
    }

    @Test
    fun `edge timeout message is safe for critical operations`() {
        assertTrue(isSupabaseEdgeTimeout("canceling statement due to statement timeout"))
        assertEquals(
            "El servidor tardo demasiado validando la operacion. Intenta de nuevo.",
            presentSupabaseEdgeMessage("read timed out"),
        )
    }

    @Test
    fun `edge error message supports common response shapes`() {
        assertEquals("Saldo insuficiente", extractEdgeErrorMessage("""{"message":"Saldo insuficiente"}"""))
        assertEquals("Credenciales no configuradas", extractEdgeErrorMessage("""{"error":"Credenciales no configuradas"}"""))
    }
}
