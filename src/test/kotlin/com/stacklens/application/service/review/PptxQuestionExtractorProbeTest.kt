package com.stacklens.application.service.review

import com.stacklens.domain.model.PptxQuestion
import com.stacklens.domain.model.PptxQuestionPlan
import com.stacklens.domain.model.PptxSlideQuestions
import com.stacklens.testsupport.TestJsonCodec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PptxQuestionExtractorProbeTest {
    private val extractor = PptxQuestionExtractor(TestJsonCodec.create())

    @Test
    fun `a blank payload yields an empty plan`() {
        assertTrue(extractor.parse(null).isEmpty())
        assertTrue(extractor.parse("   ").isEmpty())
    }

    @Test
    fun `a payload without an object root yields an empty plan`() {
        assertTrue(extractor.parse("[1,2,3]").isEmpty())
    }

    @Test
    fun `a payload without a slides array yields an empty plan`() {
        assertTrue(extractor.parse("""{"other":1}""").isEmpty())
        assertTrue(extractor.parse("""{"slides":"not-an-array"}""").isEmpty())
    }

    @Test
    fun `slides and questions of the wrong shape are skipped`() {
        val json =
            """
            {"slides":[
              "not-an-object",
              {"questions":[]},
              {"slideIndex":"not-a-number","questions":[]},
              {"slideIndex":-1,"questions":[]},
              {"slideIndex":1,"questions":"not-an-array"},
              {"slideIndex":1,"questions":["not-an-object",
                "also-not-an-object",
                {"questionId":"s1-shape0"},
                {"text":"no id"},
                {"questionId":"  ","text":"blank id"},
                {"questionId":"s1-shape0","text":"   "},
                {"questionId":"wrong-format","text":"bad id"},
                {"questionId":"s2-shape0","text":"dropped although the counter disagrees"},
                {"questionId":"s1-shape2","text":"Real question"}
              ]}
            ]}
            """.trimIndent()

        val plan = extractor.parse(json)

        assertEquals(
            listOf("s1-shape2"),
            plan.all().map { it.questionId },
        )
        assertEquals(listOf(1), plan.slides.map { it.slideIndex })
    }

    @Test
    fun `question ids are ordered by slide and shape index`() {
        val json =
            """
            {"slides":[
              {"slideIndex":2,"questions":[{"questionId":"s2-shape1","text":"later"},{"questionId":"s2-shape0","text":"first"}]},
              {"slideIndex":1,"questions":[{"questionId":"s1-shape0","text":"top"}]}
            ]}
            """.trimIndent()

        val plan = extractor.parse(json)

        assertEquals(listOf(1, 2), plan.slides.map { it.slideIndex })
        assertEquals(
            listOf("s2-shape0", "s2-shape1"),
            plan.slides
                .last()
                .questions
                .map { it.questionId },
        )
    }

    @Test
    fun `duplicate question ids keep the last text`() {
        val json =
            """
            {"slides":[
              {"slideIndex":1,"questions":[
                {"questionId":"s1-shape0","text":"first"},
                {"questionId":"s1-shape0","text":"second"}
              ]}
            ]}
            """.trimIndent()

        assertEquals(
            "second",
            extractor
                .parse(json)
                .all()
                .single()
                .text,
        )
    }

    @Test
    fun `an answer id of a slide that is out of range is parsed anyway`() {
        val json =
            """
            {"slides":[
              {"slideIndex":1,"questions":[{"questionId":"s1-shape42","text":"deep"}]}
            ]}
            """.trimIndent()

        assertEquals(
            42,
            extractor
                .parse(json)
                .all()
                .single()
                .shapeIndex,
        )
    }

    private fun planOf(vararg ids: String): PptxQuestionPlan =
        PptxQuestionPlan(
            ids.map { id ->
                PptxSlideQuestions(
                    slideIndex = id.substringAfter("s").substringBefore("-").toInt(),
                    questions = listOf(PptxQuestion(id, 1, 0, "question")),
                )
            },
        )

    @Test
    fun `the answer mapper reads json values of every json type`() {
        val mapper = PptxAnswerMapper(TestJsonCodec.create())
        val raw =
            """
            {"s1-shape0":"text answer",
             "s1-shape1":42,
             "s1-shape2":true,
             "s1-shape3":null,
             "s1-shape4":["array"],
             "s1-shape5":{"nested":1},
             "  ":"blank id",
             "s1-shape6":"   ",
             "not-an-id":"ignored"}
            """.trimIndent()

        val answers = mapper.parse(raw, planOf("s1-shape0", "s1-shape1", "s1-shape2", "s1-shape3"))

        assertEquals("text answer", answers.answers["s1-shape0"])
        assertEquals("42.0", answers.answers["s1-shape1"])
        assertEquals("true", answers.answers["s1-shape2"])
        assertTrue(answers.answers["s1-shape3"].isNullOrEmpty())
        assertTrue(answers.answers["s1-shape6"].isNullOrEmpty())
    }

    @Test
    fun `the answer mapper rejects everything for an empty plan and drops unknown ids of a filled plan`() {
        val mapper = PptxAnswerMapper(TestJsonCodec.create())
        val raw = """{"s1-shape0":"kept","s9-shape9":"unknown"}"""

        val unrestricted = mapper.parse(raw, PptxQuestionPlan(emptyList()))
        val restricted = mapper.parse(raw, planOf("s1-shape0"))

        assertTrue(unrestricted.answers.isEmpty())
        assertEquals(setOf("s1-shape0"), restricted.answers.keys)
    }

    @Test
    fun `markdown answers tolerate bullets bold ids and blockquote prefixes`() {
        val mapper = PptxAnswerMapper(TestJsonCodec.create())
        val raw =
            """
            not a json at all
            - s1-shape0: plain bullet
            * **s1-shape1**: bold id
            + s1-shape2 - plus bullet
            > s1-shape3: quoted
            - s1-shape4:${"   "}
            - broken line without separator
            """.trimIndent()

        val answers = mapper.parse(raw, planOf("s1-shape0", "s1-shape1", "s1-shape2", "s1-shape3"))

        assertEquals("plain bullet", answers.answers["s1-shape0"])
        assertEquals("bold id", answers.answers["s1-shape1"])
        assertEquals("plus bullet", answers.answers["s1-shape2"])
        assertEquals("quoted", answers.answers["s1-shape3"])
        assertTrue(answers.answers["s1-shape4"].isNullOrEmpty())
    }

    @Test
    fun `json that is not an object falls back to the markdown parser`() {
        val mapper = PptxAnswerMapper(TestJsonCodec.create())

        val answers = mapper.parse("{ not json at all", planOf("s1-shape0"))

        assertTrue(answers.isEmpty())
    }
}
