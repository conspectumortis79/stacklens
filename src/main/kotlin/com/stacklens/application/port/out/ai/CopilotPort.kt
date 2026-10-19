package com.stacklens.application.port.out.ai

interface CopilotPort : AiChatPort

interface CopilotStreamingPort :
    CopilotPort,
    AiStreamingPort,
    RepoToolPort {
    val writesFilesNatively: Boolean get() = false
}
