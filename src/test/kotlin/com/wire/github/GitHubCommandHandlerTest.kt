package com.wire.github

import com.wire.github.metrics.UsageMetrics
import com.wire.github.util.toActionsTokenStorageKey
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
    private val usageMetrics = mockk<UsageMetrics>(relaxed = true)
    private val handler = GitHubCommandHandler(
        storage = storage,
        usageMetrics = usageMetrics,
        host = "https://github-app.example.com/",
        generateSecret = { GENERATED_SECRET },
        generateActionsToken = { GENERATED_ACTIONS_TOKEN }
    )

    @Test
    fun `help lists all available commands`() {
        val response = handler.response("/GITHUB HELP", CONVERSATION_ID)

        assertContains(response.orEmpty(), GitHubCommandHandler.TOKENS_COMMAND)
        assertContains(response.orEmpty(), GitHubCommandHandler.WEBHOOK_HELP_COMMAND)
        assertContains(response.orEmpty(), GitHubCommandHandler.ACTIONS_HELP_COMMAND)
        verify(exactly = 1) { usageMetrics.onHelpCommand() }
    }

    @Test
    fun `tokens returns existing webhook secret and Actions token`() {
        every { storage.get(CONVERSATION_ID.toStorageKey()) } returns EXISTING_SECRET
        every {
            storage.get(CONVERSATION_ID.toActionsTokenStorageKey())
        } returns EXISTING_ACTIONS_TOKEN

        val response = handler.response(GitHubCommandHandler.TOKENS_COMMAND, CONVERSATION_ID)

        assertContains(response.orEmpty(), EXISTING_SECRET)
        assertContains(response.orEmpty(), EXISTING_ACTIONS_TOKEN)
        verify(exactly = 0) { storage.set(any(), any()) }
        verify(exactly = 0) { usageMetrics.onHelpCommand() }
    }

    @Test
    fun `tokens creates and stores both credentials when they are missing`() {
        every { storage.get(CONVERSATION_ID.toStorageKey()) } returns null
        every { storage.set(CONVERSATION_ID.toStorageKey(), GENERATED_SECRET) } returns "OK"
        every { storage.get(CONVERSATION_ID.toActionsTokenStorageKey()) } returns null
        every {
            storage.set(CONVERSATION_ID.toActionsTokenStorageKey(), GENERATED_ACTIONS_TOKEN)
        } returns "OK"

        val response = handler.response(GitHubCommandHandler.TOKENS_COMMAND, CONVERSATION_ID)

        assertContains(response.orEmpty(), GENERATED_SECRET)
        assertContains(response.orEmpty(), GENERATED_ACTIONS_TOKEN)
        verify(exactly = 1) { storage.set(CONVERSATION_ID.toStorageKey(), GENERATED_SECRET) }
        verify(exactly = 1) {
            storage.set(CONVERSATION_ID.toActionsTokenStorageKey(), GENERATED_ACTIONS_TOKEN)
        }
        verify(exactly = 0) { usageMetrics.onHelpCommand() }
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
        verify(exactly = 1) { usageMetrics.onHelpCommand() }
    }

    @Test
    fun `Actions help includes endpoint token and reusable action`() {
        every {
            storage.get(CONVERSATION_ID.toActionsTokenStorageKey())
        } returns EXISTING_ACTIONS_TOKEN

        val response = handler.response(GitHubCommandHandler.ACTIONS_HELP_COMMAND, CONVERSATION_ID)

        assertContains(
            response.orEmpty(),
            "https://github-app.example.com/actions/${CONVERSATION_ID.id}/${CONVERSATION_ID.domain}"
        )
        assertContains(response.orEmpty(), EXISTING_ACTIONS_TOKEN)
        assertContains(response.orEmpty(), "wireapp/github-app/.github/actions/notify-wire@v1")
        assertContains(response.orEmpty(), "webhook-url")
        assertContains(response.orEmpty(), "token")
        verify(exactly = 1) { usageMetrics.onHelpCommand() }
    }

    @Test
    fun `webhook setup generated for app onboarding is not counted as a help command`() {
        every { storage.get(CONVERSATION_ID.toStorageKey()) } returns EXISTING_SECRET

        handler.webhookHelp(CONVERSATION_ID)

        verify(exactly = 0) { usageMetrics.onHelpCommand() }
    }

    @Test
    fun `old singular token command is ignored`() {
        assertNull(handler.response("/github token", CONVERSATION_ID))
        verify(exactly = 0) { usageMetrics.onHelpCommand() }
    }

    @Test
    fun `unknown command is ignored`() {
        assertNull(handler.response("/github unknown", CONVERSATION_ID))
        verify(exactly = 0) { usageMetrics.onHelpCommand() }
    }

    private companion object {
        val CONVERSATION_ID = QualifiedId(UUID.randomUUID(), "conversation.example.com")
        const val EXISTING_SECRET = "existing-secret"
        const val GENERATED_SECRET = "generated-secret"
        const val EXISTING_ACTIONS_TOKEN = "existing-actions-token"
        const val GENERATED_ACTIONS_TOKEN = "generated-actions-token"
    }
}
