package com.wire.github.util

import com.wire.sdk.model.QualifiedId
import io.lettuce.core.api.sync.RedisCommands
import io.mockk.every
import io.mockk.mockk
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ActionsTokenValidatorTest {
    private val storage = mockk<RedisCommands<String, String>>()
    private val validator = ActionsTokenValidator(storage)

    @Test
    fun `matching stored token is valid`() {
        every {
            storage.get(ACTIONS_STORAGE_KEY)
        } returns TOKEN

        assertTrue(validator.isValid(CONVERSATION_ID, TOKEN))
    }

    @Test
    fun `wrong token is invalid`() {
        every {
            storage.get(ACTIONS_STORAGE_KEY)
        } returns TOKEN

        assertFalse(validator.isValid(CONVERSATION_ID, "wrong-token"))
    }

    @Test
    fun `missing stored token is invalid`() {
        every {
            storage.get(ACTIONS_STORAGE_KEY)
        } returns null

        assertFalse(validator.isValid(CONVERSATION_ID, TOKEN))
    }

    private companion object {
        val CONVERSATION_ID = QualifiedId(
            UUID.randomUUID(),
            "conversation.example.com"
        )
        val ACTIONS_STORAGE_KEY =
            "github-app:${CONVERSATION_ID.id}@${CONVERSATION_ID.domain}:actions-token"
        const val TOKEN = "actions-token"
    }
}
