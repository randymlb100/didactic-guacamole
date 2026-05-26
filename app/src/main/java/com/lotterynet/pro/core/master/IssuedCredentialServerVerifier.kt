package com.lotterynet.pro.core.master

import com.lotterynet.pro.core.auth.SupabaseAuthBridgeClient
import com.lotterynet.pro.core.repository.UsersRepository

class IssuedCredentialServerVerifier(
    private val usersRepository: UsersRepository,
    private val authBridgeClient: SupabaseAuthBridgeClient = SupabaseAuthBridgeClient(),
) {
    fun ensureJwtReady(credentials: List<IssuedCredential>) {
        credentials.forEach { credential ->
            val account = usersRepository.findByIdOrUser(credential.username)
                ?: throw IllegalStateException("No se encontro ${credential.username} para validar JWT.")
            val session = authBridgeClient.legacyLogin(account, credential.password)
            if (session.accessToken.isNullOrBlank()) {
                throw IllegalStateException("Servidor no genero JWT para ${credential.username}.")
            }
        }
    }
}
