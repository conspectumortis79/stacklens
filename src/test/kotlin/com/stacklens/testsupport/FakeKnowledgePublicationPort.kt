package com.stacklens.testsupport

import com.stacklens.application.service.knowledge.KnowledgePublication
import com.stacklens.application.service.knowledge.KnowledgePublicationCandidate
import com.stacklens.application.service.knowledge.KnowledgePublicationReference

class FakeKnowledgePublicationPort : KnowledgePublication {
    val published: MutableList<KnowledgePublicationCandidate> = mutableListOf()

    val removed: MutableList<KnowledgePublicationReference> = mutableListOf()

    override fun publish(candidate: KnowledgePublicationCandidate) {
        published += candidate
    }

    override fun remove(reference: KnowledgePublicationReference) {
        removed += reference
    }
}
