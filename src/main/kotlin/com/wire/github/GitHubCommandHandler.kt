package com.wire.github

import com.wire.github.util.ENV_VAR_HOST
import com.wire.github.util.ActionsTokenGenerator
import com.wire.github.util.SessionIdentifierGenerator
import com.wire.github.util.toActionsTokenStorageKey
import com.wire.github.util.toStorageKey
import com.wire.sdk.model.QualifiedId
import io.lettuce.core.api.sync.RedisCommands

internal class GitHubCommandHandler(
    private val storage: RedisCommands<String, String>,
    private val host: String = ENV_VAR_HOST,
    private val generateSecret: () -> String = SessionIdentifierGenerator::generate,
    private val generateActionsToken: () -> String = ActionsTokenGenerator::generate
) {
    fun response(
        command: String,
        conversationId: QualifiedId
    ): String? =
        when {
            command.equals(HELP_COMMAND, ignoreCase = true) -> help()
            command.equals(TOKENS_COMMAND, ignoreCase = true) -> tokens(conversationId)
            command.equals(WEBHOOK_HELP_COMMAND, ignoreCase = true) -> webhookHelp(conversationId)
            command.equals(ACTIONS_HELP_COMMAND, ignoreCase = true) -> actionsHelp(conversationId)
            else -> null
        }

    fun help(): String =
        "GitHub App supports repository webhooks and workflow-authored Actions notifications.\n\n" +
            "Available commands:\n" +
            "- `$TOKENS_COMMAND` — show or create this conversation's credentials\n" +
            "- `$WEBHOOK_HELP_COMMAND` — configure GitHub repository webhooks\n" +
            "- `$ACTIONS_HELP_COMMAND` — send custom messages from GitHub Actions"

    private fun tokens(conversationId: QualifiedId): String =
        "Conversation credentials:\n\n" +
            "GitHub webhook secret:\n`${getOrCreateSecret(conversationId)}`\n\n" +
            "GitHub Actions token:\n`${getOrCreateActionsToken(conversationId)}`\n\n" +
            "Store these values as secrets. Do not print them in workflow logs."

    fun webhookHelp(conversationId: QualifiedId): String {
        val webhookUrl = "$normalizedHost/${conversationId.id}/${conversationId.domain}"
        return "GitHub repository webhook setup:\n\n" +
            "1. Open **Settings / Webhooks / Add webhook** in the repository\n" +
            "2. Set **Payload URL**: $webhookUrl\n" +
            "3. Set **Content-Type**: application/json\n" +
            "4. Set **Secret**: ${getOrCreateSecret(conversationId)}\n" +
            "5. Select the repository events to receive\n" +
            "6. For centralized Actions notifications, select **Workflow runs** and " +
            "**Workflow jobs**"
    }

    private fun actionsHelp(conversationId: QualifiedId): String {
        val actionsUrl = "$normalizedHost/actions/${conversationId.id}/${conversationId.domain}"
        return "GitHub Actions notification setup:\n\n" +
            "Store this URL as `WIRE_WEBHOOK_URL`:\n$actionsUrl\n\n" +
            "Store this token as `WIRE_ACTIONS_TOKEN`:\n" +
            "${getOrCreateActionsToken(conversationId)}\n\n" +
            "Use the reusable action:\n\n" +
            "```yaml\n" +
            "uses: wireapp/github-app/.github/actions/notify-wire@v1\n" +
            "with:\n" +
            "  webhook-url: \${{ secrets.WIRE_WEBHOOK_URL }}\n" +
            "  token: \${{ secrets.WIRE_ACTIONS_TOKEN }}\n" +
            "  text: Message to send to Wire\n" +
            "```"
    }

    private fun getOrCreateSecret(conversationId: QualifiedId): String =
        getOrCreate(conversationId.toStorageKey(), generateSecret)

    private fun getOrCreateActionsToken(conversationId: QualifiedId): String =
        getOrCreate(conversationId.toActionsTokenStorageKey(), generateActionsToken)

    private fun getOrCreate(
        storageKey: String,
        generate: () -> String
    ): String = storage.get(storageKey) ?: generate().also { storage.set(storageKey, it) }

    private val normalizedHost: String
        get() = host.trimEnd('/')

    companion object {
        const val HELP_COMMAND = "/github help"
        const val TOKENS_COMMAND = "/github tokens"
        const val WEBHOOK_HELP_COMMAND = "/github webhook help"
        const val ACTIONS_HELP_COMMAND = "/github actions help"
    }
}
