package com.stacklens.adapter.out.knowledge.confluence

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.domain.model.ConfluenceConfig
import com.stacklens.shared.http.ReactiveHttpClientFactory
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ConfluenceDemoKbImportSystemTest {
    private val mapper = ObjectMapper()
    private lateinit var server: MockWebServer
    private lateinit var adapter: ConfluenceHierarchyAdapter

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        adapter = ConfluenceHierarchyAdapter(ReactiveHttpClientFactory(connectTimeoutMs = 5000, readTimeoutMs = 15000))
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun config(): ConfluenceConfig =
        ConfluenceConfig(
            name = "Demo Confluence",
            baseUrl = server.url("/").toString().trimEnd('/'),
            username = "demo",
            apiToken = "demo-token",
        )

    @Test
    fun `Confluence Demo KB-Import walks root + two children and linearizes text, tables and SVG labels`() {
        server.dispatcher = demoKbImportDispatcher()

        val results =
            adapter
                .walkHierarchy(
                    config(),
                    "${server.url("/").toString().trimEnd('/')}/spaces/DEMO/pages/60001/Confluence+Demo+KB-Import",
                    includeChildren = true,
                    maxDepth = 10,
                    maxPages = 50,
                ).toList()

        assertEquals(3, results.size, "expected root + 2 children, got ${results.map { it.title }}")

        val root = results.single { it.pageId == "60001" }
        val onCall = results.single { it.pageId == "60002" }
        val pipeline = results.single { it.pageId == "60003" }

        assertEquals("Confluence Demo KB-Import", root.title)
        assertEquals("On-Call Support Schedule", onCall.title)
        assertEquals("Deployment Pipeline Architecture", pipeline.title)

        assertEquals(0, root.depth)
        assertEquals(1, onCall.depth)
        assertEquals(1, pipeline.depth)

        assertTrue(root.plainText.contains("StackLens Wissensdatenbank Confluence demo content"))
        assertTrue(root.plainText.contains("Confluence Demo KB-Import"))
        assertTrue(root.plainText.contains("On-Call Support Schedule"))
        assertTrue(root.plainText.contains("Deployment Pipeline Architecture"))
        assertTrue(root.plainText.contains("fake-atlassian"))

        assertTrue(pipeline.plainText.contains("Stage 1 — Build"))
        assertTrue(pipeline.plainText.contains("Stage 2 — Test"))
        assertTrue(pipeline.plainText.contains("Stage 3 — Release"))
        assertTrue(pipeline.plainText.contains("argocd app rollback"))

        assertTrue(onCall.plainText.contains("Anna Schmidt"))
        assertTrue(onCall.plainText.contains("Björn Müller"))
        assertTrue(onCall.plainText.contains("Clara Weber"))
        assertTrue(onCall.plainText.contains("David Klein"))

        val datesFound =
            listOf(
                "2026-01-06",
                "2026-01-07",
                "2026-01-08",
                "2026-01-09",
                "2026-01-13",
                "2026-01-14",
                "2026-01-19",
            ).all { it in onCall.plainText }
        assertTrue(datesFound, "expected on-call page to contain the 14-day rotation dates")

        assertTrue(onCall.plainText.contains("08:00 – 18:00"))
        assertTrue(onCall.plainText.contains("10:00 – 14:00"))
        assertTrue(onCall.plainText.contains("17:00 – 22:00"))
        assertTrue(onCall.plainText.contains("18:00 – 22:00"))

        assertTrue(onCall.plainText.contains("SEV1 (Critical)"))
        assertTrue(onCall.plainText.contains("Engineering Manager (Lena)"))

        assertEquals(1, root.tables.size, "root page has exactly one summary table")
        assertEquals(2, onCall.tables.size, "on-call page has 2 tables (rotation + escalation)")
        assertEquals(0, pipeline.tables.size, "pipeline page has no manual tables")

        val rotationHeaders = onCall.tables.first().headers
        assertTrue(rotationHeaders.contains("Datum"))
        assertTrue(rotationHeaders.contains("Entwickler:in"))
        assertTrue(rotationHeaders.contains("Primäre Supportzeit"))
        assertTrue(rotationHeaders.contains("Sekundäre Bereitschaft"))
        assertTrue(rotationHeaders.contains("Eskalationspfad"))
        assertEquals(14, onCall.tables.first().rowCount, "rotation table has 14 rows")

        val escalationHeaders = onCall.tables[1].headers
        assertTrue(escalationHeaders.contains("Severity"))
        assertTrue(escalationHeaders.contains("Response window"))

        val rootSummaryHeaders = root.tables.first().headers
        assertTrue(rootSummaryHeaders.contains("Page"))
        assertTrue(rootSummaryHeaders.contains("ID"))
        assertTrue(rootSummaryHeaders.contains("Tiefe"))

        val annaRows = onCall.plainText.lines().filter { it.contains("Anna Schmidt") }
        assertTrue(annaRows.any { "2026-01-06" in it && "Montag" in it }, "Anna 2026-01-06 Montag line not found")
        assertTrue(annaRows.any { "2026-01-06" in it && "08:00 – 18:00" in it }, "Anna 2026-01-06 08:00 line not found")
        assertTrue(annaRows.any { "2026-01-10" in it && "Freitag" in it }, "Anna 2026-01-10 Freitag line not found")

        val davidRows = onCall.plainText.lines().filter { it.contains("David Klein") }
        assertTrue(davidRows.any { "2026-01-09" in it && "Donnerstag" in it }, "David 2026-01-09 Donnerstag line not found")
        assertTrue(davidRows.any { "2026-01-13" in it && "Montag" in it }, "David 2026-01-13 Montag line not found")

        val bjoernSaturdayRows = onCall.plainText.lines().filter { it.contains("Björn Müller") && it.contains("Samstag") }
        assertTrue(bjoernSaturdayRows.any { "2026-01-11" in it }, "Björn 2026-01-11 Samstag line not found")

        assertTrue(root.plainText.contains("Confluence REST"))
        assertTrue(pipeline.plainText.contains("GitHub Actions"))
        assertTrue(pipeline.plainText.contains("ArgoCD"))
    }

    @Test
    fun `Confluence Demo KB-Import with includeChildren=false emits only the root page`() {
        server.dispatcher = demoKbImportDispatcher()

        val results =
            adapter
                .walkHierarchy(
                    config(),
                    "${server.url("/").toString().trimEnd('/')}/spaces/DEMO/pages/60001/Confluence+Demo+KB-Import",
                    includeChildren = false,
                    maxDepth = 10,
                    maxPages = 50,
                ).toList()

        assertEquals(1, results.size)
        assertEquals("60001", results[0].pageId)
        assertEquals("Confluence Demo KB-Import", results[0].title)
    }

    private fun demoKbImportDispatcher(): Dispatcher {
        return object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.contains("/content/60001?expand=") ->
                        pageResponse("60001", "Confluence Demo KB-Import", ROOT_BODY)
                    path.contains("/content/60001/child/page") ->
                        childResponse(listOf("60002", "60003"))
                    path.contains("/content/60002?expand=") ->
                        pageResponse("60002", "On-Call Support Schedule", ON_CALL_BODY)
                    path.contains("/content/60002/child/page") ->
                        emptyChildrenResponse()
                    path.contains("/content/60003?expand=") ->
                        pageResponse("60003", "Deployment Pipeline Architecture", PIPELINE_BODY)
                    path.contains("/content/60003/child/page") ->
                        emptyChildrenResponse()
                    else ->
                        MockResponse().setResponseCode(404).setBody("""{"message":"not found"}""")
                }
            }
        }
    }

    private fun pageResponse(
        id: String,
        title: String,
        body: String,
    ): MockResponse {
        val payload =
            mapper.writeValueAsString(
                mapOf(
                    "id" to id,
                    "title" to title,
                    "space" to mapOf("key" to "DEMO"),
                    "body" to mapOf("view" to mapOf("value" to body)),
                    "version" to mapOf("number" to 1),
                ),
            )
        return MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody(payload)
    }

    private fun childResponse(childIds: List<String>): MockResponse {
        val payload =
            mapper.writeValueAsString(
                mapOf(
                    "results" to childIds.map { mapOf("id" to it) },
                    "start" to 0,
                    "limit" to 25,
                    "size" to childIds.size,
                ),
            )
        return MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody(payload)
    }

    private fun emptyChildrenResponse(): MockResponse =
        MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody("""{"results":[],"start":0,"limit":25,"size":0}""")
}

private val ROOT_BODY = """
<h1>Confluence Demo KB-Import</h1>
<p>This is the root page of the StackLens Wissensdatenbank Confluence
demo content. It demonstrates the full Confluence hierarchy import.</p>
<h2>Why this demo exists</h2>
<p>StackLens ingests Confluence pages into the local Lucene index
so the chat can answer questions that span JIRA tickets, code
analysis findings and internal documentation.</p>
<ul>
  <li>Confluence Demo KB-Import (this page)</li>
  <li>On-Call Support Schedule</li>
  <li>Deployment Pipeline Architecture</li>
</ul>
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 720 220">
  <text x="80" y="65">fake-atlassian</text>
  <text x="280" y="65">Confluence REST</text>
</svg>
<table>
  <thead><tr><th>Page</th><th>ID</th><th>Tiefe</th></tr></thead>
  <tbody>
    <tr><td>Confluence Demo KB-Import</td><td>60001</td><td>0</td></tr>
    <tr><td>On-Call Support Schedule</td><td>60002</td><td>1</td></tr>
    <tr><td>Deployment Pipeline Architecture</td><td>60003</td><td>1</td></tr>
  </tbody>
</table>
<p><img src="/download/attachments/60001/architecture-overview.png" alt="Confluence KB-Import architecture overview"/></p>
"""

private val ON_CALL_BODY = """
<h1>On-Call Support Schedule</h1>
<p>This page documents the on-call rotation for the StackLens platform team.</p>
<h2>Team</h2>
<ul>
  <li>Anna Schmidt — Platform / SRE focus.</li>
  <li>Björn Müller — Backend / API focus.</li>
  <li>Clara Weber — Frontend / UX focus.</li>
  <li>David Klein — QA / Tooling focus.</li>
</ul>
<h2>14-Tage schedule</h2>
<p>All times are Europe/Berlin.</p>
<table>
  <thead>
    <tr><th>Datum</th><th>Wochentag</th><th>Entwickler:in</th><th>Primäre Supportzeit</th><th>Sekundäre Bereitschaft</th><th>Eskalationspfad</th></tr>
  </thead>
  <tbody>
    <tr><td>2026-01-06</td><td>Montag</td><td>Anna Schmidt</td><td>08:00 – 18:00</td><td>18:00 – 22:00</td><td>Björn Müller</td></tr>
    <tr><td>2026-01-07</td><td>Dienstag</td><td>Björn Müller</td><td>08:00 – 18:00</td><td>18:00 – 22:00</td><td>Clara Weber</td></tr>
    <tr><td>2026-01-08</td><td>Mittwoch</td><td>Clara Weber</td><td>08:00 – 18:00</td><td>18:00 – 22:00</td><td>David Klein</td></tr>
    <tr><td>2026-01-09</td><td>Donnerstag</td><td>David Klein</td><td>08:00 – 18:00</td><td>18:00 – 22:00</td><td>Anna Schmidt</td></tr>
    <tr><td>2026-01-10</td><td>Freitag</td><td>Anna Schmidt</td><td>08:00 – 17:00</td><td>17:00 – 22:00</td><td>Björn Müller</td></tr>
    <tr><td>2026-01-11</td><td>Samstag</td><td>Björn Müller</td><td>10:00 – 14:00</td><td>14:00 – 20:00</td><td>Clara Weber</td></tr>
    <tr><td>2026-01-12</td><td>Sonntag</td><td>Clara Weber</td><td>10:00 – 14:00</td><td>14:00 – 20:00</td><td>David Klein</td></tr>
    <tr><td>2026-01-13</td><td>Montag</td><td>David Klein</td><td>08:00 – 18:00</td><td>18:00 – 22:00</td><td>Anna Schmidt</td></tr>
    <tr><td>2026-01-14</td><td>Dienstag</td><td>Anna Schmidt</td><td>08:00 – 18:00</td><td>18:00 – 22:00</td><td>Björn Müller</td></tr>
    <tr><td>2026-01-15</td><td>Mittwoch</td><td>Björn Müller</td><td>08:00 – 18:00</td><td>18:00 – 22:00</td><td>Clara Weber</td></tr>
    <tr><td>2026-01-16</td><td>Donnerstag</td><td>Clara Weber</td><td>08:00 – 18:00</td><td>18:00 – 22:00</td><td>David Klein</td></tr>
    <tr><td>2026-01-17</td><td>Freitag</td><td>David Klein</td><td>08:00 – 17:00</td><td>17:00 – 22:00</td><td>Anna Schmidt</td></tr>
    <tr><td>2026-01-18</td><td>Samstag</td><td>Anna Schmidt</td><td>10:00 – 14:00</td><td>14:00 – 20:00</td><td>Björn Müller</td></tr>
    <tr><td>2026-01-19</td><td>Sonntag</td><td>Björn Müller</td><td>10:00 – 14:00</td><td>14:00 – 20:00</td><td>Clara Weber</td></tr>
  </tbody>
</table>
<h2>Diagram</h2>
<pre><code>         Anna Schmidt
        /            \
Björn Müller — Clara Weber
        \            /
         David Klein
</code></pre>
<h2>Escalation matrix</h2>
<table>
  <thead><tr><th>Severity</th><th>Response window</th><th>First contact</th><th>Escalation after timeout</th></tr></thead>
  <tbody>
    <tr><td>SEV1 (Critical)</td><td>15 min</td><td>On-call developer</td><td>Engineering Manager (Lena)</td></tr>
    <tr><td>SEV2 (High)</td><td>30 min</td><td>On-call developer</td><td>Team Lead (Markus)</td></tr>
    <tr><td>SEV3 (Medium)</td><td>4 hours</td><td>On-call developer</td><td>Next-day standup</td></tr>
  </tbody>
</table>
<p><img src="/download/attachments/60002/rotation-ring.png" alt="Visualised rotation ring"/></p>
"""

private val PIPELINE_BODY = """
<h1>Deployment Pipeline Architecture</h1>
<p>This page describes the three-stage deployment pipeline.</p>
<h2>Stage 1 — Build</h2>
<p>The Build stage runs inside a GitHub Actions runner. It executes
the Gradle build with the bootJar task.</p>
<h2>Stage 2 — Test</h2>
<p>The Test stage spins up an ephemeral PostgreSQL 14 container plus
a fake Ollama container, runs the unit, integration and
knowledge-system test suites.</p>
<h2>Stage 3 — Release</h2>
<p>The Release stage tags the commit, builds the Docker image, signs
it with cosign, and triggers the ArgoCD sync. Roll-back uses
argocd app rollback and takes about 90 seconds end-to-end.</p>
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 760 200">
  <text x="120" y="65">Stage 1 — Build</text>
  <text x="380" y="65">Stage 2 — Test</text>
  <text x="640" y="65">Stage 3 — Release</text>
</svg>
<p><img src="/download/attachments/60003/pipeline-stages.png" alt="Deployment pipeline stages overview"/></p>
"""
