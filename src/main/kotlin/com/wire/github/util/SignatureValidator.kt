package com.wire.github.util

import java.util.Locale
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class SignatureValidator {
    fun isValid(
        signature: String,
        payload: String,
        secret: String
    ): Boolean {
        val generatedHmacSha256 = generateHmacSha256(payload, secret)
        val challenge = String.format(Locale.getDefault(), "sha256=%s", generatedHmacSha256)

        return challenge == signature
    }

    internal fun generateHmacSha256(
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
        const val ALGORITHM = "HmacSHA256"
    }
}
