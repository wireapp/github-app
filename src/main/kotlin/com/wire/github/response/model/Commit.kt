package com.wire.github.response.model

import kotlinx.serialization.Serializable

@Serializable
data class Commit(
    val message: String
)
