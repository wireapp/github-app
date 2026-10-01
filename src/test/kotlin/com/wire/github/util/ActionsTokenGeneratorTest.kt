package com.wire.github.util

import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class ActionsTokenGeneratorTest {
    @Test
    fun `generates a random URL-safe token from 32 bytes`() {
        val firstToken = ActionsTokenGenerator.generate()
        val secondToken = ActionsTokenGenerator.generate()

        assertEquals(43, firstToken.length)
        assertEquals(32, Base64.getUrlDecoder().decode(firstToken).size)
        assertNotEquals(firstToken, secondToken)
    }
}
