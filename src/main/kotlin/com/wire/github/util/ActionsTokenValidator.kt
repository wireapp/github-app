package com.wire.github.util

import com.wire.github.RedisRepository
import com.wire.sdk.model.QualifiedId
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

class ActionsTokenValidator(
    private val redisRepository: RedisRepository
) {
    fun isValid(
        conversationId: QualifiedId,
        token: String
    ): Boolean {
        val storedToken = redisRepository.getActionSecret(conversationId) ?: return false
        return MessageDigest.isEqual(
            storedToken.toByteArray(StandardCharsets.UTF_8),
            token.toByteArray(StandardCharsets.UTF_8)
        )
    }
}
