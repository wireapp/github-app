package com.wire.github.util

import com.wire.sdk.model.QualifiedId
import io.lettuce.core.api.sync.RedisCommands
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

class ActionsTokenValidator(
    private val storage: RedisCommands<String, String>
) {
    fun isValid(
        conversationId: QualifiedId,
        token: String
    ): Boolean {
        val storageKey = conversationId.toActionsTokenStorageKey()
        val storedToken = storage.get(storageKey) ?: return false
        return MessageDigest.isEqual(
            storedToken.toByteArray(StandardCharsets.UTF_8),
            token.toByteArray(StandardCharsets.UTF_8)
        )
    }
}
