package com.grokapp.googlebookos.data.model

import java.util.UUID

data class SessionSummary(
    val sessionId: String,
    val title: String,
    val updatedAt: Long = 0,
    val lastPromptTime: String = "",
    val timestampStr: String = "",
    val archived: Boolean = false,
    val model: String? = null
)

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: String, // "user", "assistant", "system"
    val content: String,
    val thinking: String? = null,
    val isThinking: Boolean = false,
    val toolCalls: List<ToolCallInfo> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)

data class ToolCallInfo(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val args: String = "",
    val result: String? = null,
    val status: ToolStatus = ToolStatus.RUNNING
)

enum class ToolStatus {
    RUNNING,
    SUCCESS,
    FAILED
}

data class ServerStatus(
    val status: String = "ok",
    val proxy: String? = null,
    val proxyConnected: Boolean = false,
    val models: List<ModelOption> = emptyList(),
    val reasoningEfforts: List<ReasoningOption> = emptyList(),
    val permissionModes: List<PermissionOption> = emptyList(),
    val cwd: String = "/storage/emulated/10/Codes"
)

data class ModelOption(
    val id: String,
    val name: String,
    val desc: String = ""
)

data class ReasoningOption(
    val id: String,
    val name: String,
    val desc: String = ""
)

data class PermissionOption(
    val id: String,
    val name: String,
    val desc: String = ""
)

sealed class ChatStreamEvent {
    data class Content(val text: String) : ChatStreamEvent()
    data class Thinking(val text: String) : ChatStreamEvent()
    data class ToolStart(val name: String, val args: String) : ChatStreamEvent()
    data class ToolEnd(val name: String, val result: String) : ChatStreamEvent()
    data class Done(val fullText: String? = null) : ChatStreamEvent()
    data class Error(val message: String) : ChatStreamEvent()
}

data class ProjectItem(
    val name: String,
    val path: String,
    val isGit: Boolean = false,
    val lastModified: Long = 0
)

data class FsDirectoryItem(
    val name: String,
    val path: String,
    val isHidden: Boolean = false,
    val isGit: Boolean = false
)

data class FsListResponse(
    val currentPath: String,
    val parentPath: String? = null,
    val canCreate: Boolean = true,
    val directories: List<FsDirectoryItem> = emptyList()
)

data class CreateProjectRequest(
    val name: String,
    val parentDir: String,
    val template: String = "empty",
    val initGit: Boolean = true,
    val createReadme: Boolean = true
)
