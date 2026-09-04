package com.wire.github.metrics

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry

class UsageMetrics(
    private val registry: MeterRegistry
) {
    private val helpCommandCounter: Counter = Counter
        .builder("githubapp_help_commands_total")
        .description("Number of Help commands received")
        .register(registry)

    private val appAddedToConversationCounter: Counter = Counter
        .builder("githubapp_added_to_conversation_total")
        .description("Number of times the app is added to a conversation")
        .register(registry)

    private fun webhookEventsReceivedCounter(event: String): Counter =
        Counter
            .builder("githubapp_webhook_events_received_total")
            .description("Number of GitHub webhook deliveries accepted for processing")
            .tag(TAG_EVENT, event)
            .register(registry)

    private fun notificationsSentCounter(event: String): Counter =
        Counter
            .builder("githubapp_notifications_sent_total")
            .description("Number of messages sent to a conversation for a webhook delivery")
            .tag(TAG_EVENT, event)
            .register(registry)

    private fun unsupportedEventsCounter(
        event: String,
        action: String?
    ): Counter =
        Counter
            .builder("githubapp_unsupported_events_total")
            .description("Number of webhook deliveries with no matching message template")
            .tag(TAG_EVENT, event)
            .tag(TAG_ACTION, action ?: NO_ACTION)
            .register(registry)

    /**
     * A help command was received in a conversation.
     * Signals how often users come back for the setup instructions.
     */
    fun onHelpCommand() {
        helpCommandCounter.increment()
    }

    /**
     * The app was added to a conversation.
     * First step of onboarding, before any webhook is configured.
     */
    fun onAppAddedToConversation() {
        appAddedToConversationCounter.increment()
    }

    /**
     * A webhook delivery passed signature validation and entered processing.
     * Top of the delivery funnel.
     */
    fun onWebhookEventReceived(event: String) {
        webhookEventsReceivedCounter(event).increment()
    }

    /**
     * A webhook delivery was rendered and sent to the conversation.
     * Bottom of the delivery funnel.
     */
    fun onNotificationSent(event: String) {
        notificationsSentCounter(event).increment()
    }

    /**
     * A webhook delivery had no matching message template, so nothing was sent.
     * Ranks the event/action pairs worth adding templates for.
     */
    fun onUnsupportedEvent(
        event: String,
        action: String?
    ) {
        unsupportedEventsCounter(event, action).increment()
    }

    private companion object {
        const val TAG_EVENT = "event"
        const val TAG_ACTION = "action"
        const val NO_ACTION = "none"
    }
}
