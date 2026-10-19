package com.stacklens.application.port.inbound.rex

data class RexTestStageSpec(
    val stepLabel: String,
    val targetAliases: Array<String>,
    val noTargetMsg: String,
    val runningMsgTemplate: String,
    val fileExtensionRegex: Regex,
    val task: String,
    val fixAttemptMsgKey: String,
    val successMsgTemplate: String,
)
