package com.stacklens.application.service.review

import com.stacklens.testsupport.TestJsonCodec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PptxQuestionExtractorTest {
    private val extractor = PptxQuestionExtractor(TestJsonCodec.create())

    @Test
    fun `parse returns empty plan for blank input`() {
        val plan = extractor.parse(null)
        assertTrue(plan.isEmpty())

        val plan2 = extractor.parse("")
        assertTrue(plan2.isEmpty())

        val plan3 = extractor.parse("   ")
        assertTrue(plan3.isEmpty())
    }

    @Test
    fun `parse returns empty plan for non-JSON input and logs warn`() {
        val plan = extractor.parse("not-json-at-all")
        assertTrue(plan.isEmpty())
    }

    @Test
    fun `parse extracts a single slide with two questions`() {
        val raw =
            """
            {"slides": [
              {"slideIndex": 1, "questions": [
                {"questionId": "s1-shape0", "text": "Wie war die Velocity?"},
                {"questionId": "s1-shape3", "text": "Drei Haupt-Faktoren?"}
              ]}
            ]}
            """.trimIndent()
        val plan = extractor.parse(raw)
        assertEquals(1, plan.slides.size)
        assertEquals(
            2,
            plan.slides
                .first()
                .questions.size,
        )
        assertEquals(
            "s1-shape0",
            plan.slides
                .first()
                .questions[0]
                .questionId,
        )
        assertEquals(
            "Wie war die Velocity?",
            plan.slides
                .first()
                .questions[0]
                .text,
        )
        assertEquals(
            1,
            plan.slides
                .first()
                .questions[0]
                .slideIndex,
        )
        assertEquals(
            0,
            plan.slides
                .first()
                .questions[0]
                .shapeIndex,
        )
    }

    @Test
    fun `parse tolerates missing slides key`() {
        val raw = """{"foo": "bar"}"""
        val plan = extractor.parse(raw)
        assertTrue(plan.isEmpty())
    }

    @Test
    fun `parse drops entries with missing questionId`() {
        val raw =
            """
            {"slides": [{"slideIndex": 1, "questions": [
              {"text": "No questionId here"},
              {"questionId": "s1-shape0", "text": "Valid one"}
            ]}]}
            """.trimIndent()
        val plan = extractor.parse(raw)
        assertEquals(
            1,
            plan.slides
                .first()
                .questions.size,
        )
        assertEquals(
            "s1-shape0",
            plan.slides
                .first()
                .questions
                .first()
                .questionId,
        )
    }

    @Test
    fun `parse drops entries with blank text`() {
        val raw =
            """
            {"slides": [{"slideIndex": 1, "questions": [
              {"questionId": "s1-shape0", "text": "  "},
              {"questionId": "s1-shape1", "text": "Real"}
            ]}]}
            """.trimIndent()
        val plan = extractor.parse(raw)
        assertEquals(
            1,
            plan.slides
                .first()
                .questions.size,
        )
        assertEquals(
            "s1-shape1",
            plan.slides
                .first()
                .questions
                .first()
                .questionId,
        )
    }

    @Test
    fun `parse drops entries with malformed questionId`() {
        val raw =
            """
            {"slides": [{"slideIndex": 1, "questions": [
              {"questionId": "this-is-not-valid", "text": "x"},
              {"questionId": "s1-shape0", "text": "Real"}
            ]}]}
            """.trimIndent()
        val plan = extractor.parse(raw)
        assertEquals(
            1,
            plan.slides
                .first()
                .questions.size,
        )
        assertEquals(
            "s1-shape0",
            plan.slides
                .first()
                .questions
                .first()
                .questionId,
        )
    }

    @Test
    fun `parse deduplicates by questionId (later wins)`() {
        val raw =
            """
            {"slides": [
              {"slideIndex": 1, "questions": [
                {"questionId": "s1-shape0", "text": "First"},
                {"questionId": "s1-shape0", "text": "Second"}
              ]}
            ]}
            """.trimIndent()
        val plan = extractor.parse(raw)
        assertEquals(
            1,
            plan.slides
                .first()
                .questions.size,
        )
        assertEquals(
            "Second",
            plan.slides
                .first()
                .questions
                .first()
                .text,
        )
    }

    @Test
    fun `parse handles empty slides array`() {
        val raw = """{"slides": []}"""
        val plan = extractor.parse(raw)
        assertTrue(plan.isEmpty())
    }

    @Test
    fun `parse groups by slideIndex and sorts slides by index`() {
        val raw =
            """
            {"slides": [
              {"slideIndex": 3, "questions": [{"questionId": "s3-shape0", "text": "q3"}]},
              {"slideIndex": 1, "questions": [{"questionId": "s1-shape0", "text": "q1"}]},
              {"slideIndex": 2, "questions": [{"questionId": "s2-shape0", "text": "q2a"}, {"questionId": "s2-shape1", "text": "q2b"}]}
            ]}
            """.trimIndent()
        val plan = extractor.parse(raw)
        assertEquals(listOf(1, 2, 3), plan.slides.map { it.slideIndex })
        assertEquals(2, plan.slides[1].questions.size)
        assertEquals("s2-shape0", plan.slides[1].questions[0].questionId)
        assertEquals("s2-shape1", plan.slides[1].questions[1].questionId)
    }

    @Test
    fun `parse drops slide entries with negative slideIndex`() {
        val raw =
            """
            {"slides": [
              {"slideIndex": -1, "questions": [{"questionId": "s-1-shape0", "text": "x"}]}
            ]}
            """.trimIndent()
        val plan = extractor.parse(raw)
        assertTrue(plan.isEmpty())
    }

    @Test
    fun `parse questionIds helper returns flattened set`() {
        val raw =
            """
            {"slides": [
              {"slideIndex": 1, "questions": [{"questionId": "s1-shape0", "text": "a"}]},
              {"slideIndex": 2, "questions": [{"questionId": "s2-shape0", "text": "b"}, {"questionId": "s2-shape1", "text": "c"}]}
            ]}
            """.trimIndent()
        val plan = extractor.parse(raw)
        assertEquals(setOf("s1-shape0", "s2-shape0", "s2-shape1"), plan.questionIds())
        assertFalse(plan.isEmpty())
    }
}
