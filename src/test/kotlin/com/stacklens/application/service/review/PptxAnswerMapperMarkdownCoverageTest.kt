package com.stacklens.application.service.review

import com.stacklens.domain.model.PptxAnswerSet
import com.stacklens.domain.model.PptxQuestion
import com.stacklens.domain.model.PptxQuestionPlan
import com.stacklens.domain.model.PptxSlideQuestions
import com.stacklens.testsupport.TestJsonCodec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PptxAnswerMapperMarkdownCoverageTest {
    private val mapper = PptxAnswerMapper(TestJsonCodec.create())
    private val emptyPlan = PptxQuestionPlan(emptyList())

    private fun planOf(vararg ids: String): PptxQuestionPlan =
        PptxQuestionPlan(
            ids
                .groupBy { it.substringAfter("s").substringBefore("-").toInt() }
                .map { (slideIndex, slideIds) ->
                    PptxSlideQuestions(
                        slideIndex = slideIndex,
                        questions = slideIds.map { PptxQuestion(it, slideIndex, it.substringAfter("-shape").toInt(), "q") },
                    )
                },
        )

    @Test
    fun `markdown answers are collected line by line`() {
        val raw =
            """
            - s1-shape1: Use a warm accent
            s2-shape3 - Second answer

            * s3-shape2: Third answer
            prose without an id
            """.trimIndent()

        val set = mapper.parse(raw, planOf("s1-shape1", "s2-shape3", "s3-shape2"))

        assertEquals(3, set.answers.size)
        assertEquals("Use a warm accent", set.answers["s1-shape1"])
        assertEquals("Second answer", set.answers["s2-shape3"])
        assertEquals("Third answer", set.answers["s3-shape2"])
    }

    @Test
    fun `a bullet whose answer is only whitespace is dropped`() {
        assertTrue(mapper.parse("- s1-shape1:    ", emptyPlan).isEmpty())
    }

    @Test
    fun `an empty answer payload yields the empty set`() {
        assertEquals(PptxAnswerSet.EMPTY, mapper.parse("   ", emptyPlan))
        assertEquals(PptxAnswerSet.EMPTY, mapper.parse(null, emptyPlan))
    }

    @Test
    fun `json payloads that are not objects fall back to the markdown parser`() {
        assertTrue(mapper.parse("""["nope"]""", planOf("s1-shape0")).isEmpty())
    }
}
