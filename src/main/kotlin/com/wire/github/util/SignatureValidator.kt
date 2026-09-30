package com.wire.github.util

import io.lettuce.core.api.StatefulRedisConnection
import java.io.IOException
import java.util.Locale
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import org.koin.core.context.GlobalContext

class SignatureValidator {
    private val redisConnection = GlobalContext.get().get<StatefulRedisConnection<String, String>>()
    private val storage = redisConnection.sync()

    @Throws(Exception::class)
    fun isValid(
        conversationId: String,
        conversationDomain: String,
        signature: String,
        payload: String
    ): Boolean {
        val storageKey = conversationId.toStorageKey(domain = conversationDomain)
        val secret = storage.get(storageKey)

        if (secret == null) {
            throw IOException("Missing secret for Conversation: $conversationId")
        }

        val generatedHmacSha1: String = generateHmacSha1(payload, secret)
        val challenge = String.format(Locale.getDefault(), "sha1=%s", generatedHmacSha1)

        return challenge == signature
    }

    internal fun generateHmacSha1(
        data: String,
        secret: String
    ): String {
        val keySpec = SecretKeySpec(secret.toByteArray(), ALGORITHM)
        val mac = Mac.getInstance(ALGORITHM)
        mac.init(keySpec)
        val digest = mac.doFinal(data.toByteArray())

        // Convert to hex string
        return digest.joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val ALGORITHM = "HmacSHA1"
    }
}
