package com.stacklens.application.port.out.knowledge

import com.stacklens.domain.model.knowledge.KnowledgeBase

interface KnowledgeResetPort {
    fun resetAllKnowledgeData()

    fun clearKnowledgeBase(base: KnowledgeBase)
}
