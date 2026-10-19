package com.stacklens.application.service.review

import com.stacklens.testsupport.TestJsonCodec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PptxQuestionExtractorCoverageTest {
    private val extractor = PptxQuestionExtractor(TestJsonCodec.create())

    private fun planWith(vararg questionIds: String): String {
        val questions =
            questionIds
                .map { """{"questionId":"$it","text":"Answer prompt for $it"}""" }
                .joinToString(",")
        return """{"slides":[{"slideIndex":1,"questions":[$questions]}]}"""
    }

    @Test
    fun `parses a well formed question id into slide and shape indexes`() {
        val plan = extractor.parse(planWith("s1-shape2"))

        assertEquals(1, plan.slides.size)
        assertEquals(1, plan.slides.first().slideIndex)
        assertEquals(
            "s1-shape2",
            plan.slides
                .first()
                .questions
                .first()
                .questionId,
        )
        assertEquals(
            2,
            plan.slides
                .first()
                .questions
                .first()
                .shapeIndex,
        )
    }

    @Test
    fun `drops questions whose slide index overflows`() {
        assertTrue(extractor.parse(planWith("s99999999999999999999-shape1")).slides.isEmpty())
    }

    @Test
    fun `drops questions whose shape index overflows`() {
        assertTrue(extractor.parse(planWith("s1-shape99999999999999999999")).slides.isEmpty())
    }

    @Test
    fun `drops questions whose slide index disagrees with the enclosing slide`() {
        val plan = extractor.parse(planWith("s4-shape1"))

        assertTrue(plan.slides.isEmpty())
    }

    @Test
    fun `returns the empty plan for missing or malformed payloads`() {
        assertTrue(extractor.parse(null).slides.isEmpty())
        assertTrue(extractor.parse("  ").slides.isEmpty())
        assertTrue(extractor.parse("""{"slides":"not-an-array"}""").slides.isEmpty())
        assertTrue(extractor.parse("""{"other":1}""").slides.isEmpty())
        assertTrue(extractor.parse("[]").slides.isEmpty())
    }

    @Test
    fun `skips slides and questions with unusable entries`() {
        val payload =
            """
            {"slides":[
              {"slideIndex":-1,"questions":[]},
              {"other":1},
              {"slideIndex":2,"questions":[
                 {"questionId":"s2-shape1"},
                 {"text":"no id"},
                 {"questionId":"bogus","text":"bad id"},
                 {"questionId":"s2-shape2","text":"  "},
                 {"questionId":"  ","text":"blank id"}
              ]}
            ]}
            """.trimIndent()

        assertTrue(extractor.parse(payload).slides.isEmpty())
    }

    @Test
    fun `skips non object questions inside the array`() {
        val payload = """{"slides":[{"slideIndex":3,"questions":["plain",{"questionId":"s3-shape1","text":"ok"}]}]}"""

        val plan = extractor.parse(payload)

        assertEquals(
            "s3-shape1",
            plan.slides
                .first()
                .questions
                .single()
                .questionId,
        )
    }

    @Test
    fun `sorts questions of one slide by shape index`() {
        val plan = extractor.parse(planWith("s1-shape3", "s1-shape1", "s1-shape2"))

        assertEquals(
            listOf(1, 2, 3),
            plan.slides
                .first()
                .questions
                .map { it.shapeIndex },
        )
    }
}
