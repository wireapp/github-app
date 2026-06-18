package com.wire.github.github

object GitHubPullRequestLinkParser {
    fun parse(text: String): Set<GitHubPullRequestReference> =
        pullRequestUrlRegex
            .findAll(text)
            .mapNotNull { match ->
                val number = match.groups[NUMBER_GROUP]?.value?.toIntOrNull()
                    ?: return@mapNotNull null

                GitHubPullRequestReference(
                    repository = GitHubRepository(
                        owner = match.groups[OWNER_GROUP]?.value.orEmpty(),
                        name = match.groups[REPOSITORY_GROUP]?.value.orEmpty()
                    ),
                    number = number
                )
            }.toSet()

    private val pullRequestUrlRegex =
        Regex(
            """https://github\.com/""" +
                """(?<owner>[A-Za-z0-9_.-]+)/""" +
                """(?<repository>[A-Za-z0-9_.-]+)/pull/(?<number>\d+)\b"""
        )

    private const val OWNER_GROUP = "owner"
    private const val REPOSITORY_GROUP = "repository"
    private const val NUMBER_GROUP = "number"
}
