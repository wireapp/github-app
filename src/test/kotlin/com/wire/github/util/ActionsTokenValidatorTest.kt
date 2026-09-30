package com.wire.github.util

import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.sync.RedisCommands
import io.mockk.every
import io.mockk.mockk
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ActionsTokenValidatorTest {
    private val storage = mockk<RedisCommands<String, String>>()
    private val redisConnection = mockk<StatefulRedisConnection<String, String>>()
    private val validator by lazy { ActionsTokenValidator(redisConnection) }

    init {
        every { redisConnection.sync() } returns storage
    }

    @Test
    fun `matching stored token is valid`() {
        every { storage.get(CONVERSATION_ID.toStorageKey(CONVERSATION_DOMAIN)) } returns SECRET

        assertTrue(validator.isValid(CONVERSATION_ID, CONVERSATION_DOMAIN, SECRET))
    }

    @Test
    fun `wrong token is invalid`() {
        every { storage.get(CONVERSATION_ID.toStorageKey(CONVERSATION_DOMAIN)) } returns SECRET

        assertFalse(validator.isValid(CONVERSATION_ID, CONVERSATION_DOMAIN, "wrong-secret"))
    }

    @Test
    fun `missing stored token is invalid`() {
        every { storage.get(CONVERSATION_ID.toStorageKey(CONVERSATION_DOMAIN)) } returns null

        assertFalse(validator.isValid(CONVERSATION_ID, CONVERSATION_DOMAIN, SECRET))
    }

    private companion object {
        val CONVERSATION_ID = UUID.randomUUID().toString()
        const val CONVERSATION_DOMAIN = "conversation.example.com"
        const val SECRET = "conversation-secret"
    }
}
