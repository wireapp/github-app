package com.wire.github.util

import com.wire.sdk.model.QualifiedId

private const val STORAGE_KEY_PREFIX = "github-app:"
private const val ACTIONS_TOKEN_SUFFIX = ":actions-token"

fun QualifiedId.toStorageKey() = "$STORAGE_KEY_PREFIX${this.id}@${this.domain}"

fun String.toStorageKey(domain: String) = "$STORAGE_KEY_PREFIX$this@$domain"

fun QualifiedId.toActionsTokenStorageKey() = "${toStorageKey()}$ACTIONS_TOKEN_SUFFIX"

fun String.toActionsTokenStorageKey(domain: String) = "${toStorageKey(domain)}$ACTIONS_TOKEN_SUFFIX"
