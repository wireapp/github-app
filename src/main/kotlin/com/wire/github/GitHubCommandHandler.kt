package com.wire.github

import com.wire.github.util.ENV_VAR_HOST
import com.wire.github.util.SessionIdentifierGenerator
import com.wire.github.util.toStorageKey
import com.wire.sdk.model.QualifiedId
import io.lettuce.core.api.sync.RedisCommands

internal class GitHubCommandHandler(
    private val storage: RedisCommands<String, String>,
    private val host: String = ENV_VAR_HOST,
    private val generateSecret: () -> String = SessionIdentifierGenerator::generate
) {
    fun response(
        command: String,
        conversationId: QualifiedId
    ): String? =
        when {
            command.equals(HELP_COMMAND, ignoreCase = true) -> help()
            command.equals(TOKEN_COMMAND, ignoreCase = true) -> token(conversationId)
            command.equals(WEBHOOK_HELP_COMMAND, ignoreCase = true) -> webhookHelp(conversationId)
            command.equals(ACTIONS_HELP_COMMAND, ignoreCase = true) -> actionsHelp(conversationId)
            else -> null
        }

    fun help(): String =
        "GitHub App supports repository webhooks and workflow-authored Actions notifications.\n\n" +
            "Available commands:\n" +
            "- `$TOKEN_COMMAND` — show or create this conversation's token\n" +
            "- `$WEBHOOK_HELP_COMMAND` — configure GitHub repository webhooks\n" +
            "- `$ACTIONS_HELP_COMMAND` — send custom messages from GitHub Actions"

    private fun token(conversationId: QualifiedId): String =
        "Conversation token:\n\n`${getOrCreateSecret(conversationId)}`\n\n" +
            "Store this value as a secret. Do not print it in workflow logs."

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
            "Store this token as `WIRE_WEBHOOK_SECRET`:\n${getOrCreateSecret(conversationId)}\n\n" +
            "Use the reusable action:\n\n" +
            "```yaml\n" +
            "uses: wireapp/github-app/.github/actions/notify-wire@v1\n" +
            "with:\n" +
            "  webhook-url: \${{ secrets.WIRE_WEBHOOK_URL }}\n" +
            "  secret: \${{ secrets.WIRE_WEBHOOK_SECRET }}\n" +
            "  text: Message to send to Wire\n" +
            "```"
    }

    private fun getOrCreateSecret(conversationId: QualifiedId): String {
        val storageKey = conversationId.toStorageKey()
        return storage.get(storageKey) ?: generateSecret().also { storage.set(storageKey, it) }
    }

    private val normalizedHost: String
        get() = host.trimEnd('/')

    companion object {
        const val HELP_COMMAND = "/github help"
        const val TOKEN_COMMAND = "/github token"
        const val WEBHOOK_HELP_COMMAND = "/github webhook help"
        const val ACTIONS_HELP_COMMAND = "/github actions help"
    }
}
