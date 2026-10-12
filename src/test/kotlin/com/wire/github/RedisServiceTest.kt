package com.wire.github

import com.wire.sdk.model.QualifiedId
import io.lettuce.core.api.sync.RedisCommands
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.IOException
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RedisServiceTest {
    private val storage = mockk<RedisCommands<String, String>>()
    private val service = RedisService(storage)

    @Test
    fun `get webhook secret reads the conversation storage key`() {
        every { storage.get(WEBHOOK_STORAGE_KEY) } returns WEBHOOK_SECRET

        assertEquals(WEBHOOK_SECRET, service.getWebhookSecret(CONVERSATION_ID))
    }

    @Test
    fun `set webhook secret writes the conversation storage key`() {
        every { storage.set(WEBHOOK_STORAGE_KEY, WEBHOOK_SECRET) } returns "OK"

        service.setWebhookSecret(CONVERSATION_ID, WEBHOOK_SECRET)

        verify(exactly = 1) { storage.set(WEBHOOK_STORAGE_KEY, WEBHOOK_SECRET) }
    }

    @Test
    fun `get action secret reads the Actions storage key`() {
        every { storage.get(ACTION_STORAGE_KEY) } returns ACTION_SECRET

        assertEquals(ACTION_SECRET, service.getActionSecret(CONVERSATION_ID))
    }

    @Test
    fun `set action secret writes the Actions storage key`() {
        every { storage.set(ACTION_STORAGE_KEY, ACTION_SECRET) } returns "OK"

        service.setActionSecret(CONVERSATION_ID, ACTION_SECRET)

        verify(exactly = 1) { storage.set(ACTION_STORAGE_KEY, ACTION_SECRET) }
    }

    @Test
    fun `matching stored Actions token is valid`() {
        every { storage.get(ACTION_STORAGE_KEY) } returns ACTION_SECRET

        assertTrue(service.isActionsTokenValid(CONVERSATION_ID, ACTION_SECRET))
    }

    @Test
    fun `wrong Actions token is invalid`() {
        every { storage.get(ACTION_STORAGE_KEY) } returns ACTION_SECRET

        assertFalse(service.isActionsTokenValid(CONVERSATION_ID, "wrong-token"))
    }

    @Test
    fun `missing stored Actions token is invalid`() {
        every { storage.get(ACTION_STORAGE_KEY) } returns null

        assertFalse(service.isActionsTokenValid(CONVERSATION_ID, ACTION_SECRET))
    }

    @Test
    fun `signature generated with the stored webhook secret is valid`() {
        every { storage.get(WEBHOOK_STORAGE_KEY) } returns WEBHOOK_SECRET
        val signature = "sha1=${service.generateHmacSha1(PAYLOAD, WEBHOOK_SECRET)}"

        assertTrue(
            service.isWebhookSignatureValid(
                CONVERSATION_ID.id.toString(),
                CONVERSATION_ID.domain,
                signature,
                PAYLOAD
            )
        )
    }

    @Test
    fun `signature generated with another secret is invalid`() {
        every { storage.get(WEBHOOK_STORAGE_KEY) } returns WEBHOOK_SECRET
        val signature = "sha1=${service.generateHmacSha1(PAYLOAD, "another-secret")}"

        assertFalse(
            service.isWebhookSignatureValid(
                CONVERSATION_ID.id.toString(),
                CONVERSATION_ID.domain,
                signature,
                PAYLOAD
            )
        )
    }

    @Test
    fun `missing webhook secret throws`() {
        every { storage.get(WEBHOOK_STORAGE_KEY) } returns null

        assertFailsWith<IOException> {
            service.isWebhookSignatureValid(
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
        const val WEBHOOK_STORAGE_KEY =
            "github-app:e37b6009-fbaa-4bb9-b345-7bdbf7d5462e@conversation.example.com"
        const val ACTION_STORAGE_KEY = "$WEBHOOK_STORAGE_KEY:actions-token"
        const val WEBHOOK_SECRET = "webhook-secret"
        const val ACTION_SECRET = "action-secret"
        const val PAYLOAD = "payload"
    }
}
