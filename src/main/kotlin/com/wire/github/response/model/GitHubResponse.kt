package com.wire.github.response.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GitHubResponse(
    val action: String? = null,
    @SerialName("pull_request")
    val pullRequest: PullRequest? = null,
    val comment: Comment? = null,
    val issue: Issue? = null,
    val commits: List<Commit> = emptyList(),
    val sender: User,
    val compare: String? = null,
    val review: Review? = null,
    @SerialName("check_suite")
    val checkSuite: CheckSuite? = null,
    @SerialName("workflow_run")
    val workflowRun: WorkflowRun? = null,
    val state: String? = null,
    val context: String? = null,
    val description: String? = null,
    @SerialName("target_url")
    val targetUrl: String? = null,
    val sha: String? = null,
    val repository: Repository,
    val created: Boolean? = null,
    val deleted: Boolean? = null
) {
    val statusSuccessful: Boolean
        get() = state == GITHUB_STATUS_SUCCESS

    val statusFailed: Boolean
        get() = state in GITHUB_STATUS_FAILURE_STATES
}

private const val GITHUB_STATUS_SUCCESS = "success"

private val GITHUB_STATUS_FAILURE_STATES = setOf(
    "failure",
    "error"
)
