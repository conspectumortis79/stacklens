package com.stacklens.application.service.knowledge.ingestor

import com.stacklens.application.port.out.ConfluenceConfigPort
import com.stacklens.application.port.out.knowledge.ConfluenceHierarchyPort
import com.stacklens.application.port.out.knowledge.ConfluencePageSnapshot
import com.stacklens.application.service.knowledge.KnowledgeDocumentIngester
import com.stacklens.application.stereotype.ApplicationService
import com.stacklens.domain.model.knowledge.KnowledgeDocumentStatus
import com.stacklens.domain.model.knowledge.KnowledgeSource
import com.stacklens.domain.model.knowledge.KnowledgeSourceType

@ApplicationService
class ConfluenceKnowledgeSourceIngestor(
    private val confluenceAdapter: ConfluenceHierarchyPort,
    private val confluenceConfigPort: ConfluenceConfigPort,
    private val documentIngester: KnowledgeDocumentIngester,
) : KnowledgeSourceIngestor {
    override fun supports(type: KnowledgeSourceType): Boolean = type == KnowledgeSourceType.CONFLUENCE

    override fun ingest(source: KnowledgeSource): KnowledgeSourceIngestionOutcome {
        val confluenceConfigId =
            source.externalConfigId
                ?: error("Confluence source ${source.id} is missing a config id")
        val config =
            confluenceConfigPort.findConfluenceConfigById(confluenceConfigId)
                ?: error("Confluence config $confluenceConfigId not found")
        val rootUrl =
            source.rootUrl
                ?: source.externalReference
                ?: error("Confluence source ${source.id} is missing a root URL")
        confluenceAdapter.ensureHostMatches(config, rootUrl)
        var count = 0
        var anyDocFailed = false
        for (snapshot: ConfluencePageSnapshot in confluenceAdapter.walkHierarchy(config, rootUrl, source.includeChildren, source.maxDepth)) {
            val docStatus =
                documentIngester.ingestDocument(
                    source = source,
                    title = snapshot.title,
                    plainText = snapshot.plainText,
                    sourceUri = snapshot.sourceUri,
                    sourceType = KnowledgeSourceType.CONFLUENCE,
                    mimeType = "text/html",
                    metadata = buildMetadata(snapshot),
                )
            if (docStatus == KnowledgeDocumentStatus.FAILED) anyDocFailed = true
            count++
        }
        if (count == 0) error("Confluence hierarchy walk returned no pages for $rootUrl")
        return KnowledgeSourceIngestionOutcome(ingestedCount = count, anyDocFailed = anyDocFailed)
    }

    private fun buildMetadata(snapshot: ConfluencePageSnapshot): Map<String, String> {
        val base =
            mutableMapOf(
                "pageId" to snapshot.pageId,
                "depth" to snapshot.depth.toString(),
            )
        if (snapshot.tables.isNotEmpty()) {
            base["tableCount"] = snapshot.tables.size.toString()
            base["tableRowCount"] = snapshot.tables.sumOf { it.rowCount }.toString()
            snapshot.tables.forEachIndexed { idx, table ->
                base["tableCaption.$idx"] = table.caption
                base["tableHeaders.$idx"] = table.headers.joinToString("|")
                base["tableRowCount.$idx"] = table.rowCount.toString()
            }
        }
        if (snapshot.calendars.isNotEmpty()) {
            base["calendarCount"] = snapshot.calendars.size.toString()
            base["calendarEventCount"] = snapshot.calendars.sumOf { it.eventCount }.toString()
            snapshot.calendars.forEachIndexed { idx, cal ->
                base["calendarId.$idx"] = cal.calendarId
                base["calendarTitle.$idx"] = cal.title
                base["calendarEventCount.$idx"] = cal.eventCount.toString()
            }
        }
        return base
    }
}
