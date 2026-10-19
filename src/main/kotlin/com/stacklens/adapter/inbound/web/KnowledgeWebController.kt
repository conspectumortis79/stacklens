package com.stacklens.adapter.inbound.web

import com.stacklens.application.port.inbound.knowledge.KnowledgeBaseUseCase
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping

@Controller
class KnowledgeWebController(
    private val knowledgeBaseService: KnowledgeBaseUseCase,
) {
    @GetMapping("/knowledge")
    fun knowledgePage(model: Model): String {
        model.addAttribute("knowledgeBases", knowledgeBaseService.listBases())
        return "knowledge"
    }
}
