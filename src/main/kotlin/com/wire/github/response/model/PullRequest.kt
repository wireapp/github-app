package com.wire.github.response.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PullRequest(
    @SerialName("html_url")
    val htmlUrl: String,
    val title: String,
    val body: String? = null,
    val user: User,
    val number: Int,
    val additions: Int = 0,
    val deletions: Int = 0,
    val draft: Boolean = false,
    val merged: Boolean? = false
)
