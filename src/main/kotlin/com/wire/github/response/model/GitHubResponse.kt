package com.wire.github.response.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GitHubResponse(
    @SerialName("action")
    val action: String? = null,
    @SerialName("pull_request")
    val pullRequest: PullRequest? = null,
    @SerialName("comment")
    val comment: Comment? = null,
    @SerialName("issue")
    val issue: Issue? = null,
    @SerialName("commits")
    val commits: List<Commit>? = null,
    @SerialName("sender")
    val sender: User? = null,
    @SerialName("compare")
    val compare: String? = null,
    @SerialName("review")
    val review: Review? = null,
    @SerialName("repository")
    val repository: Repository? = null,
    @SerialName("created")
    val created: Boolean? = null,
    @SerialName("deleted")
    val deleted: Boolean? = null
)
