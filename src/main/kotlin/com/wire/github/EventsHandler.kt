package com.wire.github

import com.wire.github.metrics.UsageMetrics
import com.wire.sdk.WireEventsHandlerSuspending
import com.wire.sdk.model.Conversation
import com.wire.sdk.model.ConversationMember
import com.wire.sdk.model.WireMessage
import io.lettuce.core.api.StatefulRedisConnection
import org.koin.core.context.GlobalContext
import org.slf4j.LoggerFactory

class EventsHandler : WireEventsHandlerSuspending() {
    private val logger = LoggerFactory.getLogger(this::class.java)
    private val redisConnection = GlobalContext.get().get<StatefulRedisConnection<String, String>>()
    private val usageMetrics = GlobalContext.get().get<UsageMetrics>()
    private val commandHandler = GitHubCommandHandler(
        storage = redisConnection.sync(),
        usageMetrics = usageMetrics
    )

    override suspend fun onTextMessageReceived(wireMessage: WireMessage.Text) {
        val response = commandHandler.response(
            command = wireMessage.text,
            conversationId = wireMessage.conversationId
        ) ?: return

        logger.info(
            "Event received. Event: TextMessageReceived (GitHub command), " +
                "conversationId: ${wireMessage.conversationId}, " +
                "senderId: ${wireMessage.sender}"
        )
        manager.sendMessage(
            message = WireMessage.Text.create(
                conversationId = wireMessage.conversationId,
                text = response
            )
        )
        logger.info(
            "Event is processed successfully. Event: TextMessageReceived (GitHub command), " +
                "conversationId: ${wireMessage.conversationId}"
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
        usageMetrics.onAppAddedToConversation()
        val webhookSetup = commandHandler.webhookHelp(conversationId = conversation.id)
        val message = buildString {
            appendLine(WELCOME_TEXT)
            appendLine(webhookSetup)
            appendLine()
            append("Use `${GitHubCommandHandler.HELP_COMMAND}` to see all available commands.")
        }

        manager.sendMessage(
            message = WireMessage.Text.create(
                conversationId = conversation.id,
                text = message
            )
        )
        logger.info(
            "Event is processed successfully. Event: AppAddedToConversation, " +
                "conversationId: ${conversation.id}"
        )
    }

    private companion object {
        const val WELCOME_TEXT =
            "👋 Hi, I'm GitHub App. Thanks for adding me to the conversation.\n" +
                "You can use me to receive GitHub notifications in Wire.\n" +
                "I'm here to help make everyday work a little easier."
    }
}
