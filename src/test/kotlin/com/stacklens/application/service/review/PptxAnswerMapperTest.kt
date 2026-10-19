package com.stacklens.application.service.review

import com.stacklens.domain.model.PptxQuestion
import com.stacklens.domain.model.PptxQuestionPlan
import com.stacklens.domain.model.PptxSlideQuestions
import com.stacklens.testsupport.TestJsonCodec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PptxAnswerMapperTest {
    private val mapper = PptxAnswerMapper(TestJsonCodec.create())

    private fun plan(vararg allowedIds: String): PptxQuestionPlan {
        val bySlide =
            allowedIds.groupBy { id ->
                id
                    .split("-")
                    .first()
                    .removePrefix("s")
                    .toInt()
            }
        val slides =
            bySlide.map { (slideIndex, ids) ->
                PptxSlideQuestions(
                    slideIndex = slideIndex,
                    questions =
                        ids.map { id ->
                            val shapeIndex = id.substringAfter("-shape").toInt()
                            PptxQuestion(id, slideIndex, shapeIndex, "Q?")
                        },
                )
            }
        return PptxQuestionPlan(slides)
    }

    @Test
    fun `parse returns EMPTY for null input`() {
        val result = mapper.parse(null, plan("s1-shape0"))
        assertTrue(result.isEmpty())
    }

    @Test
    fun `parse returns EMPTY for blank input`() {
        assertTrue(mapper.parse("", plan("s1-shape0")).isEmpty())
        assertTrue(mapper.parse("   ", plan("s1-shape0")).isEmpty())
    }

    @Test
    fun `parse accepts markdown list format with dash`() {
        val raw =
            """
            - s1-shape0: Velocity war 42.
            - s1-shape3: Drei Faktoren: Code-Reviews, Tests, klares Backlog.
            """.trimIndent()
        val result = mapper.parse(raw, plan("s1-shape0", "s1-shape3"))
        assertEquals("Velocity war 42.", result.answers["s1-shape0"])
        assertEquals("Drei Faktoren: Code-Reviews, Tests, klares Backlog.", result.answers["s1-shape3"])
    }

    @Test
    fun `parse accepts markdown list format with star`() {
        val raw =
            """
            * s1-shape0: Velocity war 42.
            * s1-shape3: Drei Faktoren.
            """.trimIndent()
        val result = mapper.parse(raw, plan("s1-shape0", "s1-shape3"))
        assertEquals(2, result.answers.size)
    }

    @Test
    fun `parse accepts markdown list with bold questionId`() {
        val raw =
            """
            - **s1-shape0**: Velocity war 42.
            - *s1-shape3*: Drei Faktoren.
            """.trimIndent()
        val result = mapper.parse(raw, plan("s1-shape0", "s1-shape3"))
        assertEquals("Velocity war 42.", result.answers["s1-shape0"])
        assertEquals("Drei Faktoren.", result.answers["s1-shape3"])
    }

    @Test
    fun `parse accepts markdown without bullet marker`() {
        val raw =
            """
            s1-shape0: Velocity war 42.
            s1-shape3: Drei Faktoren.
            """.trimIndent()
        val result = mapper.parse(raw, plan("s1-shape0", "s1-shape3"))
        assertEquals(2, result.answers.size)
    }

    @Test
    fun `parse accepts JSON object format`() {
        val raw = """{"s1-shape0": "Velocity war 42.", "s1-shape3": "Drei Faktoren."}"""
        val result = mapper.parse(raw, plan("s1-shape0", "s1-shape3"))
        assertEquals("Velocity war 42.", result.answers["s1-shape0"])
        assertEquals("Drei Faktoren.", result.answers["s1-shape3"])
    }

    @Test
    fun `parse JSON with whitespace is tolerated`() {
        val raw = """  { "s1-shape0" : "ans",   "s1-shape3" : "ans2" }  """
        val result = mapper.parse(raw, plan("s1-shape0", "s1-shape3"))
        assertEquals(2, result.answers.size)
    }

    @Test
    fun `parse drops answers whose questionId is not in the plan`() {
        val raw =
            """
            - s1-shape0: Velocity war 42.
            - s9-shape9: Should be dropped.
            """.trimIndent()
        val result = mapper.parse(raw, plan("s1-shape0"))
        assertEquals(1, result.answers.size)
        assertTrue(result.answers["s1-shape0"] != null)
        assertTrue(result.answers["s9-shape9"] == null)
    }

    @Test
    fun `parse with an empty plan discards all answers`() {
        val raw =
            """
            - s1-shape0: Velocity war 42.
            - s1-shape3: Drei Faktoren.
            """.trimIndent()
        val result = mapper.parse(raw, PptxQuestionPlan(emptyList()))
        assertTrue(result.isEmpty())
    }

    @Test
    fun `parse ignores lines that do not match the pattern`() {
        val raw =
            """
            Hallo, das ist eine Einleitung.
            - s1-shape0: Velocity war 42.
            Diese Zeile hat keinen Marker.
            - this-is-not-valid: wird ignoriert
            """.trimIndent()
        val result = mapper.parse(raw, plan("s1-shape0"))
        assertEquals(1, result.answers.size)
        assertEquals("Velocity war 42.", result.answers["s1-shape0"])
    }

    @Test
    fun `parse ignores answers with empty text after colon`() {
        val raw =
            """
            - s1-shape0:
            - s1-shape3:  Echte Antwort
            """.trimIndent()
        val result = mapper.parse(raw, plan("s1-shape0", "s1-shape3"))
        assertEquals(1, result.answers.size)
        assertEquals("Echte Antwort", result.answers["s1-shape3"])
    }

    @Test
    fun `parse handles malformed JSON by falling back to markdown parse`() {
        val raw = """{"not": "valid",}"""
        val result = mapper.parse(raw, plan("s1-shape0"))
        assertTrue(result.isEmpty())
    }

    @Test
    fun `parse preserves multiline answer if single line`() {
        val raw = "- s1-shape0: Velocity: 42 Story-Points, 8 % über Schnitt."
        val result = mapper.parse(raw, plan("s1-shape0"))
        assertEquals("Velocity: 42 Story-Points, 8 % über Schnitt.", result.answers["s1-shape0"])
    }

    @Test
    fun `parse handles JSON with extra unknown keys`() {
        val raw = """{"s1-shape0": "answer", "extra": "ignored"}"""
        val result = mapper.parse(raw, plan("s1-shape0"))
        assertEquals(1, result.answers.size)
        assertEquals("answer", result.answers["s1-shape0"])
    }
}
