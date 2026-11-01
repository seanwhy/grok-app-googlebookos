package com.grokapp.googlebookos.ui.workbench

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grokapp.googlebookos.data.api.GrokApiClient
import com.grokapp.googlebookos.data.model.ChatMessage
import com.grokapp.googlebookos.data.model.ChatStreamEvent
import com.grokapp.googlebookos.data.model.ServerStatus
import com.grokapp.googlebookos.data.model.SessionSummary
import com.grokapp.googlebookos.data.model.ToolCallInfo
import com.grokapp.googlebookos.data.model.ToolStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.grokapp.googlebookos.data.model.ProjectItem
import com.grokapp.googlebookos.data.model.SubscriptionQuota
import com.grokapp.googlebookos.data.model.CodeReviewState
import com.grokapp.googlebookos.data.model.FsDirectoryItem
import com.grokapp.googlebookos.data.model.FsListResponse
import com.grokapp.googlebookos.util.StorageHelper
import com.grokapp.googlebookos.util.ChromeHelper
import android.content.Context
import java.io.File
import java.util.UUID

enum class FolderPickerTarget {
    SWITCH_PROJECT,
    NEW_PROJECT_PARENT
}

data class WorkbenchUiState(
    val sessions: List<SessionSummary> = emptyList(),
    val currentSessionId: String? = null,
    val currentSessionTitle: String = "新会话",
    val messages: List<ChatMessage> = emptyList(),
    val isGenerating: Boolean = false,
    val serverStatus: ServerStatus? = null,
    val serverConnected: Boolean = false,
    val serverUrl: String = "http://127.0.0.1:8088",
    val selectedModel: String = "grok-4.7",
    val selectedReasoning: String = "high",
    val selectedPermission: String = "always-approve",
    val searchQuery: String = "",
    val showArchived: Boolean = false,
    val activeCwd: String = StorageHelper.getDefaultCodesDir(),
    val activeProjectName: String = "Codes",
    val recentProjects: List<ProjectItem> = emptyList(),
    val currentFsList: FsListResponse? = null,
    val isFsLoading: Boolean = false,
    val showNewProjectDialog: Boolean = false,
    val showFolderPickerDialog: Boolean = false,
    val folderPickerTarget: FolderPickerTarget = FolderPickerTarget.SWITCH_PROJECT,
    val pendingParentDir: String = StorageHelper.getDefaultCodesDir(),
    val subscriptionQuota: SubscriptionQuota = SubscriptionQuota(),
    val codeReviewState: CodeReviewState = CodeReviewState(),
    val showCodeReviewDialog: Boolean = false,
    val showActivityTimeline: Boolean = true,
    val selectedToolCallId: String? = null,
    val errorMessage: String? = null
)

class GrokWorkbenchViewModel(
    private val apiClient: GrokApiClient = GrokApiClient()
) : ViewModel() {

    private val _uiState = MutableStateFlow(WorkbenchUiState())
    val uiState: StateFlow<WorkbenchUiState> = _uiState.asStateFlow()

    private var streamJob: Job? = null
    private var heartbeatJob: Job? = null

    init {
        startHeartbeatLoop()
    }

    fun startHeartbeatLoop() {
        heartbeatJob?.cancel()
        heartbeatJob = viewModelScope.launch {
            while (isActive) {
                val wasConnected = _uiState.value.serverConnected
                apiClient.getStatus().fold(
                    onSuccess = { status ->
                        val currentActive = _uiState.value.activeCwd
                        val effectiveCwd = if (currentActive.isNotEmpty()) currentActive else status.cwd
                        val projName = File(effectiveCwd).name.ifEmpty { "Codes" }
                        _uiState.update {
                            it.copy(
                                serverStatus = status,
                                serverConnected = true,
                                activeCwd = effectiveCwd,
                                activeProjectName = projName,
                                errorMessage = null
                            )
                        }
                        if (!wasConnected) {
                            loadSessions()
                            loadProjects()
                            loadSubscriptionQuota()
                        }
                        delay(8_000)
                    },
                    onFailure = { err ->
                        _uiState.update {
                            it.copy(
                                serverConnected = false,
                                errorMessage = if (it.isGenerating) "连接中断: ${err.message}" else null
                            )
                        }
                        delay(2_500)
                    }
                )
            }
        }
    }

    fun refreshAll() {
        startHeartbeatLoop()
        loadSessions()
        loadProjects()
        loadSubscriptionQuota()
    }

    fun loadSubscriptionQuota() {
        _uiState.update { it.copy(subscriptionQuota = it.subscriptionQuota.copy(isLoading = true)) }
        viewModelScope.launch {
            apiClient.getSubscriptionQuota().fold(
                onSuccess = { quota ->
                    _uiState.update { it.copy(subscriptionQuota = quota.copy(isLoading = false, error = null)) }
                },
                onFailure = { err ->
                    _uiState.update { it.copy(subscriptionQuota = it.subscriptionQuota.copy(isLoading = false, error = err.message)) }
                }
            )
        }
    }

    fun loadProjects() {
        viewModelScope.launch {
            apiClient.getProjects().fold(
                onSuccess = { projects ->
                    _uiState.update { it.copy(recentProjects = projects) }
                },
                onFailure = {
                    val localList = StorageHelper.listLocalDirectories(StorageHelper.getDefaultCodesDir()).map {
                        ProjectItem(name = it.name, path = it.path, isGit = it.isGit)
                    }
                    _uiState.update { it.copy(recentProjects = localList) }
                }
            )
        }
    }

    fun loadFsList(path: String) {
        _uiState.update { it.copy(isFsLoading = true) }
        viewModelScope.launch {
            apiClient.getFsList(path).fold(
                onSuccess = { fsResp ->
                    _uiState.update { it.copy(currentFsList = fsResp, isFsLoading = false) }
                },
                onFailure = {
                    val localDirs = StorageHelper.listLocalDirectories(path)
                    val parent = File(path).parent
                    val fallback = FsListResponse(
                        currentPath = path,
                        parentPath = parent,
                        canCreate = true,
                        directories = localDirs
                    )
                    _uiState.update { it.copy(currentFsList = fallback, isFsLoading = false) }
                }
            )
        }
    }

    fun openNewProjectDialog(initialParentDir: String? = null) {
        val parent = initialParentDir ?: _uiState.value.activeCwd.let {
            if (File(it).name == "Codes") it else (File(it).parent ?: StorageHelper.getDefaultCodesDir())
        }
        _uiState.update {
            it.copy(
                showNewProjectDialog = true,
                pendingParentDir = parent
            )
        }
    }

    fun closeNewProjectDialog() {
        _uiState.update { it.copy(showNewProjectDialog = false) }
    }

    fun openFolderPickerDialog(target: FolderPickerTarget, initialPath: String? = null) {
        val path = initialPath ?: _uiState.value.activeCwd
        _uiState.update {
            it.copy(
                showFolderPickerDialog = true,
                folderPickerTarget = target
            )
        }
        loadFsList(path)
        loadProjects()
    }

    fun closeFolderPickerDialog() {
        _uiState.update { it.copy(showFolderPickerDialog = false) }
    }

    fun setPendingParentDir(path: String) {
        _uiState.update { it.copy(pendingParentDir = path) }
    }

    fun setActiveCwd(path: String) {
        val normalized = if (path.endsWith("/") && path.length > 1) path.dropLast(1) else path
        val projName = File(normalized).name.ifEmpty { "根目录" }
        _uiState.update {
            it.copy(
                activeCwd = normalized,
                activeProjectName = projName
            )
        }
        startNewSession()
        loadProjects()
    }

    fun createNewProject(
        name: String,
        parentDir: String,
        template: String = "empty",
        initGit: Boolean = true,
        createReadme: Boolean = true
    ) {
        val targetPath = File(parentDir, name).absolutePath
        viewModelScope.launch {
            apiClient.createProject(name, parentDir, template, initGit, createReadme).fold(
                onSuccess = { createdPath ->
                    val finalPath = if (createdPath.isNotEmpty()) createdPath else targetPath
                    setActiveCwd(finalPath)
                    closeNewProjectDialog()
                    loadProjects()
                },
                onFailure = {
                    val localSuccess = StorageHelper.createLocalProject(targetPath, template, createReadme)
                    if (localSuccess) {
                        setActiveCwd(targetPath)
                        closeNewProjectDialog()
                        loadProjects()
                    } else {
                        _uiState.update { it.copy(errorMessage = "创建项目失败: ${it.errorMessage}") }
                    }
                }
            )
        }
    }

    fun loadSessions() {
        viewModelScope.launch {
            apiClient.getSessions().fold(
                onSuccess = { sessionList ->
                    _uiState.update { it.copy(sessions = sessionList) }
                },
                onFailure = { err ->
                    _uiState.update { it.copy(errorMessage = "获取会话列表失败: ${err.message}") }
                }
            )
        }
    }

    fun selectSession(sessionId: String) {
        viewModelScope.launch {
            apiClient.getSessionDetail(sessionId).fold(
                onSuccess = { (title, msgs) ->
                    _uiState.update {
                        it.copy(
                            currentSessionId = sessionId,
                            currentSessionTitle = title,
                            messages = msgs,
                            errorMessage = null
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update { it.copy(errorMessage = "加载会话详情失败: ${err.message}") }
                }
            )
        }
    }

    fun startNewSession() {
        streamJob?.cancel()
        _uiState.update {
            it.copy(
                currentSessionId = null,
                currentSessionTitle = "新会话",
                messages = emptyList(),
                isGenerating = false,
                errorMessage = null
            )
        }
    }

    fun sendMessage(prompt: String) {
        val trimmed = prompt.trim()
        if (trimmed.isEmpty() || _uiState.value.isGenerating) return

        val userMsg = ChatMessage(role = "user", content = trimmed)
        val assistantMsgId = UUID.randomUUID().toString()
        val initialAssistantMsg = ChatMessage(
            id = assistantMsgId,
            role = "assistant",
            content = "",
            isThinking = true
        )

        _uiState.update {
            it.copy(
                messages = it.messages + userMsg + initialAssistantMsg,
                isGenerating = true,
                errorMessage = null
            )
        }

        val currentState = _uiState.value
        streamJob?.cancel()
        streamJob = viewModelScope.launch {
            val contentBuilder = StringBuilder()
            val thinkingBuilder = StringBuilder()
            val currentToolCalls = mutableListOf<ToolCallInfo>()

            apiClient.streamChat(
                prompt = trimmed,
                sessionId = currentState.currentSessionId,
                model = currentState.selectedModel,
                reasoningEffort = currentState.selectedReasoning,
                permissionMode = currentState.selectedPermission,
                cwd = currentState.activeCwd
            ).collect { event ->
                when (event) {
                    is ChatStreamEvent.Thinking -> {
                        thinkingBuilder.append(event.text)
                        updateAssistantMessage(assistantMsgId) { msg ->
                            msg.copy(
                                thinking = thinkingBuilder.toString(),
                                isThinking = true
                            )
                        }
                    }
                    is ChatStreamEvent.Content -> {
                        contentBuilder.append(event.text)
                        updateAssistantMessage(assistantMsgId) { msg ->
                            msg.copy(
                                content = contentBuilder.toString(),
                                isThinking = false
                            )
                        }
                    }
                    is ChatStreamEvent.ToolStart -> {
                        val tool = ToolCallInfo(
                            id = event.id.ifEmpty { UUID.randomUUID().toString() },
                            name = event.name,
                            args = event.args,
                            status = ToolStatus.RUNNING
                        )
                        currentToolCalls.add(tool)
                        updateAssistantMessage(assistantMsgId) { msg ->
                            msg.copy(
                                toolCalls = currentToolCalls.toList(),
                                isThinking = false
                            )
                        }
                    }
                    is ChatStreamEvent.ToolEnd -> {
                        val targetStatus = if (event.status.equals("failed", ignoreCase = true)) ToolStatus.FAILED else ToolStatus.SUCCESS
                        val idx = if (event.id.isNotEmpty()) {
                            currentToolCalls.indexOfLast { it.id == event.id }
                        } else {
                            currentToolCalls.indexOfLast { it.status == ToolStatus.RUNNING }
                        }
                        if (idx != -1) {
                            currentToolCalls[idx] = currentToolCalls[idx].copy(
                                result = event.result,
                                status = targetStatus
                            )
                        } else if (currentToolCalls.isNotEmpty()) {
                            val lastRunning = currentToolCalls.indexOfLast { it.status == ToolStatus.RUNNING }
                            val targetIdx = if (lastRunning != -1) lastRunning else currentToolCalls.size - 1
                            currentToolCalls[targetIdx] = currentToolCalls[targetIdx].copy(
                                result = event.result,
                                status = targetStatus
                            )
                        }
                        updateAssistantMessage(assistantMsgId) { msg ->
                            msg.copy(toolCalls = currentToolCalls.toList())
                        }
                    }
                    is ChatStreamEvent.Done -> {
                        _uiState.update { it.copy(isGenerating = false) }
                        updateAssistantMessage(assistantMsgId) { msg ->
                            msg.copy(isThinking = false)
                        }
                        loadSessions()
                        loadSubscriptionQuota()
                    }
                    is ChatStreamEvent.Error -> {
                        _uiState.update {
                            it.copy(
                                isGenerating = false,
                                errorMessage = event.message
                            )
                        }
                        updateAssistantMessage(assistantMsgId) { msg ->
                            val fallback = if (msg.content.isEmpty()) "⚠️ ${event.message}" else msg.content
                            msg.copy(content = fallback, isThinking = false)
                        }
                    }
                }
            }
        }
    }

    private fun updateAssistantMessage(id: String, transform: (ChatMessage) -> ChatMessage) {
        _uiState.update { state ->
            val updated = state.messages.map { msg ->
                if (msg.id == id) transform(msg) else msg
            }
            state.copy(messages = updated)
        }
    }

    fun cancelGeneration() {
        streamJob?.cancel()
        _uiState.update { it.copy(isGenerating = false) }
    }

    fun renameSession(sessionId: String, newTitle: String) {
        viewModelScope.launch {
            apiClient.renameSession(sessionId, newTitle).fold(
                onSuccess = {
                    loadSessions()
                    if (_uiState.value.currentSessionId == sessionId) {
                        _uiState.update { it.copy(currentSessionTitle = newTitle) }
                    }
                },
                onFailure = { err ->
                    _uiState.update { it.copy(errorMessage = "重命名失败: ${err.message}") }
                }
            )
        }
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            apiClient.deleteSession(sessionId).fold(
                onSuccess = {
                    loadSessions()
                    if (_uiState.value.currentSessionId == sessionId) {
                        startNewSession()
                    }
                },
                onFailure = { err ->
                    _uiState.update { it.copy(errorMessage = "删除会话失败: ${err.message}") }
                }
            )
        }
    }

    fun toggleArchive(sessionId: String) {
        val session = _uiState.value.sessions.find { it.sessionId == sessionId } ?: return
        viewModelScope.launch {
            apiClient.archiveSession(sessionId, !session.archived).fold(
                onSuccess = { loadSessions() },
                onFailure = { err ->
                    _uiState.update { it.copy(errorMessage = "归档操作失败: ${err.message}") }
                }
            )
        }
    }

    fun setModel(modelId: String) {
        _uiState.update { it.copy(selectedModel = modelId) }
    }

    fun setReasoning(reasoning: String) {
        _uiState.update { it.copy(selectedReasoning = reasoning) }
    }

    fun setPermission(permission: String) {
        _uiState.update { it.copy(selectedPermission = permission) }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun setShowArchived(show: Boolean) {
        _uiState.update { it.copy(showArchived = show) }
    }

    fun updateServerUrl(url: String) {
        apiClient.updateBaseUrl(url)
        _uiState.update { it.copy(serverUrl = apiClient.getBaseUrl()) }
        refreshAll()
    }

    fun clearErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun openCodeReview(scope: String = "auto") {
        _uiState.update {
            it.copy(
                showCodeReviewDialog = true,
                codeReviewState = it.codeReviewState.copy(isLoading = true, error = null)
            )
        }
        loadGitDiff(scope)
    }

    fun closeCodeReview() {
        _uiState.update { it.copy(showCodeReviewDialog = false) }
    }

    fun toggleActivityTimeline() {
        _uiState.update { it.copy(showActivityTimeline = !it.showActivityTimeline) }
    }

    fun selectToolCall(toolId: String?) {
        _uiState.update {
            it.copy(
                selectedToolCallId = if (it.selectedToolCallId == toolId) null else toolId,
                showActivityTimeline = true
            )
        }
    }

    fun loadGitDiff(scope: String = "auto") {
        val cwd = _uiState.value.activeCwd
        viewModelScope.launch {
            apiClient.getGitDiff(cwd, scope).fold(
                onSuccess = { diffResp ->
                    val firstFile = diffResp.files.firstOrNull()?.path
                    _uiState.update { state ->
                        state.copy(
                            codeReviewState = state.codeReviewState.copy(
                                isGit = diffResp.isGit,
                                cwd = diffResp.cwd,
                                branch = diffResp.branch,
                                commitId = diffResp.commitId,
                                commitMsg = diffResp.commitMsg,
                                hasUncommitted = diffResp.hasUncommitted,
                                scope = diffResp.scope,
                                statSummary = diffResp.statSummary,
                                files = diffResp.files,
                                selectedFilePath = firstFile,
                                isLoading = false,
                                error = null
                            )
                        )
                    }
                    if (firstFile != null) {
                        loadGitFileContent(firstFile)
                    }
                },
                onFailure = { err ->
                    _uiState.update { state ->
                        state.copy(
                            codeReviewState = state.codeReviewState.copy(
                                isLoading = false,
                                error = err.message
                            )
                        )
                    }
                }
            )
        }
    }

    fun selectDiffFile(path: String) {
        _uiState.update { state ->
            state.copy(
                codeReviewState = state.codeReviewState.copy(selectedFilePath = path)
            )
        }
        loadGitFileContent(path)
    }

    fun loadGitFileContent(path: String) {
        val cwd = _uiState.value.activeCwd
        viewModelScope.launch {
            apiClient.getGitFile(cwd, path).fold(
                onSuccess = { content ->
                    _uiState.update { state ->
                        state.copy(
                            codeReviewState = state.codeReviewState.copy(selectedFileContent = content)
                        )
                    }
                },
                onFailure = {
                    _uiState.update { state ->
                        state.copy(
                            codeReviewState = state.codeReviewState.copy(selectedFileContent = null)
                        )
                    }
                }
            )
        }
    }

    fun revertDiffFile(path: String) {
        val cwd = _uiState.value.activeCwd
        viewModelScope.launch {
            apiClient.revertGitChange(cwd, path).fold(
                onSuccess = {
                    loadGitDiff(_uiState.value.codeReviewState.scope)
                },
                onFailure = { err ->
                    _uiState.update { state ->
                        state.copy(
                            codeReviewState = state.codeReviewState.copy(error = "撤销失败: ${err.message}")
                        )
                    }
                }
            )
        }
    }

    fun startAiCodeReview() {
        val reviewScope = _uiState.value.codeReviewState.scope
        val scopeDesc = if (reviewScope == "working") "当前工作区未提交改动" else "最新提交内容"
        val prompt = """/review 请针对项目（${_uiState.value.activeProjectName}）的${scopeDesc}进行专业技术代码审查：
重点检查：
1. 【架构设计与模块分层】：是否存在反模式、过度设计或破坏现有分层？
2. 【逻辑漏洞与边界条件】：是否存在空指针、越界、并发安全或状态不同步隐患？
3. 【代码异味与复杂度】：是否存在冗余分支、死代码、或可借由 Code Judo 极简化的结构？
4. 【优化与整改方案】：请给出具体的代码片段修改建议与重构方案。"""
        
        _uiState.update { state ->
            state.copy(
                codeReviewState = state.codeReviewState.copy(
                    isReviewing = true,
                    reviewReport = "正在启动 Grok 智能代码审查，正在深度分析变更并对比语法树与逻辑流..."
                )
            )
        }
        
        sendMessage(prompt)
    }

    fun previewHtmlInChrome(context: Context, html: String, title: String = "Web 页面预览") {
        viewModelScope.launch {
            apiClient.createWebPreview(html, title).fold(
                onSuccess = { previewUrl ->
                    ChromeHelper.openUrl(context, previewUrl)
                },
                onFailure = { err ->
                    _uiState.update { state ->
                        state.copy(errorMessage = "生成网页预览失败: ${err.message}")
                    }
                }
            )
        }
    }
}
