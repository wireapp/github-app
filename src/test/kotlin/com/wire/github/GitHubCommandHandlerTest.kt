package com.wire.github

import com.wire.github.util.toStorageKey
import com.wire.sdk.model.QualifiedId
import io.lettuce.core.api.sync.RedisCommands
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertNull

class GitHubCommandHandlerTest {
    private val storage = mockk<RedisCommands<String, String>>()
    private val handler = GitHubCommandHandler(
        storage = storage,
        host = "https://github-app.example.com/",
        generateSecret = { GENERATED_SECRET }
    )

    @Test
    fun `help lists all available commands`() {
        val response = handler.response(GitHubCommandHandler.HELP_COMMAND, CONVERSATION_ID)

        assertContains(response.orEmpty(), GitHubCommandHandler.TOKEN_COMMAND)
        assertContains(response.orEmpty(), GitHubCommandHandler.WEBHOOK_HELP_COMMAND)
        assertContains(response.orEmpty(), GitHubCommandHandler.ACTIONS_HELP_COMMAND)
    }

    @Test
    fun `token returns existing conversation token`() {
        every { storage.get(CONVERSATION_ID.toStorageKey()) } returns EXISTING_SECRET

        val response = handler.response(GitHubCommandHandler.TOKEN_COMMAND, CONVERSATION_ID)

        assertContains(response.orEmpty(), EXISTING_SECRET)
        verify(exactly = 0) { storage.set(any(), any()) }
    }

    @Test
    fun `token creates and stores conversation token when one is missing`() {
        every { storage.get(CONVERSATION_ID.toStorageKey()) } returns null
        every { storage.set(CONVERSATION_ID.toStorageKey(), GENERATED_SECRET) } returns "OK"

        val response = handler.response(GitHubCommandHandler.TOKEN_COMMAND, CONVERSATION_ID)

        assertContains(response.orEmpty(), GENERATED_SECRET)
        verify(exactly = 1) { storage.set(CONVERSATION_ID.toStorageKey(), GENERATED_SECRET) }
    }

    @Test
    fun `webhook help includes webhook URL and token`() {
        every { storage.get(CONVERSATION_ID.toStorageKey()) } returns EXISTING_SECRET

        val response = handler.response(GitHubCommandHandler.WEBHOOK_HELP_COMMAND, CONVERSATION_ID)

        assertContains(
            response.orEmpty(),
            "https://github-app.example.com/${CONVERSATION_ID.id}/${CONVERSATION_ID.domain}"
        )
        assertContains(response.orEmpty(), EXISTING_SECRET)
    }

    @Test
    fun `Actions help includes endpoint token and reusable action`() {
        every { storage.get(CONVERSATION_ID.toStorageKey()) } returns EXISTING_SECRET

        val response = handler.response(GitHubCommandHandler.ACTIONS_HELP_COMMAND, CONVERSATION_ID)

        assertContains(
            response.orEmpty(),
            "https://github-app.example.com/actions/${CONVERSATION_ID.id}/${CONVERSATION_ID.domain}"
        )
        assertContains(response.orEmpty(), EXISTING_SECRET)
        assertContains(response.orEmpty(), "wireapp/github-app/.github/actions/notify-wire@v1")
        assertContains(response.orEmpty(), "webhook-url")
    }

    @Test
    fun `unknown command is ignored`() {
        assertNull(handler.response("/github unknown", CONVERSATION_ID))
    }

    private companion object {
        val CONVERSATION_ID = QualifiedId(UUID.randomUUID(), "conversation.example.com")
        const val EXISTING_SECRET = "existing-secret"
        const val GENERATED_SECRET = "generated-secret"
    }
}
