package com.wire.github.response.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Comment(
    val body: String,
    val user: User,
    @SerialName("html_url")
    val htmlUrl: String,
    val id: String,
    val line: Int? = null
)
