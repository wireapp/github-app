package com.wire.github.response.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Comment(
    @SerialName("body")
    val body: String? = null,
    @SerialName("user")
    val user: User? = null,
    @SerialName("html_url")
    val htmlUrl: String? = null,
    @SerialName("id")
    val id: String? = null,
    @SerialName("line")
    val line: Integer? = null
)
