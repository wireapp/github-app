package com.wire.github.github

data class GitHubRepository(
    val owner: String,
    val name: String
) {
    val fullName: String = "$owner/$name"

    companion object {
        fun fromFullName(fullName: String): GitHubRepository? {
            val parts = fullName.split("/")
            return parts
                .takeIf { it.size == REPOSITORY_FULL_NAME_PARTS }
                ?.let { GitHubRepository(owner = it[0], name = it[1]) }
        }

        private const val REPOSITORY_FULL_NAME_PARTS = 2
    }
}
