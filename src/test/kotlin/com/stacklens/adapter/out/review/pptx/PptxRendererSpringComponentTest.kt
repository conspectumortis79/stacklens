package com.stacklens.adapter.out.review.pptx

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.stereotype.Component

class PptxRendererSpringComponentTest {
    @Test
    fun `PptxSlideHtmlRenderer is registered as a Spring bean`() {
        val ctx = AnnotationConfigApplicationContext()
        ctx.scan("com.stacklens.adapter.out.review.pptx")
        ctx.refresh()
        try {
            val renderer = ctx.getBean(PptxSlideHtmlRenderer::class.java)
            assertNotNull(renderer, "PptxSlideHtmlRenderer must be available as a Spring bean")
        } finally {
            ctx.close()
        }
    }

    @Test
    fun `PptxSlideHtmlRenderer carries the @Component annotation`() {
        val annotation = PptxSlideHtmlRenderer::class.java.getAnnotation(Component::class.java)
        assertNotNull(annotation, "PptxSlideHtmlRenderer must be annotated with @Component")
    }

    @Test
    fun `PptxStructureExtractor is still a Spring bean`() {
        val ctx = AnnotationConfigApplicationContext()
        ctx.scan("com.stacklens.adapter.out.review.pptx")
        ctx.refresh()
        try {
            assertNotNull(ctx.getBean(PptxStructureExtractor::class.java))
        } finally {
            ctx.close()
        }
    }

    @Test
    fun `every class in the pptx package that is meant to be a bean IS a bean`() {
        val requiredBeans =
            setOf(
                PptxSlideHtmlRenderer::class.java,
                PptxStructureExtractor::class.java,
            )
        val ctx = AnnotationConfigApplicationContext()
        ctx.scan("com.stacklens.adapter.out.review.pptx")
        ctx.refresh()
        try {
            requiredBeans.forEach { beanClass ->
                assertTrue(
                    ctx.containsBean(beanClass.simpleName) ||
                        ctx.getBeanNamesForType(beanClass).isNotEmpty(),
                    "$beanClass must be registered as a Spring bean",
                )
            }
        } finally {
            ctx.close()
        }
    }
}
