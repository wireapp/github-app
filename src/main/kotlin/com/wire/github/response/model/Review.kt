package com.wire.github.response.model

import kotlinx.serialization.Serializable

@Serializable
data class Review(
    val body: String,
    val user: User,
    val state: String
)
