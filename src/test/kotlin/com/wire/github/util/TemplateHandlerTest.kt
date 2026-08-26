package com.wire.github.util

import com.wire.github.response.model.Commit
import com.wire.github.response.model.GitHubResponse
import com.wire.github.response.model.PullRequest
import com.wire.github.response.model.Repository
import com.wire.github.response.model.Review
import com.wire.github.response.model.User
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertNull

class TemplateHandlerTest {
    private val templateHandler = TemplateHandler()

    @Test
    fun `does not render a submitted pull request review without a body`() {
        val message = templateHandler.handleEvent(
            event = "pull_request_review",
            response = reviewResponse(body = null)
        )

        assertNull(message)
    }

    @Test
    fun `does not render a submitted pull request review with a blank body`() {
        val message = templateHandler.handleEvent(
            event = "pull_request_review",
            response = reviewResponse(body = "")
        )

        assertNull(message)
    }

    @Test
    fun `renders a submitted pull request review with a body`() {
        val message = templateHandler.handleEvent(
            event = "pull_request_review",
            response = reviewResponse(body = "Looks good")
        )

        assertContains(message.orEmpty(), "Looks good")
    }

    @Test
    fun `does not render a push with no commits`() {
        val message = templateHandler.handleEvent(
            event = "push",
            response = pushResponse(commits = emptyList())
        )

        assertNull(message)
    }

    @Test
    fun `renders a push with commits`() {
        val message = templateHandler.handleEvent(
            event = "push",
            response = pushResponse(commits = listOf(Commit(message = "Add feature")))
        )

        assertContains(message.orEmpty(), "Add feature")
    }

    private fun reviewResponse(body: String?) =
        GitHubResponse(
            action = "submitted",
            pullRequest = PullRequest(
                htmlUrl = "https://github.com/wire/example/pull/1",
                title = "Example pull request",
                user = user,
                number = 1
            ),
            review = Review(
                body = body,
                user = user,
                state = "approved"
            ),
            sender = user,
            repository = Repository(
                fullName = "wire/example",
                name = "example"
            )
        )

    private fun pushResponse(commits: List<Commit>) =
        GitHubResponse(
            commits = commits,
            sender = user,
            compare = "https://github.com/wire/example/compare/main",
            repository = Repository(
                fullName = "wire/example",
                name = "example"
            )
        )

    private companion object {
        val user = User(
            avatarUrl = "https://github.com/wire.png",
            login = "wire"
        )
    }
}
