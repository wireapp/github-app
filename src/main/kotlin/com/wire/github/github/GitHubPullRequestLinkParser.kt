package com.wire.github.github

object GitHubPullRequestLinkParser {
    fun parse(text: String): Set<GitHubRepository> =
        pullRequestUrlRegex
            .findAll(text)
            .map { match ->
                GitHubRepository(
                    owner = match.groups[OWNER_GROUP]?.value.orEmpty(),
                    name = match.groups[REPOSITORY_GROUP]?.value.orEmpty()
                )
            }.toSet()

    private val pullRequestUrlRegex =
        Regex(
            """https://github\.com/""" +
                """(?<owner>[A-Za-z0-9_.-]+)/""" +
                """(?<repository>[A-Za-z0-9_.-]+)/pull/\d+\b"""
        )

    private const val OWNER_GROUP = "owner"
    private const val REPOSITORY_GROUP = "repository"
}
