package com.wire.github.response.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WorkflowJob(
    val name: String? = null,
    val conclusion: String? = null,
    @SerialName("html_url")
    val htmlUrl: String? = null,
    @SerialName("workflow_name")
    val workflowName: String? = null,
    @SerialName("head_branch")
    val headBranch: String? = null
) {
    val emoji: String
        get() = conclusionPresentation(conclusion).emoji

    val conclusionText: String
        get() = conclusionPresentation(conclusion).text

    val displayName: String
        get() = name?.takeIf { it.isNotBlank() } ?: "Workflow job"
}
