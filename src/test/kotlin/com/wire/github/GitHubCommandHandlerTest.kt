package com.wire.github

import com.wire.github.metrics.UsageMetrics
import com.wire.sdk.model.QualifiedId
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertNull

class GitHubCommandHandlerTest {
    private val redisRepository = mockk<RedisRepository>()
    private val usageMetrics = mockk<UsageMetrics>(relaxed = true)
    private val handler = GitHubCommandHandler(
        redisRepository = redisRepository,
        usageMetrics = usageMetrics,
        host = "https://github-app.example.com/",
        generateSecret = { GENERATED_SECRET },
        generateActionsToken = { GENERATED_ACTIONS_TOKEN }
    )

    @Test
    fun `help lists all available commands`() {
        val response = handler.response("/GITHUB HELP", CONVERSATION_ID)

        assertContains(response.orEmpty(), GitHubCommandHandler.WEBHOOK_HELP_COMMAND)
        assertContains(response.orEmpty(), GitHubCommandHandler.ACTIONS_HELP_COMMAND)
        verify(exactly = 1) { usageMetrics.onHelpCommand() }
    }

    @Test
    fun `webhook help includes webhook URL and token`() {
        every { redisRepository.getWebhookSecret(CONVERSATION_ID) } returns EXISTING_SECRET

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
            redisRepository.getActionSecret(CONVERSATION_ID)
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
        every { redisRepository.getWebhookSecret(CONVERSATION_ID) } returns EXISTING_SECRET

        handler.webhookHelp(CONVERSATION_ID)

        verify(exactly = 0) { usageMetrics.onHelpCommand() }
    }

    @Test
    fun `webhook help stores and returns a generated secret when none exists`() {
        every { redisRepository.getWebhookSecret(CONVERSATION_ID) } returns null
        every { redisRepository.setWebhookSecret(CONVERSATION_ID, GENERATED_SECRET) } returns Unit

        val response = handler.webhookHelp(CONVERSATION_ID)

        assertContains(response, GENERATED_SECRET)
        verify(exactly = 1) {
            redisRepository.setWebhookSecret(CONVERSATION_ID, GENERATED_SECRET)
        }
    }

    @Test
    fun `Actions help stores and returns a generated secret when none exists`() {
        every { redisRepository.getActionSecret(CONVERSATION_ID) } returns null
        every {
            redisRepository.setActionSecret(
                CONVERSATION_ID,
                GENERATED_ACTIONS_TOKEN
            )
        } returns Unit

        val response = handler.response(GitHubCommandHandler.ACTIONS_HELP_COMMAND, CONVERSATION_ID)

        assertContains(response.orEmpty(), GENERATED_ACTIONS_TOKEN)
        verify(exactly = 1) {
            redisRepository.setActionSecret(CONVERSATION_ID, GENERATED_ACTIONS_TOKEN)
        }
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
