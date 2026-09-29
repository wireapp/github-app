package com.wire.github.response.model

internal data class Conclusion(
    val emoji: String,
    val text: String
)

internal fun conclusionPresentation(conclusion: String?): Conclusion =
    when (conclusion) {
        "success" -> Conclusion(emoji = "✅", text = "succeeded")
        "failure" -> Conclusion(emoji = "❌", text = "failed")
        "cancelled" -> Conclusion(emoji = "🚫", text = "cancelled")
        "timed_out" -> Conclusion(emoji = "⏱️", text = "timed out")
        "action_required" -> Conclusion(emoji = "⚠️", text = "requires action")
        "skipped" -> Conclusion(emoji = "⏭️", text = "skipped")
        "stale" -> Conclusion(emoji = "💤", text = "stale")
        else -> Conclusion(emoji = "ℹ️", text = "completed")
    }
