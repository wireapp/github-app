package com.wire.github.response.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PullRequest(
    @SerialName("html_url")
    val htmlUrl: String? = null,
    @SerialName("title")
    val title: String? = null,
    @SerialName("body")
    val body: String? = null,
    @SerialName("user")
    val user: User? = null,
    @SerialName("merged")
    val merged: Boolean? = null,
    @SerialName("number")
    val number: Int? = null
)
