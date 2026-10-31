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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

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
    val activeCwd: String = "/storage/emulated/10/Codes",
    val errorMessage: String? = null
)

class GrokWorkbenchViewModel(
    private val apiClient: GrokApiClient = GrokApiClient()
) : ViewModel() {

    private val _uiState = MutableStateFlow(WorkbenchUiState())
    val uiState: StateFlow<WorkbenchUiState> = _uiState.asStateFlow()

    private var streamJob: Job? = null

    init {
        refreshAll()
    }

    fun refreshAll() {
        checkStatus()
        loadSessions()
    }

    fun checkStatus() {
        viewModelScope.launch {
            apiClient.getStatus().fold(
                onSuccess = { status ->
                    _uiState.update {
                        it.copy(
                            serverStatus = status,
                            serverConnected = true,
                            activeCwd = status.cwd,
                            errorMessage = null
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            serverConnected = false,
                            errorMessage = "后端服务未连接 (127.0.0.1:8088): ${err.message}"
                        )
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
                        val idx = currentToolCalls.indexOfLast { it.name == event.name }
                        if (idx != -1) {
                            currentToolCalls[idx] = currentToolCalls[idx].copy(
                                result = event.result,
                                status = ToolStatus.SUCCESS
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
}
