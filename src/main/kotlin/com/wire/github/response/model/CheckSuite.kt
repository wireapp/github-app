package com.wire.github.response.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CheckSuite(
    @SerialName("html_url")
    val htmlUrl: String? = null,
    @SerialName("head_branch")
    val headBranch: String? = null,
    @SerialName("head_sha")
    val headSha: String? = null,
    val status: String? = null,
    val conclusion: String? = null
) {
    val successful: Boolean
        get() = conclusion == CHECK_SUITE_CONCLUSION_SUCCESS

    val failed: Boolean
        get() = conclusion in CHECK_SUITE_FAILURE_CONCLUSIONS
}

private const val CHECK_SUITE_CONCLUSION_SUCCESS = "success"

private val CHECK_SUITE_FAILURE_CONCLUSIONS = setOf(
    "failure",
    "error",
    "timed_out",
    "action_required",
    "startup_failure"
)
