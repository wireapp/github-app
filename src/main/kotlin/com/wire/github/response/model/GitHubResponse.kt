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
    val repository: Repository,
    @SerialName("workflow_run")
    val workflowRun: WorkflowRun? = null,
    @SerialName("workflow_job")
    val workflowJob: WorkflowJob? = null,
    val created: Boolean? = null,
    val deleted: Boolean? = null
)
