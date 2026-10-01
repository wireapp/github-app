package com.wire.github.util

import java.security.SecureRandom
import java.util.Base64

object ActionsTokenGenerator {
    private const val TOKEN_SIZE_BYTES = 32
    private val random = SecureRandom()

    fun generate(): String {
        val bytes = ByteArray(TOKEN_SIZE_BYTES)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}
