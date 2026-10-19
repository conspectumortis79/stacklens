package com.stacklens.domain.text

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AcceptanceCriteriaExtractorTest {
    @Test
    fun `extract returns empty for description without header`() {
        assertEquals(emptyList<String>(), AcceptanceCriteriaExtractor.extract("just some text without header"))
    }

    @Test
    fun `extract parses German Akzeptanzkriterien header`() {
        val desc =
            """
            Description text

            Akzeptanzkriterien:
            - [ ] first criterion
            - [ ] second criterion
            """.trimIndent()
        assertEquals(
            listOf("first criterion", "second criterion"),
            AcceptanceCriteriaExtractor.extract(desc),
        )
    }

    @Test
    fun `extract parses English AC header`() {
        val desc =
            """
            Acceptance Criteria:
            * item one
            * item two
            """.trimIndent()
        assertEquals(listOf("item one", "item two"), AcceptanceCriteriaExtractor.extract(desc))
    }

    @Test
    fun `extract parses AK abbreviation`() {
        val desc =
            """
            AK:
            - criterion
            """.trimIndent()
        assertEquals(listOf("criterion"), AcceptanceCriteriaExtractor.extract(desc))
    }

    @Test
    fun `extract parses AC abbreviation`() {
        val desc =
            """
            AC:
            - criterion
            """.trimIndent()
        assertEquals(listOf("criterion"), AcceptanceCriteriaExtractor.extract(desc))
    }

    @Test
    fun `extract parses ACs plural abbreviation`() {
        val desc =
            """
            ACs:
            - plural criterion
            """.trimIndent()
        assertEquals(listOf("plural criterion"), AcceptanceCriteriaExtractor.extract(desc))
    }

    @Test
    fun `extract parses acceptance criteria header without separator`() {
        val desc =
            """
            AcceptanceCriteria:
            - merged header item
            """.trimIndent()
        assertEquals(listOf("merged header item"), AcceptanceCriteriaExtractor.extract(desc))
    }

    @Test
    fun `extract parses acceptance criteria header with hyphen separator`() {
        val desc =
            """
            Acceptance-Criteria:
            - hyphen header item
            """.trimIndent()
        assertEquals(listOf("hyphen header item"), AcceptanceCriteriaExtractor.extract(desc))
    }

    @Test
    fun `extract parses Akzeptanz header without Kriterien suffix`() {
        val desc =
            """
            Akzeptanz:
            - kurzes Kriterium
            """.trimIndent()
        assertEquals(listOf("kurzes Kriterium"), AcceptanceCriteriaExtractor.extract(desc))
    }

    @Test
    fun `extract does not match abbreviations inside larger words`() {
        assertEquals(emptyList<String>(), AcceptanceCriteriaExtractor.extract("QUAK:\n- not a criterion"))
        assertEquals(emptyList<String>(), AcceptanceCriteriaExtractor.extract("FAC:\n- not a criterion"))
    }

    @Test
    fun `extract handles checked checkbox prefix`() {
        val desc =
            """
            Acceptance Criteria:
            - [x] done criterion
            - [X] another done
            """.trimIndent()
        assertEquals(
            listOf("done criterion", "another done"),
            AcceptanceCriteriaExtractor.extract(desc),
        )
    }

    @Test
    fun `extract parses plain bracket checkbox prefixes`() {
        val desc =
            """
            AC:
            [ ] Todo item
            [x] Done item
            [X] Capital done
            """.trimIndent()
        assertEquals(
            listOf("Todo item", "Done item", "Capital done"),
            AcceptanceCriteriaExtractor.extract(desc),
        )
    }

    @Test
    fun `extract handles numbered list items`() {
        val desc =
            """
            Acceptance Criteria:
            1. first
            2. second
            """.trimIndent()
        assertEquals(listOf("first", "second"), AcceptanceCriteriaExtractor.extract(desc))
    }

    @Test
    fun `extract strips numeric prefixes`() {
        assertEquals(
            listOf("First", "Second", "Third"),
            AcceptanceCriteriaExtractor.extract("AC:\n1. First\n2) Second\n3 Third"),
        )
    }

    @Test
    fun `extract parses numbered, dashed and starred bullets`() {
        assertEquals(
            listOf("first bullet", "second bullet", "third", "fourth"),
            AcceptanceCriteriaExtractor.extract(
                "Akzeptanzkriterien\n1. first bullet\n2. second bullet\n- third\n* fourth\n\n## Done",
            ),
        )
    }

    @Test
    fun `extract with EN header returns bullet items`() {
        assertEquals(
            listOf("User can login", "User can logout"),
            AcceptanceCriteriaExtractor.extract(
                "Background info.\n\nAcceptance Criteria:\n- User can login\n- User can logout",
            ),
        )
    }

    @Test
    fun `extract with German header`() {
        assertEquals(
            listOf("Login", "Logout"),
            AcceptanceCriteriaExtractor.extract("Info.\n\nAkzeptanzkriterien:\n- Login\n- Logout"),
        )
    }

    @Test
    fun `extract with Abnahmekriterien header`() {
        assertEquals(
            listOf("Login", "Logout"),
            AcceptanceCriteriaExtractor.extract("Info.\n\nAbnahmekriterien:\n- Login\n- Logout"),
        )
    }

    @Test
    fun `extract with AK abbreviation`() {
        assertEquals(
            listOf("Login", "Logout"),
            AcceptanceCriteriaExtractor.extract("Info.\n\nAK:\n- Login\n- Logout"),
        )
    }

    @Test
    fun `extract returns empty when no header present`() {
        assertTrue(AcceptanceCriteriaExtractor.extract("No criteria here, just text.").isEmpty())
    }

    @Test
    fun `extract strips checkbox markers`() {
        assertEquals(
            listOf("Todo item", "Done item", "Capital done"),
            AcceptanceCriteriaExtractor.extract("AC:\n- [ ] Todo item\n- [x] Done item\n- [X] Capital done"),
        )
    }

    @Test
    fun `extract limits to 20 items`() {
        val bullets = (1..30).joinToString("\n") { "- item $it" }
        assertEquals(20, AcceptanceCriteriaExtractor.extract("AC:\n$bullets").size)
    }

    @Test
    fun `extract caps at 20 items`() {
        val items = (1..25).joinToString("\n") { "- criterion $it" }
        assertEquals(20, AcceptanceCriteriaExtractor.extract("Acceptance Criteria:\n$items").size)
    }

    @Test
    fun `extract handles nextSection present`() {
        assertEquals(
            listOf("First item", "Second item"),
            AcceptanceCriteriaExtractor.extract(
                "AC:\n- First item\n- Second item\n\n## Next section\n- Third item here",
            ),
        )
    }

    @Test
    fun `extract stops at next section header (heading)`() {
        val desc =
            """
            Acceptance Criteria:
            - item one

            ## Additional info
            - this should not be included
            """.trimIndent()
        assertEquals(listOf("item one"), AcceptanceCriteriaExtractor.extract(desc))
    }

    @Test
    fun `extract stops at capitalized label section`() {
        val desc =
            """
            Acceptance Criteria:
            - item one

            Notes:
            - this should not be included
            """.trimIndent()
        assertEquals(listOf("item one"), AcceptanceCriteriaExtractor.extract(desc))
    }

    @Test
    fun `extract without trailing newline after header still works`() {
        assertEquals(listOf("item"), AcceptanceCriteriaExtractor.extract("Acceptance Criteria:\n- item"))
    }

    @Test
    fun `extract handles header with colon at end and next line`() {
        assertEquals(
            listOf("item 1", "item 2"),
            AcceptanceCriteriaExtractor.extract("Acceptance Criteria\n- item 1\n- item 2"),
        )
    }

    @Test
    fun `extract filters out very short items`() {
        val desc =
            """
            Acceptance Criteria:
            - ok
            - this is a real criterion
            """.trimIndent()
        assertEquals(listOf("this is a real criterion"), AcceptanceCriteriaExtractor.extract(desc))
    }

    @Test
    fun `extract filters out items with three or fewer characters`() {
        assertEquals(
            listOf("ok this is long enough"),
            AcceptanceCriteriaExtractor.extract("AC:\n- ab\n- ok this is long enough\n- cd"),
        )
    }

    @Test
    fun `extract truncates long sections to 1500 chars`() {
        val longContent = "x".repeat(2000)
        val result = AcceptanceCriteriaExtractor.extract("AC:\n- $longContent")
        assertEquals(1, result.size)
        assertEquals(1497, result.single().length)
    }

    @Test
    fun `extract returns empty when no header is matched`() {
        assertTrue(AcceptanceCriteriaExtractor.extract("no header here").isEmpty())
    }
}
