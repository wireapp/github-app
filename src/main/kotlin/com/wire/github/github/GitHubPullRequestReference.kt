package com.wire.github.github

data class GitHubPullRequestReference(
    val repository: GitHubRepository,
    val number: Int
)
