package com.stacklens.adapter.out.security

data class ConfigEnvelope(
    val version: Int = 1,
    val encrypted: Boolean = true,
    val algorithm: String = "AES-256-GCM",
    val kdf: String = "PBKDF2WithHmacSHA256",
    val iterations: Int = 310_000,
    val salt: String = "",
    val iv: String = "",
    val data: String = "",
)
