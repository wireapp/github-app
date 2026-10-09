package com.wire.github

import com.wire.sdk.model.QualifiedId
import io.lettuce.core.api.sync.RedisCommands

class RedisRepository(
    private val storage: RedisCommands<String, String>
) {
    fun getWebhookSecret(conversationId: QualifiedId): String? =
        storage.get(conversationId.toWebhookStorageKey())

    fun setWebhookSecret(
        conversationId: QualifiedId,
        secret: String
    ) {
        storage.set(conversationId.toWebhookStorageKey(), secret)
    }

    fun getActionSecret(conversationId: QualifiedId): String? =
        storage.get(conversationId.toActionStorageKey())

    fun setActionSecret(
        conversationId: QualifiedId,
        secret: String
    ) {
        storage.set(conversationId.toActionStorageKey(), secret)
    }

    private fun QualifiedId.toWebhookStorageKey() = "$STORAGE_KEY_PREFIX$id@$domain"

    private fun QualifiedId.toActionStorageKey() = "${toWebhookStorageKey()}$ACTION_TOKEN_SUFFIX"

    private companion object {
        const val STORAGE_KEY_PREFIX = "github-app:"
        const val ACTION_TOKEN_SUFFIX = ":actions-token"
    }
}
