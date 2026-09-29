package com.wire.github.response.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WorkflowRun(
    val name: String,
    val conclusion: String?,
    val event: String,
    val actor: User,
    @SerialName("head_branch")
    val headBranch: String,
    @SerialName("html_url")
    val htmlUrl: String,
    @SerialName("run_number")
    val runNumber: Int
) {
    val emoji: String
        get() = when (conclusion) {
            "success" -> "✅"
            "failure" -> "❌"
            "cancelled" -> "🚫"
            "timed_out" -> "⏱️"
            "action_required" -> "⚠️"
            "skipped" -> "⏭️"
            "stale" -> "💤"
            else -> "ℹ️"
        }
}
