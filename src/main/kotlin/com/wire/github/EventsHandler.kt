package com.wire.github

import com.wire.github.util.ENV_VAR_HOST
import com.wire.github.util.SessionIdentifierGenerator
import com.wire.github.util.toStorageKey
import com.wire.sdk.WireEventsHandlerSuspending
import com.wire.sdk.model.ConversationData
import com.wire.sdk.model.ConversationMember
import com.wire.sdk.model.QualifiedId
import com.wire.sdk.model.WireMessage
import io.lettuce.core.api.StatefulRedisConnection
import org.koin.core.context.GlobalContext

class EventsHandler : WireEventsHandlerSuspending() {
    private val redisConnection = GlobalContext.get().get<StatefulRedisConnection<String, String>>()
    private val storage = redisConnection.sync()

    override suspend fun onMessage(wireMessage: WireMessage.Text) {
        if (wireMessage.text.equals("/help", ignoreCase = true)) {
            val message = formatHelp(
                conversationId = wireMessage.conversationId,
                secret = storage.get(wireMessage.conversationId.toStorageKey())
            )

            manager.sendMessage(
                message = WireMessage.Text.create(
                    conversationId = wireMessage.conversationId,
                    text = message
                )
            )
        }
    }

    override suspend fun onConversationJoin(
        conversation: ConversationData,
        members: List<ConversationMember>
    ) {
        val message = formatHelp(conversationId = conversation.id)

        manager.sendMessage(
            message = WireMessage.Text.create(
                conversationId = conversation.id,
                text = message
            )
        )
    }

    private fun formatHelp(
        conversationId: QualifiedId,
        secret: String? = null
    ): String {
        val generatedSecret = secret ?: run {
            val generated = SessionIdentifierGenerator.generate()
            storage.set(conversationId.toStorageKey(), generated)
            generated
        }

        val url = String.format(
            HOST_URL_PATTERN,
            ENV_VAR_HOST,
            conversationId.id,
            conversationId.domain
        )

        val message = String.format(
            "Hi, I'm GitHub App. Here is how to set me up:\n\n" +
                "1. Go to the repository that you would like to connect to\n" +
                "2. Go to **Settings / Webhooks / Add webhook**\n" +
                "3. Add **Payload URL**: %s\n" +
                "4. Set **Content-Type**: application/json\n" +
                "5. Set **Secret**: %s",
            url,
            generatedSecret
        )

        return message
    }

    private companion object {
        const val HOST_URL_PATTERN = "%s/%s/%s"
    }
}
