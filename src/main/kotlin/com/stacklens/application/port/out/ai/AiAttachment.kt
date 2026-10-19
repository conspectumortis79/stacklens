package com.stacklens.application.port.out.ai

data class AiAttachment(
    val mimeType: String,
    val base64Data: String,
) {
    fun toDataUri(): String = "data:$mimeType;base64,$base64Data"
}
