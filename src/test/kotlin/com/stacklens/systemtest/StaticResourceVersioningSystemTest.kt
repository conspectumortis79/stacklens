package com.stacklens.systemtest

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpStatus

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "spring.web.resources.chain.strategy.content.enabled=true",
        "spring.web.resources.chain.strategy.content.paths=/**",
    ],
)
class StaticResourceVersioningSystemTest {
    @Autowired
    lateinit var restTemplate: TestRestTemplate

    @Test
    fun `knowledge page references content-versioned assets and the versioned css resolves`() {
        val page = restTemplate.getForEntity("/knowledge", String::class.java)

        assertEquals(HttpStatus.OK, page.statusCode)
        val body = page.body.orEmpty()
        for (asset in listOf("css/knowledge.css", "css/custom.css", "css/themes.css", "js/shared-helpers.js")) {
            val (dir, file) = asset.split("/")
            val name = file.substringBeforeLast('.')
            val ext = file.substringAfterLast('.')
            assertTrue(
                Regex("""/$dir/$name-[0-9a-f]{32}\.$ext""").containsMatchIn(body),
                "rendered /knowledge must reference a content-versioned $asset",
            )
        }

        val versionedHref =
            Regex("""href="(/css/knowledge-[0-9a-f]{32}\.css)"""")
                .find(body)
                ?.groupValues
                ?.get(1)
        assertNotNull(versionedHref, "no versioned knowledge.css link found in /knowledge")
        val css = restTemplate.getForEntity(versionedHref!!, String::class.java)
        assertEquals(HttpStatus.OK, css.statusCode)
        assertTrue(
            css.body.orEmpty().contains(".kb-page .card-header h5"),
            "versioned knowledge.css must serve the current content",
        )
    }

    @Test
    fun `unversioned asset url still resolves but a wrong hash does not`() {
        val plain = restTemplate.getForEntity("/css/knowledge.css", String::class.java)
        assertEquals(HttpStatus.OK, plain.statusCode)

        val bogus =
            restTemplate.getForEntity(
                "/css/knowledge-00000000000000000000000000000000.css",
                String::class.java,
            )
        assertEquals(HttpStatus.NOT_FOUND, bogus.statusCode)
    }
}
