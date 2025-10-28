package com.wire.github.util

import com.wire.sdk.model.QualifiedId

fun QualifiedId.toStorageKey() = "${this.id}@${this.domain}"

fun String.toStorageKey(domain: String) = "$this@$domain"
