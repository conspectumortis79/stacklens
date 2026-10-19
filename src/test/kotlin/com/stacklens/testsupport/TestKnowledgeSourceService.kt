package com.stacklens.testsupport

import com.stacklens.application.port.out.time.ClockPort
import com.stacklens.application.service.knowledge.KnowledgeSourceLister
import com.stacklens.application.service.knowledge.KnowledgeSourceService
import com.stacklens.application.service.knowledge.KnowledgeSourceServiceDeps
import com.stacklens.application.service.knowledge.LocalKnowledgeSourceCreator
import com.stacklens.testsupport.knowledgeSourceService

fun knowledgeSourceService(
    deps: KnowledgeSourceServiceDeps,
    clock: ClockPort = com.stacklens.adapter.out.time.SystemClockPort,
): KnowledgeSourceService =
    KnowledgeSourceService(
        deps = deps,
        lister =
            KnowledgeSourceLister(
                sourcePort = deps.sourcePort,
                chunkPort = deps.chunkPort,
            ),
        localCreator =
            LocalKnowledgeSourceCreator(
                basePort = deps.basePort,
                sourcePort = deps.sourcePort,
                extractors = deps.extractors,
                documentIngester = deps.documentIngester,
                clock = clock,
            ),
        clock = clock,
    )
