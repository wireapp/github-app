package com.wire.github.response.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Review(
    @SerialName("body")
    val body: String? = null,
    @SerialName("user")
    val user: User? = null,
    @SerialName("state")
    val state: String? = null
)
