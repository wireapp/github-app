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
    val merged: Boolean? = false,
    val number: Int
)
