package com.wire.github

import com.wire.github.github.GitHubAppClient
import com.wire.github.github.GitHubPullRequestLinkParser
import com.wire.github.github.GitHubPullRequestReference
import com.wire.github.github.GitHubRepository
import com.wire.github.github.GitHubWebhookManager
import com.wire.github.util.TemplateHandler
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
    private val gitHubAppClient by lazy { GlobalContext.get().get<GitHubAppClient>() }
    private val templateHandler by lazy { GlobalContext.get().get<TemplateHandler>() }

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
        val pullRequests = GitHubPullRequestLinkParser.parse(wireMessage.text)
        if (pullRequests.isEmpty()) {
            return
        }

        val conversationId = wireMessage.conversationId
        val statusMessage = registerRepositories(
            repositories = pullRequests.map { it.repository }.toSet(),
            conversationId = conversationId
        )
        if (statusMessage.isNotBlank()) {
            sendMessage(
                conversationId = conversationId,
                text = statusMessage
            )
        }

        announcePullRequests(
            pullRequests = pullRequests,
            conversationId = conversationId
        )
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
    ): String {
        val results = repositories.map { repository ->
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

        return results.filterNotNull().joinToString(separator = "\n")
    }

    private fun announcePullRequests(
        pullRequests: Set<GitHubPullRequestReference>,
        conversationId: QualifiedId
    ) {
        pullRequests.forEach { reference ->
            val pullRequest = runCatching {
                gitHubAppClient.fetchPullRequest(
                    repository = reference.repository,
                    number = reference.number
                )
            }.onFailure { exception ->
                logger.warn(
                    "Failed to fetch pull request " +
                        "${reference.repository.fullName}#${reference.number}",
                    exception
                )
            }.getOrNull() ?: return@forEach

            templateHandler.renderPullRequest(pullRequest)?.let { message ->
                sendMessage(
                    conversationId = conversationId,
                    text = message
                )
            }
        }
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
