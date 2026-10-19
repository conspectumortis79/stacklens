package com.stacklens.application.port.model

enum class ConnectionFailureKind {
    AUTHENTICATION,
    AUTHORIZATION,
    NOT_FOUND,
    CONNECTION,
    OTHER,
}
