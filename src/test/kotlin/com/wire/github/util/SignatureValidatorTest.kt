package com.wire.github.util

import com.wire.github.RedisRepository
import com.wire.sdk.model.QualifiedId
import io.mockk.every
import io.mockk.mockk
import java.io.IOException
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SignatureValidatorTest {
    private val redisRepository = mockk<RedisRepository>()
    private val validator = SignatureValidator(redisRepository)

    @Test
    fun `signature generated with the stored webhook secret is valid`() {
        every { redisRepository.getWebhookSecret(CONVERSATION_ID) } returns WEBHOOK_SECRET
        val signature = "sha1=${validator.generateHmacSha1(PAYLOAD, WEBHOOK_SECRET)}"

        assertTrue(
            validator.isValid(
                CONVERSATION_ID.id.toString(),
                CONVERSATION_ID.domain,
                signature,
                PAYLOAD
            )
        )
    }

    @Test
    fun `signature generated with another secret is invalid`() {
        every { redisRepository.getWebhookSecret(CONVERSATION_ID) } returns WEBHOOK_SECRET
        val signature = "sha1=${validator.generateHmacSha1(PAYLOAD, "another-secret")}"

        assertFalse(
            validator.isValid(
                CONVERSATION_ID.id.toString(),
                CONVERSATION_ID.domain,
                signature,
                PAYLOAD
            )
        )
    }

    @Test
    fun `missing webhook secret throws`() {
        every { redisRepository.getWebhookSecret(CONVERSATION_ID) } returns null

        assertFailsWith<IOException> {
            validator.isValid(
                CONVERSATION_ID.id.toString(),
                CONVERSATION_ID.domain,
                "sha1=unused",
                PAYLOAD
            )
        }
    }

    private companion object {
        val CONVERSATION_ID = QualifiedId(
            UUID.fromString("e37b6009-fbaa-4bb9-b345-7bdbf7d5462e"),
            "conversation.example.com"
        )
        const val WEBHOOK_SECRET = "webhook-secret"
        const val PAYLOAD = "payload"
    }
}
