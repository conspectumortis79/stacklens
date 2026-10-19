package com.stacklens.application.service.knowledge

import com.stacklens.adapter.out.shared.UuidIdGenerator
import com.stacklens.application.port.out.IdGeneratorPort
import com.stacklens.application.port.out.file.FileReadWritePort
import com.stacklens.application.port.out.knowledge.KnowledgeArchiveReaderPort
import com.stacklens.application.port.out.knowledge.KnowledgeBasePort
import com.stacklens.application.port.out.knowledge.KnowledgeBundlePort
import com.stacklens.application.port.out.knowledge.KnowledgeChunkPort
import com.stacklens.application.port.out.knowledge.KnowledgeDocumentPort
import com.stacklens.application.port.out.knowledge.KnowledgeHashingPort
import com.stacklens.application.port.out.knowledge.KnowledgeIndexPort
import com.stacklens.application.port.out.knowledge.KnowledgeSourcePort
import com.stacklens.application.port.out.time.ClockPort

internal fun knowledgeImportService(
    basePort: KnowledgeBasePort,
    sourcePort: KnowledgeSourcePort,
    documentPort: KnowledgeDocumentPort,
    chunkPort: KnowledgeChunkPort,
    readerPort: KnowledgeArchiveReaderPort,
    indexPort: KnowledgeIndexPort,
    hashing: KnowledgeHashingPort,
    clock: ClockPort,
    fileReadWritePort: FileReadWritePort,
    bundlePort: KnowledgeBundlePort,
    idGenerator: IdGeneratorPort = UuidIdGenerator(),
): KnowledgeImportService =
    KnowledgeImportService(
        fileReadWritePort = fileReadWritePort,
        bundlePort = bundlePort,
        persistence =
            KnowledgeArchivePersistence(
                basePort = basePort,
                sourcePort = sourcePort,
                documentPort = documentPort,
                chunkPort = chunkPort,
                readerPort = readerPort,
                indexPort = indexPort,
                hashing = hashing,
                clock = clock,
                idGenerator = idGenerator,
            ),
    )
