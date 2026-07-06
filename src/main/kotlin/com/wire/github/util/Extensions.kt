package com.wire.github.util

import com.wire.sdk.model.QualifiedId

private const val STORAGE_KEY_PREFIX = "github-app:"

fun QualifiedId.toStorageKey() = "$STORAGE_KEY_PREFIX${this.id}@${this.domain}"

fun String.toStorageKey(domain: String) = "$STORAGE_KEY_PREFIX$this@$domain"
