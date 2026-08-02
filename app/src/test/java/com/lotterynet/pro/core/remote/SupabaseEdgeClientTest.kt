package com.lotterynet.pro.core.remote

import java.net.UnknownHostException
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
    fun `invalid session response is classified as auth required`() {
        assertEquals(
            SupabaseEdgeFailureReason.AUTH_REQUIRED,
            resolveSupabaseEdgeFailureReason(401, "Sesion invalida."),
        )
        assertEquals(
            SupabaseEdgeFailureReason.REMOTE_ERROR,
            resolveSupabaseEdgeFailureReason(500, "Sesion invalida."),
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

    @Test
    fun `unknown host is presented as actionable connection issue`() {
        assertEquals(
            "No se pudo encontrar el servidor. Revisa internet o cambia el DNS/red del equipo.",
            presentSupabaseTransportMessage(UnknownHostException("unhoulkujbtsypccpirc.supabase.co")),
        )
    }

    @Test
    fun `cloudflare html body is presented as supabase network issue`() {
        val html = """
            <!DOCTYPE html>
            <html><body>
            DNS resolution error
            Error 1001
            Cloudflare is currently unable to resolve your requested domain
            </body></html>
        """.trimIndent()

        assertEquals(
            "Supabase no respondio correctamente. Revisa la red e intenta de nuevo.",
            presentSupabaseHttpFailureMessage(html, "text/html"),
        )
    }
}
