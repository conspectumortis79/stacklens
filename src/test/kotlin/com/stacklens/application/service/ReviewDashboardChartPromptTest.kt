package com.stacklens.application.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.stacklens.adapter.out.ai.PromptLoader
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.core.io.DefaultResourceLoader

class ReviewDashboardChartPromptTest {
    private val loader =
        PromptLoader(
            DefaultResourceLoader(),
            ObjectMapper().registerModule(KotlinModule.Builder().build()),
            dataDir =
                java.io.File
                    .createTempFile("unused", ".tmp")
                    .parent,
        )

    @Test
    fun `de prompt instructs explicit canvas width and height attributes`() {
        val prompt = loader.get("de", "review.dashboardInstruction")
        assertNotNull(prompt)
        assertTrue(
            prompt.contains("width=\"280\"") || prompt.contains("width=\""),
            "DE prompt must require an explicit `width` attribute on every <canvas>.\nGot:\n$prompt",
        )
        assertTrue(
            prompt.contains("height=\"280\"") || prompt.contains("height=\""),
            "DE prompt must require an explicit `height` attribute on every <canvas>.\nGot:\n$prompt",
        )
    }

    @Test
    fun `en prompt instructs explicit canvas width and height attributes`() {
        val prompt = loader.get("en", "review.dashboardInstruction")
        assertNotNull(prompt)
        assertTrue(
            prompt.contains("width=\"280\"") || prompt.contains("width=\""),
            "EN prompt must require an explicit `width` attribute on every <canvas>.\nGot:\n$prompt",
        )
        assertTrue(
            prompt.contains("height=\"280\"") || prompt.contains("height=\""),
            "EN prompt must require an explicit `height` attribute on every <canvas>.\nGot:\n$prompt",
        )
    }

    @Test
    fun `de prompt requires canvas to be in DOM before Chart init`() {
        val prompt = loader.get("de", "review.dashboardInstruction")
        assertTrue(
            prompt.contains("DOMContentLoaded") || prompt.contains("defer"),
            "DE prompt must defer chart initialisation until the canvas is in the DOM.\nGot:\n$prompt",
        )
    }

    @Test
    fun `en prompt requires canvas to be in DOM before Chart init`() {
        val prompt = loader.get("en", "review.dashboardInstruction")
        assertTrue(
            prompt.contains("DOMContentLoaded") || prompt.contains("defer"),
            "EN prompt must defer chart initialisation until the canvas is in the DOM.\nGot:\n$prompt",
        )
    }

    @Test
    fun `de prompt requires try-catch around new Chart() and a fallback text`() {
        val prompt = loader.get("de", "review.dashboardInstruction")
        assertTrue(
            prompt.contains("try") && prompt.contains("catch"),
            "DE prompt must require try/catch around new Chart().\nGot:\n$prompt",
        )
        assertTrue(
            prompt.contains("Fallback") || prompt.contains("nicht verfügbar") || prompt.contains("fallback"),
            "DE prompt must require a fallback text when chart init fails.\nGot:\n$prompt",
        )
    }

    @Test
    fun `en prompt requires try-catch around new Chart() and a fallback text`() {
        val prompt = loader.get("en", "review.dashboardInstruction")
        assertTrue(
            prompt.contains("try") && prompt.contains("catch"),
            "EN prompt must require try/catch around new Chart().\nGot:\n$prompt",
        )
        assertTrue(
            prompt.contains("fallback") || prompt.contains("unavailable"),
            "EN prompt must require a fallback text when chart init fails.\nGot:\n$prompt",
        )
    }

    @Test
    fun `de prompt requires a fixed-height chart wrapper`() {
        val prompt = loader.get("de", "review.dashboardInstruction")
        assertTrue(
            prompt.contains("chart-frame") && prompt.contains("height:320px") && prompt.contains("min-width:0"),
            "DE prompt must bound each responsive chart through a fixed-height parent.\nGot:\n$prompt",
        )
    }

    @Test
    fun `en prompt requires a fixed-height chart wrapper`() {
        val prompt = loader.get("en", "review.dashboardInstruction")
        assertTrue(
            prompt.contains("chart-frame") && prompt.contains("height:320px") && prompt.contains("min-width:0"),
            "EN prompt must bound each responsive chart through a fixed-height parent.\nGot:\n$prompt",
        )
    }

    @Test
    fun `de prompt names the reproduced ResizeObserver canvas growth failure`() {
        val prompt = loader.get("de", "review.dashboardInstruction")
        assertTrue(
            prompt.contains("Canvas exceeds max size") && prompt.contains("ResizeObserver") && prompt.contains("maintainAspectRatio:false"),
            "DE prompt must describe the reproduced responsive-canvas feedback loop.\nGot:\n$prompt",
        )
    }

    @Test
    fun `en prompt names the reproduced ResizeObserver canvas growth failure`() {
        val prompt = loader.get("en", "review.dashboardInstruction")
        assertTrue(
            prompt.contains("Canvas exceeds max size") && prompt.contains("ResizeObserver") && prompt.contains("maintainAspectRatio:false"),
            "EN prompt must describe the reproduced responsive-canvas feedback loop.\nGot:\n$prompt",
        )
    }
}
