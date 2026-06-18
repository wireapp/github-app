package com.wire.github.response.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WorkflowRun(
    val name: String? = null,
    @SerialName("html_url")
    val htmlUrl: String? = null,
    @SerialName("head_branch")
    val headBranch: String? = null,
    @SerialName("head_sha")
    val headSha: String? = null,
    val status: String? = null,
    val conclusion: String? = null
) {
    val successful: Boolean
        get() = conclusion == WORKFLOW_RUN_CONCLUSION_SUCCESS

    val failed: Boolean
        get() = conclusion in WORKFLOW_RUN_FAILURE_CONCLUSIONS
}

private const val WORKFLOW_RUN_CONCLUSION_SUCCESS = "success"

private val WORKFLOW_RUN_FAILURE_CONCLUSIONS = setOf(
    "failure",
    "error",
    "timed_out",
    "action_required",
    "startup_failure"
)
