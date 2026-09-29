package com.wire.github.response.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WorkflowRun(
    val name: String? = null,
    val conclusion: String? = null,
    val event: String? = null,
    val actor: User? = null,
    @SerialName("head_branch")
    val headBranch: String? = null,
    @SerialName("html_url")
    val htmlUrl: String? = null,
    @SerialName("run_number")
    val runNumber: Int? = null
) {
    val emoji: String
        get() = conclusionPresentation(conclusion).emoji

    val conclusionText: String
        get() = conclusionPresentation(conclusion).text

    val displayName: String
        get() = name?.takeIf { it.isNotBlank() } ?: "Workflow run"
}
