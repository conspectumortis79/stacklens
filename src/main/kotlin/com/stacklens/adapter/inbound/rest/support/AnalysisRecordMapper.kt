package com.stacklens.adapter.inbound.rest.support

import com.stacklens.domain.model.CodeAnalysis
import com.stacklens.domain.model.LoadTestAnalysis
import com.stacklens.domain.model.PipelineAnalysis
import com.stacklens.domain.model.SecurityAnalysis

internal fun CodeAnalysis.toResponseMap(): Map<String, Any?> =
    mapOf(
        "id" to id,
        "status" to status,
        "summaryText" to summaryText,
        "findingsJson" to findingsJson,
        "fileChangesJson" to fileChangesJson,
        "errorMessage" to errorMessage,
        "modelUsed" to modelUsed,
        "mrUrl" to mrUrl,
        "mrBranch" to mrBranch,
        "mermaid" to mermaid,
        "drawioXml" to drawioXml,
        "monorepoSubPath" to monorepoSubPath,
        "monorepoAppName" to monorepoAppName,
        "createdAt" to createdAt.toString(),
        "completedAt" to completedAt?.toString(),
        "repoConfigId" to repoConfigId,
        "gitlabConfigId" to gitlabConfigId,
        "gitlabProjectId" to gitlabProjectId,
        "gitlabProjectPath" to gitlabProjectPath,
        "githubRepoConfigId" to githubRepoConfigId,
    )

internal fun SecurityAnalysis.toResponseMap(): Map<String, Any?> =
    mapOf(
        "id" to id,
        "repoConfigId" to repoConfigId,
        "gitlabConfigId" to gitlabConfigId,
        "gitlabProjectId" to gitlabProjectId,
        "gitlabProjectPath" to gitlabProjectPath,
        "githubRepoConfigId" to githubRepoConfigId,
        "status" to status,
        "summaryText" to summaryText,
        "findingsJson" to findingsJson,
        "errorMessage" to errorMessage,
        "modelUsed" to modelUsed,
        "currentIteration" to currentIteration,
        "totalIterations" to totalIterations,
        "currentStep" to currentStep,
        "createdAt" to createdAt,
        "completedAt" to completedAt,
    )

internal fun LoadTestAnalysis.toResponseMap(): Map<String, Any?> =
    mapOf(
        "id" to id,
        "status" to status,
        "summaryText" to summaryText,
        "findingsJson" to findingsJson,
        "k6Script" to k6Script,
        "environmentsJson" to environmentsJson,
        "errorMessage" to errorMessage,
        "modelUsed" to modelUsed,
        "gitlabConfigId" to gitlabConfigId,
        "gitlabProjectId" to gitlabProjectId,
        "gitlabProjectPath" to gitlabProjectPath,
        "repoConfigId" to repoConfigId,
        "githubRepoConfigId" to githubRepoConfigId,
        "createdAt" to createdAt.toString(),
        "completedAt" to completedAt?.toString(),
        "k6Output" to k6Output,
        "k6Error" to k6Error,
        "k6ExitCode" to k6ExitCode,
        "k6SummaryJson" to k6SummaryJson,
    )

internal fun PipelineAnalysis.toResponseMap(): Map<String, Any?> =
    mapOf(
        "id" to id,
        "status" to status,
        "summaryText" to summaryText,
        "findingsJson" to findingsJson,
        "fileDescriptionsJson" to fileDescriptionsJson,
        "optimizationsJson" to optimizationsJson,
        "mermaid" to mermaid,
        "errorMessage" to errorMessage,
        "modelUsed" to modelUsed,
        "createdAt" to createdAt.toString(),
        "completedAt" to completedAt?.toString(),
        "repoConfigId" to repoConfigId,
        "gitlabConfigId" to gitlabConfigId,
        "gitlabProjectId" to gitlabProjectId,
        "gitlabProjectPath" to gitlabProjectPath,
        "githubRepoConfigId" to githubRepoConfigId,
        "userPromptAdditions" to userPromptAdditions,
        "mrUrl" to mrUrl,
        "mrBranch" to mrBranch,
        "mrProvider" to mrProvider,
    )
