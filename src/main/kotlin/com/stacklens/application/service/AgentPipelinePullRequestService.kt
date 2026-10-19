package com.stacklens.application.service

import com.stacklens.application.port.out.PullRequestDiffPort
import com.stacklens.application.port.out.PullRequestDiffRequest
import com.stacklens.application.service.pipeline.CopilotPipelineRegistry
import com.stacklens.application.stereotype.ApplicationService
import com.stacklens.domain.pipeline.PullRequestData

@ApplicationService
class AgentPipelinePullRequestService(
    private val registry: CopilotPipelineRegistry,
    private val pullRequestDiffPort: PullRequestDiffPort,
) {
    fun buildPullRequestData(id: Long): PullRequestData? {
        val run = registry.get(id) ?: return null
        val result =
            pullRequestDiffPort.computeDiffs(
                PullRequestDiffRequest(
                    repoPath = run.repoPath,
                    baseBranch = run.baseBranch,
                    baseSha = run.baseSha,
                    preExistingUntracked = run.preExistingUntracked,
                    finnOutput = run.finn.output,
                    tessOutput = run.tess.output,
                    quinnOutput = run.quinn.output,
                    veraOutput = run.vera.output,
                ),
            )
        val baseBranch = result?.baseBranch ?: ""
        val diffs = result?.diffs ?: emptyList()
        return PullRequestData(
            runId = run.id,
            featureSlug = run.featureSlug,
            gitBranch = run.gitBranch.ifBlank { run.featureSlug },
            baseBranch = baseBranch,
            repoPath = run.repoPath,
            diffs = diffs,
        )
    }
}
