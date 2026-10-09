package com.wire.github

import com.wire.sdk.model.QualifiedId
import io.lettuce.core.api.sync.RedisCommands
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class RedisRepositoryTest {
    private val storage = mockk<RedisCommands<String, String>>()
    private val repository = RedisRepository(storage)

    @Test
    fun `get webhook secret reads the conversation storage key`() {
        every { storage.get(WEBHOOK_STORAGE_KEY) } returns WEBHOOK_SECRET

        assertEquals(WEBHOOK_SECRET, repository.getWebhookSecret(CONVERSATION_ID))
    }

    @Test
    fun `set webhook secret writes the conversation storage key`() {
        every { storage.set(WEBHOOK_STORAGE_KEY, WEBHOOK_SECRET) } returns "OK"

        repository.setWebhookSecret(CONVERSATION_ID, WEBHOOK_SECRET)

        verify(exactly = 1) { storage.set(WEBHOOK_STORAGE_KEY, WEBHOOK_SECRET) }
    }

    @Test
    fun `get action secret reads the Actions storage key`() {
        every { storage.get(ACTION_STORAGE_KEY) } returns ACTION_SECRET

        assertEquals(ACTION_SECRET, repository.getActionSecret(CONVERSATION_ID))
    }

    @Test
    fun `set action secret writes the Actions storage key`() {
        every { storage.set(ACTION_STORAGE_KEY, ACTION_SECRET) } returns "OK"

        repository.setActionSecret(CONVERSATION_ID, ACTION_SECRET)

        verify(exactly = 1) { storage.set(ACTION_STORAGE_KEY, ACTION_SECRET) }
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
    }
}
