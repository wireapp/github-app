package com.wire.github

import com.wire.sdk.model.QualifiedId
import io.lettuce.core.api.sync.RedisCommands
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class RedisService(
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

    fun isActionsTokenValid(
        conversationId: QualifiedId,
        token: String
    ): Boolean {
        val storedToken = getActionSecret(conversationId) ?: return false
        return MessageDigest.isEqual(
            storedToken.toByteArray(StandardCharsets.UTF_8),
            token.toByteArray(StandardCharsets.UTF_8)
        )
    }

    @Throws(Exception::class)
    fun isWebhookSignatureValid(
        conversationId: String,
        conversationDomain: String,
        signature: String,
        payload: String
    ): Boolean {
        val qualifiedConversationId = QualifiedId(
            UUID.fromString(conversationId),
            conversationDomain
        )
        val secret = getWebhookSecret(qualifiedConversationId) ?: throw IOException(
            "Missing secret for Conversation: $conversationId"
        )
        val generatedHmacSha1 = generateHmacSha1(payload, secret)
        val challenge = String.format(Locale.getDefault(), "sha1=%s", generatedHmacSha1)

        return challenge == signature
    }

    internal fun generateHmacSha1(
        data: String,
        secret: String
    ): String {
        val keySpec = SecretKeySpec(secret.toByteArray(), HMAC_ALGORITHM)
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        mac.init(keySpec)
        val digest = mac.doFinal(data.toByteArray())

        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun QualifiedId.toWebhookStorageKey() = "$STORAGE_KEY_PREFIX$id@$domain"

    private fun QualifiedId.toActionStorageKey() = "${toWebhookStorageKey()}$ACTION_TOKEN_SUFFIX"

    private companion object {
        const val STORAGE_KEY_PREFIX = "github-app:"
        const val ACTION_TOKEN_SUFFIX = ":actions-token"
        const val HMAC_ALGORITHM = "HmacSHA1"
    }
}
