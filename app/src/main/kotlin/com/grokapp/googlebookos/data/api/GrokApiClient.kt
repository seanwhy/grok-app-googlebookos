package com.grokapp.googlebookos.data.api

import com.grokapp.googlebookos.data.model.ChatMessage
import com.grokapp.googlebookos.data.model.ChatStreamEvent
import com.grokapp.googlebookos.data.model.ModelOption
import com.grokapp.googlebookos.data.model.PermissionOption
import com.grokapp.googlebookos.data.model.ProjectItem
import com.grokapp.googlebookos.data.model.FsDirectoryItem
import com.grokapp.googlebookos.data.model.FsListResponse
import com.grokapp.googlebookos.data.model.ReasoningOption
import com.grokapp.googlebookos.data.model.ServerStatus
import com.grokapp.googlebookos.data.model.SessionSummary
import com.grokapp.googlebookos.data.model.SubscriptionQuota
import com.grokapp.googlebookos.data.model.ProductUsageItem
import com.grokapp.googlebookos.data.model.GitDiffFile
import com.grokapp.googlebookos.data.model.GitDiffResponse
import com.grokapp.googlebookos.data.model.ToolCallInfo
import com.grokapp.googlebookos.data.model.ToolStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class GrokApiClient(private var baseUrl: String = "http://127.0.0.1:8088") {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.MINUTES)
        .writeTimeout(10, TimeUnit.MINUTES)
        .build()

    private val sseFactory = EventSources.createFactory(client)

    fun updateBaseUrl(url: String) {
        baseUrl = if (url.endsWith("/")) url.dropLast(1) else url
    }

    fun getBaseUrl(): String = baseUrl

    suspend fun getStatus(): Result<ServerStatus> = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("$baseUrl/api/status")
                .get()
                .build()

            client.newCall(req).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException("Server error: ${response.code}"))
                }
                val bodyStr = response.body?.string() ?: "{}"
                val json = JSONObject(bodyStr)

                val models = mutableListOf<ModelOption>()
                val modelsArr = json.optJSONArray("models") ?: JSONArray()
                for (i in 0 until modelsArr.length()) {
                    val m = modelsArr.getJSONObject(i)
                    models.add(
                        ModelOption(
                            id = m.optString("id"),
                            name = m.optString("name"),
                            desc = m.optString("desc")
                        )
                    )
                }

                val reasoning = mutableListOf<ReasoningOption>()
                val reasoningArr = json.optJSONArray("reasoning_efforts") ?: JSONArray()
                for (i in 0 until reasoningArr.length()) {
                    val r = reasoningArr.getJSONObject(i)
                    reasoning.add(
                        ReasoningOption(
                            id = r.optString("id"),
                            name = r.optString("name"),
                            desc = r.optString("desc")
                        )
                    )
                }

                val permissions = mutableListOf<PermissionOption>()
                val permArr = json.optJSONArray("permission_modes") ?: JSONArray()
                for (i in 0 until permArr.length()) {
                    val p = permArr.getJSONObject(i)
                    permissions.add(
                        PermissionOption(
                            id = p.optString("id"),
                            name = p.optString("name"),
                            desc = p.optString("desc")
                        )
                    )
                }

                val status = ServerStatus(
                    status = json.optString("status", "ok"),
                    proxy = json.optString("proxy").takeIf { it.isNotEmpty() },
                    proxyConnected = json.optBoolean("proxy_connected", false),
                    models = models,
                    reasoningEfforts = reasoning,
                    permissionModes = permissions,
                    cwd = json.optString("cwd", "/storage/emulated/10/Codes")
                )
                Result.success(status)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getSubscriptionQuota(): Result<SubscriptionQuota> = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("$baseUrl/api/subscription")
                .get()
                .build()

            client.newCall(req).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException("Server error: ${response.code}"))
                }
                val bodyStr = response.body?.string() ?: "{}"
                val json = JSONObject(bodyStr)

                val products = mutableListOf<ProductUsageItem>()
                val prodArr = json.optJSONArray("product_usage")
                if (prodArr != null) {
                    for (i in 0 until prodArr.length()) {
                        val p = prodArr.getJSONObject(i)
                        products.add(
                            ProductUsageItem(
                                product = p.optString("product", ""),
                                usagePercent = p.optDouble("usage_percent", 0.0).toFloat()
                            )
                        )
                    }
                }

                val quota = SubscriptionQuota(
                    tier = json.optString("tier", "SuperGrok"),
                    email = json.optString("email", ""),
                    creditUsagePercent = json.optDouble("credit_usage_percent", 0.0).toFloat(),
                    creditRemainingPercent = json.optDouble("credit_remaining_percent", 100.0).toFloat(),
                    periodType = json.optString("period_type", "USAGE_PERIOD_TYPE_WEEKLY"),
                    periodStart = json.optString("period_start", ""),
                    periodEnd = json.optString("period_end", ""),
                    productUsage = products,
                    updatedAt = json.optString("updated_at", ""),
                    isLoading = false
                )
                Result.success(quota)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getSessions(): Result<List<SessionSummary>> = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("$baseUrl/api/sessions")
                .get()
                .build()

            client.newCall(req).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException("HTTP ${response.code}"))
                }
                val bodyStr = response.body?.string() ?: "{}"
                val json = JSONObject(bodyStr)
                val list = mutableListOf<SessionSummary>()
                val arr = json.optJSONArray("sessions") ?: JSONArray()
                for (i in 0 until arr.length()) {
                    val item = arr.getJSONObject(i)
                    list.add(
                        SessionSummary(
                            sessionId = item.optString("session_id"),
                            title = item.optString("title"),
                            updatedAt = item.optLong("updated_at", 0),
                            lastPromptTime = item.optString("last_prompt_time", ""),
                            timestampStr = item.optString("timestamp_str", ""),
                            archived = item.optBoolean("archived", false),
                            model = item.optString("model").takeIf { it.isNotEmpty() }
                        )
                    )
                }
                Result.success(list)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getSessionDetail(sessionId: String): Result<Pair<String, List<ChatMessage>>> = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("$baseUrl/api/session/$sessionId")
                .get()
                .build()

            client.newCall(req).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException("HTTP ${response.code}"))
                }
                val bodyStr = response.body?.string() ?: "{}"
                val json = JSONObject(bodyStr)
                val title = json.optString("title", "会话")
                val messages = mutableListOf<ChatMessage>()
                val arr = json.optJSONArray("messages") ?: JSONArray()
                for (i in 0 until arr.length()) {
                    val m = arr.getJSONObject(i)
                    val role = m.optString("role", "user")
                    val content = m.optString("content", "")
                    val thinking = m.optString("thinking").takeIf { it.isNotEmpty() }
                    val toolCalls = mutableListOf<ToolCallInfo>()
                    val tcArr = m.optJSONArray("tool_calls")
                    if (tcArr != null) {
                        for (j in 0 until tcArr.length()) {
                            val tc = tcArr.getJSONObject(j)
                            val statusStr = tc.optString("status", "SUCCESS")
                            toolCalls.add(
                                ToolCallInfo(
                                    id = tc.optString("id", java.util.UUID.randomUUID().toString()),
                                    name = tc.optString("name", "工具"),
                                    args = tc.optString("args", ""),
                                    result = tc.optString("result").takeIf { it.isNotEmpty() },
                                    status = if (statusStr.equals("FAILED", ignoreCase = true)) ToolStatus.FAILED else ToolStatus.SUCCESS
                                )
                            )
                        }
                    }
                    messages.add(
                        ChatMessage(
                            role = role,
                            content = content,
                            thinking = thinking,
                            toolCalls = toolCalls
                        )
                    )
                }
                Result.success(Pair(title, messages))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun renameSession(sessionId: String, newTitle: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject().apply {
                put("session_id", sessionId)
                put("title", newTitle)
            }.toString()

            val req = Request.Builder()
                .url("$baseUrl/api/session/rename")
                .post(payload.toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(req).execute().use { response ->
                Result.success(response.isSuccessful)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteSession(sessionId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject().apply {
                put("session_id", sessionId)
            }.toString()

            val req = Request.Builder()
                .url("$baseUrl/api/session/delete")
                .post(payload.toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(req).execute().use { response ->
                Result.success(response.isSuccessful)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun archiveSession(sessionId: String, archived: Boolean): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject().apply {
                put("session_id", sessionId)
                put("archived", archived)
            }.toString()

            val req = Request.Builder()
                .url("$baseUrl/api/session/archive")
                .post(payload.toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(req).execute().use { response ->
                Result.success(response.isSuccessful)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun streamChat(
        prompt: String,
        sessionId: String?,
        model: String,
        reasoningEffort: String,
        permissionMode: String,
        cwd: String
    ): Flow<ChatStreamEvent> = callbackFlow {
        val payload = JSONObject().apply {
            put("prompt", prompt)
            if (!sessionId.isNullOrEmpty()) put("session_id", sessionId)
            put("model", model)
            put("reasoning_effort", reasoningEffort)
            put("permission_mode", permissionMode)
            put("cwd", cwd)
        }.toString()

        val req = Request.Builder()
            .url("$baseUrl/api/chat")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()

        var eventSource: EventSource? = null

        val listener = object : EventSourceListener() {
            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                try {
                    val json = JSONObject(data)
                    when (json.optString("type")) {
                        "chunk" -> {
                            val chunkText = json.optString("text")
                            trySend(ChatStreamEvent.Content(chunkText))
                        }
                        "thinking" -> {
                            val thinkText = json.optString("text")
                            trySend(ChatStreamEvent.Thinking(thinkText))
                        }
                        "tool_call" -> {
                            val toolId = json.optString("id", "")
                            val toolTitle = json.optString("title").ifEmpty { json.optString("tool", "工具") }
                            val args = json.optString("args", "")
                            trySend(ChatStreamEvent.ToolStart(id = toolId, name = toolTitle, args = args))
                        }
                        "tool_result" -> {
                            val toolId = json.optString("id", "")
                            val statusStr = json.optString("status", "success")
                            val result = json.optString("result", "")
                            trySend(ChatStreamEvent.ToolEnd(id = toolId, result = result, status = statusStr))
                        }
                        "end", "done" -> {
                            trySend(ChatStreamEvent.Done(json.optString("session_id")))
                            close()
                        }
                        "error" -> {
                            trySend(ChatStreamEvent.Error(json.optString("message", "Unknown error")))
                            close()
                        }
                    }
                } catch (e: Exception) {
                    trySend(ChatStreamEvent.Content(data))
                }
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                val msg = t?.localizedMessage ?: "Connection failure (${response?.code ?: "Unknown"})"
                trySend(ChatStreamEvent.Error(msg))
                close(t)
            }

            override fun onClosed(eventSource: EventSource) {
                close()
            }
        }

        eventSource = sseFactory.newEventSource(req, listener)

        awaitClose {
            eventSource.cancel()
        }
    }.flowOn(Dispatchers.IO)

    suspend fun getProjects(): Result<List<ProjectItem>> = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url("$baseUrl/api/projects").get().build()
            client.newCall(req).execute().use { response ->
                if (!response.isSuccessful) return@withContext Result.failure(IOException("Server error: ${response.code}"))
                val bodyStr = response.body?.string() ?: "{}"
                val json = JSONObject(bodyStr)
                val projects = mutableListOf<ProjectItem>()
                val arr = json.optJSONArray("projects") ?: JSONArray()
                for (i in 0 until arr.length()) {
                    val p = arr.getJSONObject(i)
                    projects.add(
                        ProjectItem(
                            name = p.optString("name"),
                            path = p.optString("path"),
                            isGit = p.optBoolean("is_git", false),
                            lastModified = p.optLong("last_modified", 0)
                        )
                    )
                }
                Result.success(projects)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getFsList(path: String): Result<FsListResponse> = withContext(Dispatchers.IO) {
        try {
            val encodedPath = java.net.URLEncoder.encode(path, "UTF-8")
            val req = Request.Builder().url("$baseUrl/api/fs/list?path=$encodedPath").get().build()
            client.newCall(req).execute().use { response ->
                if (!response.isSuccessful) return@withContext Result.failure(IOException("Server error: ${response.code}"))
                val bodyStr = response.body?.string() ?: "{}"
                val json = JSONObject(bodyStr)
                val dirs = mutableListOf<FsDirectoryItem>()
                val arr = json.optJSONArray("directories") ?: JSONArray()
                for (i in 0 until arr.length()) {
                    val d = arr.getJSONObject(i)
                    dirs.add(
                        FsDirectoryItem(
                            name = d.optString("name"),
                            path = d.optString("path"),
                            isHidden = d.optBoolean("is_hidden", false),
                            isGit = d.optBoolean("is_git", false)
                        )
                    )
                }
                val result = FsListResponse(
                    currentPath = json.optString("current_path", path),
                    parentPath = json.optString("parent_path").takeIf { it.isNotEmpty() && it != "null" },
                    canCreate = json.optBoolean("can_create", true),
                    directories = dirs
                )
                Result.success(result)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createProject(
        name: String,
        parentDir: String,
        template: String = "empty",
        initGit: Boolean = true,
        createReadme: Boolean = true
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val jsonBody = JSONObject().apply {
                put("name", name)
                put("parent_dir", parentDir)
                put("template", template)
                put("init_git", initGit)
                put("create_readme", createReadme)
            }
            val req = Request.Builder()
                .url("$baseUrl/api/project/create")
                .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(req).execute().use { response ->
                if (!response.isSuccessful) return@withContext Result.failure(IOException("Server error: ${response.code}"))
                val bodyStr = response.body?.string() ?: "{}"
                val json = JSONObject(bodyStr)
                val targetPath = json.optString("path")
                Result.success(targetPath)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getGitDiff(cwd: String, scope: String = "auto"): Result<GitDiffResponse> = withContext(Dispatchers.IO) {
        try {
            val encodedCwd = java.net.URLEncoder.encode(cwd, "UTF-8")
            val req = Request.Builder()
                .url("$baseUrl/api/git/diff?cwd=$encodedCwd&scope=$scope")
                .get()
                .build()

            client.newCall(req).execute().use { response ->
                if (!response.isSuccessful) return@withContext Result.failure(IOException("Server error: ${response.code}"))
                val bodyStr = response.body?.string() ?: "{}"
                val json = JSONObject(bodyStr)

                val fileList = mutableListOf<GitDiffFile>()
                val filesArr = json.optJSONArray("files")
                if (filesArr != null) {
                    for (i in 0 until filesArr.length()) {
                        val fo = filesArr.getJSONObject(i)
                        fileList.add(
                            GitDiffFile(
                                path = fo.optString("path"),
                                name = fo.optString("name"),
                                status = fo.optString("status", "M"),
                                statusLabel = fo.optString("status_label", "修改"),
                                additions = fo.optInt("additions", 0),
                                deletions = fo.optInt("deletions", 0),
                                diff = fo.optString("diff", "")
                            )
                        )
                    }
                }

                val diffResp = GitDiffResponse(
                    status = json.optString("status", "ok"),
                    isGit = json.optBoolean("is_git", false),
                    cwd = json.optString("cwd", cwd),
                    branch = json.optString("branch", ""),
                    commitId = json.optString("commit_id", ""),
                    commitMsg = json.optString("commit_msg", ""),
                    hasUncommitted = json.optBoolean("has_uncommitted", false),
                    scope = json.optString("scope", scope),
                    statSummary = json.optString("stat_summary", ""),
                    files = fileList
                )
                Result.success(diffResp)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getGitFile(cwd: String, path: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val encodedCwd = java.net.URLEncoder.encode(cwd, "UTF-8")
            val encodedPath = java.net.URLEncoder.encode(path, "UTF-8")
            val req = Request.Builder()
                .url("$baseUrl/api/git/file?cwd=$encodedCwd&path=$encodedPath")
                .get()
                .build()

            client.newCall(req).execute().use { response ->
                if (!response.isSuccessful) return@withContext Result.failure(IOException("Server error: ${response.code}"))
                val bodyStr = response.body?.string() ?: "{}"
                val json = JSONObject(bodyStr)
                Result.success(json.optString("content", ""))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun revertGitChange(cwd: String, path: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val jsonBody = JSONObject().apply {
                put("cwd", cwd)
                put("path", path)
            }
            val req = Request.Builder()
                .url("$baseUrl/api/git/revert")
                .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(req).execute().use { response ->
                if (!response.isSuccessful) return@withContext Result.failure(IOException("Server error: ${response.code}"))
                Result.success(true)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createWebPreview(html: String, title: String = "Web 页面预览"): Result<String> = withContext(Dispatchers.IO) {
        try {
            val jsonBody = JSONObject().apply {
                put("html", html)
                put("title", title)
            }
            val req = Request.Builder()
                .url("$baseUrl/api/web/preview")
                .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(req).execute().use { response ->
                if (!response.isSuccessful) return@withContext Result.failure(IOException("Server error: ${response.code}"))
                val bodyStr = response.body?.string() ?: "{}"
                val json = JSONObject(bodyStr)
                val url = json.optString("url", "")
                if (url.isNotEmpty()) {
                    Result.success(url)
                } else {
                    Result.failure(IOException("No preview url returned"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

