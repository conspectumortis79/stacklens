package com.stacklens.adapter.out.shared

import org.springframework.stereotype.Component

@Component
internal class JiraSprintIssueFieldResolver {
    fun resolveDoneTimestamp(item: JiraSprintIssueItem): String? {
        val direct = item.fields.resolutiondate ?: item.fields.statuscategorychangedate
        if (!direct.isNullOrBlank()) return direct

        return item.changelog
            ?.histories
            ?.asSequence()
            ?.filter { h ->
                h.items.any { i ->
                    val toStatus =
                        i.`toString`
                            ?.trim()
                            ?.lowercase()
                            .orEmpty()
                    i.field.equals("status", ignoreCase = true) &&
                        DONE_STATUSES.contains(toStatus)
                }
            }?.mapNotNull { it.created }
            ?.lastOrNull()
    }

    fun resolveQaLoopCount(item: JiraSprintIssueItem): Int {
        val qaEntries =
            item.changelog?.histories?.count { h ->
                h.items.any { i ->
                    val toStatus =
                        i.`toString`
                            ?.trim()
                            ?.lowercase()
                            .orEmpty()
                    i.field.equals("status", ignoreCase = true) &&
                        QA_STATUSES.contains(toStatus)
                }
            } ?: 0

        return (qaEntries - 1).coerceAtLeast(0)
    }

    fun resolveStartedTimestamp(item: JiraSprintIssueItem): String? {
        val startFromHistory =
            item.changelog
                ?.histories
                ?.asSequence()
                ?.filter { h ->
                    h.items.any { i ->
                        val toStatus =
                            i.`toString`
                                ?.trim()
                                ?.lowercase()
                                .orEmpty()
                        i.field.equals("status", ignoreCase = true) &&
                            START_STATUSES.contains(toStatus)
                    }
                }?.mapNotNull { it.created }
                ?.firstOrNull()

        return startFromHistory ?: item.fields.created
    }

    companion object {
        val DONE_STATUSES: Set<String> = setOf("geschlossen", "fertig", "done", "closed", "resolved")

        val QA_STATUSES: Set<String> = setOf("waiting for qa", "qa", "in qa", "ready for qa")

        val START_STATUSES: Set<String> = setOf("in arbeit", "in progress", "doing", "started")
    }
}
