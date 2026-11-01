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
    data class ToolStart(val id: String = "", val name: String, val args: String = "") : ChatStreamEvent()
    data class ToolEnd(val id: String = "", val name: String = "", val result: String = "", val status: String = "success") : ChatStreamEvent()
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

data class ProductUsageItem(
    val product: String,
    val usagePercent: Float
)

data class SubscriptionQuota(
    val tier: String = "SuperGrok",
    val email: String = "",
    val creditUsagePercent: Float = 0f,
    val creditRemainingPercent: Float = 100f,
    val periodType: String = "USAGE_PERIOD_TYPE_WEEKLY",
    val periodStart: String = "",
    val periodEnd: String = "",
    val productUsage: List<ProductUsageItem> = emptyList(),
    val updatedAt: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
)

data class GitDiffFile(
    val path: String,
    val name: String,
    val status: String = "M",
    val statusLabel: String = "修改",
    val additions: Int = 0,
    val deletions: Int = 0,
    val diff: String = ""
)

data class GitDiffResponse(
    val status: String = "ok",
    val isGit: Boolean = false,
    val cwd: String = "",
    val branch: String = "",
    val commitId: String = "",
    val commitMsg: String = "",
    val hasUncommitted: Boolean = false,
    val scope: String = "working",
    val statSummary: String = "",
    val files: List<GitDiffFile> = emptyList()
)

data class CodeReviewState(
    val isGit: Boolean = false,
    val cwd: String = "",
    val branch: String = "",
    val commitId: String = "",
    val commitMsg: String = "",
    val hasUncommitted: Boolean = false,
    val scope: String = "auto",
    val statSummary: String = "",
    val files: List<GitDiffFile> = emptyList(),
    val selectedFilePath: String? = null,
    val selectedFileContent: String? = null,
    val isReviewing: Boolean = false,
    val reviewReport: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

