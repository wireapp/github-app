package com.wire.github.util

import com.wire.github.RedisRepository
import com.wire.sdk.model.QualifiedId
import io.mockk.every
import io.mockk.mockk
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ActionsTokenValidatorTest {
    private val redisRepository = mockk<RedisRepository>()
    private val validator = ActionsTokenValidator(redisRepository)

    @Test
    fun `matching stored token is valid`() {
        every {
            redisRepository.getActionSecret(CONVERSATION_ID)
        } returns TOKEN

        assertTrue(validator.isValid(CONVERSATION_ID, TOKEN))
    }

    @Test
    fun `wrong token is invalid`() {
        every {
            redisRepository.getActionSecret(CONVERSATION_ID)
        } returns TOKEN

        assertFalse(validator.isValid(CONVERSATION_ID, "wrong-token"))
    }

    @Test
    fun `missing stored token is invalid`() {
        every {
            redisRepository.getActionSecret(CONVERSATION_ID)
        } returns null

        assertFalse(validator.isValid(CONVERSATION_ID, TOKEN))
    }

    private companion object {
        val CONVERSATION_ID = QualifiedId(
            UUID.randomUUID(),
            "conversation.example.com"
        )
        const val TOKEN = "actions-token"
    }
}
