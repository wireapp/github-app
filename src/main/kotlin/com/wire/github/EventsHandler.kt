package com.wire.github

import com.wire.github.github.GitHubPullRequestLinkParser
import com.wire.github.github.GitHubRepository
import com.wire.github.github.GitHubWebhookManager
import com.wire.sdk.WireEventsHandlerSuspending
import com.wire.sdk.model.Conversation
import com.wire.sdk.model.ConversationMember
import com.wire.sdk.model.QualifiedId
import com.wire.sdk.model.WireMessage
import org.koin.core.context.GlobalContext
import org.slf4j.LoggerFactory

class EventsHandler : WireEventsHandlerSuspending() {
    private val logger = LoggerFactory.getLogger(this::class.java)
    private val gitHubWebhookManager by lazy { GlobalContext.get().get<GitHubWebhookManager>() }

    override suspend fun onTextMessageReceived(wireMessage: WireMessage.Text) {
        if (wireMessage.text.equals(HELP_COMMAND, ignoreCase = true)) {
            logger.info(
                "Event received. Event: TextMessageReceived (HELP command), " +
                    "conversationId: ${wireMessage.conversationId}, " +
                    "senderId: ${wireMessage.sender}"
            )
            sendMessage(
                conversationId = wireMessage.conversationId,
                text = USAGE_TEXT
            )
            logger.info(
                "Event is processed successfully. Event: TextMessageReceived (HELP command), " +
                    "conversationId: ${wireMessage.conversationId}"
            )
            return
        }
        val repositories = GitHubPullRequestLinkParser.parse(wireMessage.text)
        if (repositories.isEmpty()) {
            return
        }

        registerRepositories(
            repositories = repositories,
            conversationId = wireMessage.conversationId
        )?.let { message ->
            sendMessage(
                conversationId = wireMessage.conversationId,
                text = message
            )
        }
    }

    override suspend fun onAppAddedToConversation(
        conversation: Conversation,
        members: List<ConversationMember>
    ) {
        logger.info(
            "Event received. Event: AppAddedToConversation, " +
                "conversationId: ${conversation.id}"
        )
        val message = buildString {
            appendLine(WELCOME_TEXT)
            appendLine(USAGE_TEXT)
            appendLine()
            append("Use the `$HELP_COMMAND` command to see the usage again.")
        }

        sendMessage(
            conversationId = conversation.id,
            text = message
        )
        logger.info(
            "Event is processed successfully. Event: AppAddedToConversation, " +
                "conversationId: ${conversation.id}"
        )
    }

    private fun registerRepositories(
        repositories: Set<GitHubRepository>,
        conversationId: QualifiedId
    ): String? {
        val results = repositories.mapNotNull { repository ->
            runCatching {
                gitHubWebhookManager.ensureWebhookForConversation(
                    repository = repository,
                    conversationId = conversationId
                )
            }.fold(
                onSuccess = { result ->
                    if (result.newlySubscribed) {
                        "Receiving pull request notifications for `${repository.fullName}`."
                    } else {
                        null
                    }
                },
                onFailure = { exception ->
                    logger.warn(
                        "Failed to provision GitHub webhook for ${repository.fullName}",
                        exception
                    )
                    "Could not set up pull request notifications for `${repository.fullName}`."
                }
            )
        }

        return results.takeIf { it.isNotEmpty() }?.joinToString("\n")
    }

    private fun sendMessage(
        conversationId: QualifiedId,
        text: String
    ) {
        manager.sendMessage(
            message = WireMessage.Text.create(
                conversationId = conversationId,
                text = text
            )
        )
    }

    private companion object {
        const val WELCOME_TEXT =
            "👋 Hi, I'm GitHub App. Thanks for adding me to the conversation.\n" +
                "You can use me to receive GitHub notifications in Wire.\n" +
                "I'm here to help make everyday work a little easier."

        const val USAGE_TEXT =
            "Post a GitHub pull request link in this conversation and I will set up " +
                "pull request notifications for that repository."

        const val HELP_COMMAND = "/github help"
    }
}
