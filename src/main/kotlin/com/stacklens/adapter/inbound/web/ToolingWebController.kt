package com.stacklens.adapter.inbound.web

import com.stacklens.application.port.inbound.tooling.ToolingReadUseCase
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam

@Controller
class ToolingWebController(
    private val toolingService: ToolingReadUseCase,
) {
    @GetMapping("/tooling")
    fun tooling(
        @RequestParam(required = false) focus: Long?,
        model: Model,
    ): String {
        model.addAttribute("tools", toolingService.getAllTools())
        model.addAttribute("confluenceConfigs", toolingService.listActiveConfluenceConfigs())
        model.addAttribute("focusMode", focus != null)
        return "tooling"
    }

    @GetMapping("/tool/{id}")
    fun toolView(
        @PathVariable id: Long,
        model: Model,
    ): String {
        val tool = toolingService.getTool(id) ?: return "redirect:/tooling"
        model.addAttribute("tool", tool)
        return "tool-view"
    }
}
