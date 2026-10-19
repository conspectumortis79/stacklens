package com.stacklens.adapter.inbound.rest.knowledge

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import java.util.NoSuchElementException

@ControllerAdvice(basePackages = ["com.stacklens.adapter.inbound.rest.knowledge"])
class KnowledgeExceptionAdvice {
    @ExceptionHandler(NoSuchElementException::class)
    fun handleNotFound(e: NoSuchElementException): ResponseEntity<Map<String, Any>> = ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf("error" to (e.message ?: "Not found")))

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleBadRequest(e: IllegalArgumentException): ResponseEntity<Map<String, Any>> = ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf("error" to (e.message ?: "Invalid request")))

    @ExceptionHandler(IllegalStateException::class)
    fun handleUnavailable(e: IllegalStateException): ResponseEntity<Map<String, Any>> = ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf("error" to (e.message ?: "Not available")))
}
