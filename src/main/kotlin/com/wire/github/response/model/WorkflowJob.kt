package com.wire.github.response.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WorkflowJob(
    val name: String,
    val conclusion: String?,
    @SerialName("html_url")
    val htmlUrl: String,
    @SerialName("workflow_name")
    val workflowName: String? = null,
    @SerialName("head_branch")
    val headBranch: String? = null
) {
    val emoji: String
        get() = when (conclusion) {
            "success" -> "✅"
            "failure" -> "❌"
            "cancelled" -> "🚫"
            "skipped" -> "⏭️"
            else -> "ℹ️"
        }
}
