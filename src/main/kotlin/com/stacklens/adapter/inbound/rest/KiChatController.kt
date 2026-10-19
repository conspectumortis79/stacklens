package com.stacklens.adapter.inbound.rest

import com.stacklens.application.port.inbound.chat.ChatRequest
import com.stacklens.application.port.inbound.chat.KiChatEvent
import com.stacklens.application.port.inbound.chat.KiChatUsageUseCase
import com.stacklens.application.port.inbound.chat.KiChatUseCase
import com.stacklens.application.port.inbound.platform.I18nUseCase
import com.stacklens.domain.util.quietly
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

@RestController
@RequestMapping("/api/ki-chat")
class KiChatController internal constructor(
    private val kiChat: KiChatUseCase,
    private val i18n: I18nUseCase,
    private val chatUsage: KiChatUsageUseCase,
    private val objectMapper: com.fasterxml.jackson.databind.ObjectMapper? = null,
) {
    @PostMapping(produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun chat(
        @RequestBody req: ChatRequest,
    ): SseEmitter {
        if (req.userMessage.isBlank()) {
            return SseEmitter(0L).also { it.completeWithError(IllegalArgumentException("userMessage required")) }
        }
        if (req.attachments.size > MAX_ATTACHMENTS_PER_REQUEST) {
            return SseEmitter(0L).also {
                it.completeWithError(IllegalArgumentException("too_many_attachments"))
            }
        }
        for (att in req.attachments) {
            if (att.base64Data.length > MAX_ATTACHMENT_BASE64_LENGTH) {
                return SseEmitter(0L).also {
                    it.completeWithError(IllegalArgumentException("attachment_too_large"))
                }
            }
            if (!SUPPORTED_IMAGE_MIME_TYPES.contains(att.mimeType.lowercase())) {
                return SseEmitter(0L).also {
                    it.completeWithError(IllegalArgumentException("unsupported_attachment_mime_type"))
                }
            }
        }

        val emitter = SseEmitter(300_000L)
        emitter.onCompletion { kiChat.cancel(req.tabId) }
        emitter.onTimeout { kiChat.cancel(req.tabId) }
        emitter.onError { kiChat.cancel(req.tabId) }

        try {
            runChatThread(emitter, req)
        } catch (e: Exception) {
            runCatching {
                emitter.send(SseEmitter.event().name("error").data(i18n.translateError(e)))
                emitter.complete()
            }.onFailure { safeCompleteWithError(emitter, e) }
        }

        return emitter
    }

    init {
        warmUpKiChatClasses()
    }

    private fun warmUpKiChatClasses() {
        quietly {
            Class.forName(
                KiChatEvent.Chunk::class.java.name,
                true,
                Thread.currentThread().contextClassLoader,
            )
            Class.forName(
                KiChatEvent.Sources::class.java.name,
                true,
                Thread.currentThread().contextClassLoader,
            )
            Class.forName(
                KiChatEvent.Done::class.java.name,
                true,
                Thread.currentThread().contextClassLoader,
            )
        }
    }

    internal fun runChatThread(
        emitter: SseEmitter,
        req: ChatRequest,
    ) {
        Thread(
            object : Runnable {
                override fun run() {
                    try {
                        streamChat(emitter, req)
                    } catch (t: Throwable) {
                        quietly { emitter.complete() }
                        throw t
                    }
                }
            },
        ).also {
            it.isDaemon = true
            it.name = "ki-chat-${req.tabId}"
        }.start()
    }

    internal fun streamChat(
        emitter: SseEmitter,
        req: ChatRequest,
    ) {
        try {
            kiChat.chatEvents(req) { event ->
                try {
                    when (event) {
                        is com.stacklens.application.port.inbound.chat.KiChatEvent.Chunk -> {
                            emitter.send(SseEmitter.event().name("chunk").data(jsonEncode(event.text)))
                        }
                        is com.stacklens.application.port.inbound.chat.KiChatEvent.Sources -> {
                            emitter.send(
                                SseEmitter
                                    .event()
                                    .name("sources")
                                    .data(jsonEncodeObject(event.sources)),
                            )
                        }
                        else -> Unit
                    }
                } catch (e: Exception) {
                    kiChat.cancel(req.tabId)
                    throw InterruptedException("client disconnected")
                }
            }
            if (!Thread.interrupted()) {
                try {
                    emitter.send(SseEmitter.event().name("done").data(""))
                } catch (_: Exception) {
                }
            }
            emitter.complete()
        } catch (_: InterruptedException) {
            quietly { emitter.complete() }
        } catch (e: Exception) {
            try {
                emitter.send(SseEmitter.event().name("error").data(e.message ?: i18n.t("common.be_unknown_error")))
                emitter.complete()
            } catch (_: Exception) {
                safeCompleteWithError(emitter, e)
            }
        }
    }

    @DeleteMapping("/usage/{chatId}")
    fun clearUsage(
        @PathVariable chatId: Long,
    ): ResponseEntity<Map<String, Any>> {
        if (chatId <= 0) {
            return ResponseEntity.badRequest().body(mapOf("ok" to false))
        }
        chatUsage.clearUsage(chatId)
        return ResponseEntity.ok(mapOf("ok" to true))
    }

    @PostMapping("/cancel/{tabId}")
    fun cancel(
        @PathVariable tabId: String,
    ): ResponseEntity<Map<String, Any>> {
        val wasRunning = kiChat.cancel(tabId)
        return ResponseEntity.ok(mapOf("ok" to true, "wasRunning" to wasRunning))
    }

    internal fun safeCompleteWithError(
        emitter: SseEmitter,
        e: Exception,
    ) {
        try {
            emitter.completeWithError(e)
        } catch (_: Exception) {
        }
    }

    private fun jsonEncode(s: String): String =
        buildString {
            append('"')
            for (c in s) {
                when (c) {
                    '"' -> append("\\\"")
                    '\\' -> append("\\\\")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else ->
                        if (c.code < 0x20) {
                            append("\\u")
                            append(c.code.toString(16).padStart(4, '0'))
                        } else {
                            append(c)
                        }
                }
            }
            append('"')
        }

    private fun jsonEncodeObject(value: Any): String {
        val mapper = objectMapper
        if (mapper != null) {
            return try {
                mapper.writeValueAsString(value)
            } catch (_: Exception) {
                "[]"
            }
        }
        return "[]"
    }

    companion object {
        const val MAX_ATTACHMENTS_PER_REQUEST = 8
        const val MAX_ATTACHMENT_BASE64_LENGTH = 8 * 1024 * 1024
        val SUPPORTED_IMAGE_MIME_TYPES: Set<String> =
            setOf(
                "image/png",
                "image/jpeg",
                "image/jpg",
                "image/tiff",
                "image/tif",
            )
    }
}
