package com.stacklens.domain.failure

class StackLensFailureException(
    val failure: StackLensFailure,
    cause: Throwable? = null,
) : IllegalStateException(failure.wireMessage, cause)
