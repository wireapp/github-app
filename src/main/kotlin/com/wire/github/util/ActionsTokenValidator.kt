package com.wire.github.util

import io.lettuce.core.api.StatefulRedisConnection
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

class ActionsTokenValidator(
    redisConnection: StatefulRedisConnection<String, String>
) {
    private val storage = redisConnection.sync()

    fun isValid(
        conversationId: String,
        conversationDomain: String,
        token: String
    ): Boolean {
        val storageKey = conversationId.toStorageKey(domain = conversationDomain)
        val secret = storage.get(storageKey) ?: return false
        return MessageDigest.isEqual(
            secret.toByteArray(StandardCharsets.UTF_8),
            token.toByteArray(StandardCharsets.UTF_8)
        )
    }
}
