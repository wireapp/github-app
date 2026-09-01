package com.wire.github.metrics

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry

class UsageMetrics(
    registry: MeterRegistry
) {
    private val appAddedToConversationCounter: Counter = Counter
        .builder("githubapp_added_to_conversation_total")
        .description("Number of times the app is added to a conversation")
        .register(registry)

    fun onAppAddedToConversation() {
        appAddedToConversationCounter.increment()
    }
}
