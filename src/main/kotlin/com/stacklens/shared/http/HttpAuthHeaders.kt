package com.stacklens.shared.http

import java.util.Base64

fun bearerOrBasicAuthHeader(
    username: String,
    apiToken: String,
): String =
    if (username.isBlank()) {
        "Bearer $apiToken"
    } else {
        "Basic " + Base64.getEncoder().encodeToString("$username:$apiToken".toByteArray())
    }
