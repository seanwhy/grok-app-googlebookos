package com.grokapp.googlebookos.ui.workbench

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.grokapp.googlebookos.data.model.ChatMessage
import com.grokapp.googlebookos.data.model.SessionSummary
import com.grokapp.googlebookos.data.model.ToolCallInfo
import com.grokapp.googlebookos.data.model.ToolStatus
import kotlinx.coroutines.launch

@Composable
fun GrokWorkbenchScreen(
    modifier: Modifier = Modifier,
    viewModel: GrokWorkbenchViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showServerDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf<String?>(null) }
    var renameInputText by remember { mutableStateOf("") }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isWideScreen = maxWidth >= 720.dp

        if (isWideScreen) {
            Row(modifier = Modifier.fillMaxSize()) {
                // Left Column: Sessions & Controls Sidebar
                Surface(
                    modifier = Modifier
                        .width(320.dp)
                        .fillMaxHeight(),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    tonalElevation = 1.dp
                ) {
                    SidebarContent(
                        uiState = uiState,
                        onNewSession = { viewModel.startNewSession() },
                        onSelectSession = { viewModel.selectSession(it) },
                        onRenameSession = { sid, currentTitle ->
                            renameInputText = currentTitle
                            showRenameDialog = sid
                        },
                        onDeleteSession = { viewModel.deleteSession(it) },
                        onToggleArchive = { viewModel.toggleArchive(it) },
                        onSearchChange = { viewModel.setSearchQuery(it) },
                        onShowArchivedToggle = { viewModel.setShowArchived(it) },
                        onOpenSettings = { showServerDialog = true }
                    )
                }

                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // Right Column: Main Chat & Agent Timeline
                MainChatArea(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    uiState = uiState,
                    isWideScreen = true,
                    onOpenDrawer = {},
                    onSendMessage = { viewModel.sendMessage(it) },
                    onCancelGeneration = { viewModel.cancelGeneration() },
                    onModelSelect = { viewModel.setModel(it) },
                    onReasoningSelect = { viewModel.setReasoning(it) },
                    onPermissionSelect = { viewModel.setPermission(it) },
                    onOpenSettings = { showServerDialog = true },
                    onRenameCurrent = {
                        uiState.currentSessionId?.let { sid ->
                            renameInputText = uiState.currentSessionTitle
                            showRenameDialog = sid
                        }
                    }
                )
            }
        } else {
            // Narrow Screen: Modal Navigation Drawer
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    ModalDrawerSheet(modifier = Modifier.width(300.dp)) {
                        SidebarContent(
                            uiState = uiState,
                            onNewSession = {
                                viewModel.startNewSession()
                                scope.launch { drawerState.close() }
                            },
                            onSelectSession = {
                                viewModel.selectSession(it)
                                scope.launch { drawerState.close() }
                            },
                            onRenameSession = { sid, currentTitle ->
                                renameInputText = currentTitle
                                showRenameDialog = sid
                            },
                            onDeleteSession = { viewModel.deleteSession(it) },
                            onToggleArchive = { viewModel.toggleArchive(it) },
                            onSearchChange = { viewModel.setSearchQuery(it) },
                            onShowArchivedToggle = { viewModel.setShowArchived(it) },
                            onOpenSettings = { showServerDialog = true }
                        )
                    }
                }
            ) {
                MainChatArea(
                    modifier = Modifier.fillMaxSize(),
                    uiState = uiState,
                    isWideScreen = false,
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                    onSendMessage = { viewModel.sendMessage(it) },
                    onCancelGeneration = { viewModel.cancelGeneration() },
                    onModelSelect = { viewModel.setModel(it) },
                    onReasoningSelect = { viewModel.setReasoning(it) },
                    onPermissionSelect = { viewModel.setPermission(it) },
                    onOpenSettings = { showServerDialog = true },
                    onRenameCurrent = {
                        uiState.currentSessionId?.let { sid ->
                            renameInputText = uiState.currentSessionTitle
                            showRenameDialog = sid
                        }
                    }
                )
            }
        }
    }

    // Rename Session Dialog
    if (showRenameDialog != null) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = null },
            title = { Text("重命名会话") },
            text = {
                OutlinedTextField(
                    value = renameInputText,
                    onValueChange = { renameInputText = it },
                    label = { Text("会话标题") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRenameDialog?.let { sid ->
                            if (renameInputText.isNotBlank()) {
                                viewModel.renameSession(sid, renameInputText.trim())
                            }
                        }
                        showRenameDialog = null
                    }
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = null }) {
                    Text("取消")
                }
            }
        )
    }

    // Server Settings Dialog
    if (showServerDialog) {
        var urlInput by remember { mutableStateOf(uiState.serverUrl) }
        AlertDialog(
            onDismissRequest = { showServerDialog = false },
            title = { Text("后端服务设置") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "连接到运行在 Linux VM 中的 grok-web 后端服务：",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        label = { Text("服务地址 (URL)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "当前代理: ${uiState.serverStatus?.proxy ?: "未启用"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (urlInput.isNotBlank()) {
                            viewModel.updateServerUrl(urlInput.trim())
                        }
                        showServerDialog = false
                    }
                ) {
                    Text("连接并刷新")
                }
            },
            dismissButton = {
                TextButton(onClick = { showServerDialog = false }) {
                    Text("关闭")
                }
            }
        )
    }
}

@Composable
fun SidebarContent(
    uiState: WorkbenchUiState,
    onNewSession: () -> Unit,
    onSelectSession: (String) -> Unit,
    onRenameSession: (String, String) -> Unit,
    onDeleteSession: (String) -> Unit,
    onToggleArchive: (String) -> Unit,
    onSearchChange: (String) -> Unit,
    onShowArchivedToggle: (Boolean) -> Unit,
    onOpenSettings: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        // App Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = "Grok Logo",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Grok Workbench",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (uiState.serverConnected) Color(0xFF10B981) else Color(0xFFEF4444))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (uiState.serverConnected) "127.0.0.1:8088" else "未连接",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Default.Settings, contentDescription = "Settings")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // New Session Button
        Button(
            onClick = onNewSession,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("新建会话", fontWeight = FontWeight.SemiBold)
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Search Bar
        OutlinedTextField(
            value = uiState.searchQuery,
            onValueChange = onSearchChange,
            placeholder = { Text("搜索会话历史...", fontSize = 13.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedContainerColor = MaterialTheme.colorScheme.surface
            )
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Filter: Active / Archived
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (uiState.showArchived) "已归档会话" else "历史会话",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(
                onClick = { onShowArchivedToggle(!uiState.showArchived) },
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
            ) {
                Text(
                    text = if (uiState.showArchived) "看活跃会话" else "查看已归档",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }

        // Sessions List
        val filtered = uiState.sessions.filter {
            it.archived == uiState.showArchived &&
                (uiState.searchQuery.isBlank() || it.title.contains(uiState.searchQuery, ignoreCase = true))
        }

        if (filtered.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (uiState.showArchived) "暂无归档会话" else "暂无历史会话",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(filtered, key = { it.sessionId }) { session ->
                    SessionItemRow(
                        session = session,
                        isSelected = session.sessionId == uiState.currentSessionId,
                        onClick = { onSelectSession(session.sessionId) },
                        onRename = { onRenameSession(session.sessionId, session.title) },
                        onDelete = { onDeleteSession(session.sessionId) },
                        onToggleArchive = { onToggleArchive(session.sessionId) }
                    )
                }
            }
        }
    }
}

@Composable
fun SessionItemRow(
    session: SessionSummary,
    isSelected: Boolean,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onToggleArchive: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() },
        color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = session.title.ifBlank { "会话 " + session.sessionId.take(8) },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (session.timestampStr.isNotBlank() || session.model != null) {
                    Text(
                        text = "${session.model ?: "Grok"} · ${session.timestampStr.take(16)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }

            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Menu", modifier = Modifier.size(16.dp))
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("重命名") },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onRename()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(if (session.archived) "取消归档" else "归档") },
                        leadingIcon = { Icon(Icons.Default.Archive, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onToggleArchive()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("删除", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainChatArea(
    modifier: Modifier = Modifier,
    uiState: WorkbenchUiState,
    isWideScreen: Boolean,
    onOpenDrawer: () -> Unit,
    onSendMessage: (String) -> Unit,
    onCancelGeneration: () -> Unit,
    onModelSelect: (String) -> Unit,
    onReasoningSelect: (String) -> Unit,
    onPermissionSelect: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onRenameCurrent: () -> Unit
) {
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Auto-scroll to bottom on new messages
    LaunchedEffect(uiState.messages.size, uiState.messages.lastOrNull()?.content?.length) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = uiState.currentSessionTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (uiState.currentSessionId != null) {
                            IconButton(onClick = onRenameCurrent, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Edit, contentDescription = "Rename", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                },
                navigationIcon = {
                    if (!isWideScreen) {
                        IconButton(onClick = onOpenDrawer) {
                            Icon(Icons.Default.Menu, contentDescription = "Drawer")
                        }
                    }
                },
                actions = {
                    // Badge: Model & Reasoning
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${uiState.selectedModel} (${uiState.selectedReasoning})",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Outlined.Dns, contentDescription = "Server Status")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            ComposerBar(
                inputText = inputText,
                onInputTextChange = { inputText = it },
                isGenerating = uiState.isGenerating,
                selectedModel = uiState.selectedModel,
                selectedReasoning = uiState.selectedReasoning,
                selectedPermission = uiState.selectedPermission,
                onModelSelect = onModelSelect,
                onReasoningSelect = onReasoningSelect,
                onPermissionSelect = onPermissionSelect,
                onSend = {
                    if (inputText.isNotBlank()) {
                        onSendMessage(inputText)
                        inputText = ""
                    }
                },
                onCancel = onCancelGeneration
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (!uiState.serverConnected) {
                ServerOfflineBanner(context = LocalContext.current)
            }
            if (uiState.messages.isEmpty()) {
                WelcomeHero(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    onQuickPrompt = { prompt ->
                        inputText = prompt
                    }
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item { Spacer(modifier = Modifier.height(8.dp)) }
                    items(uiState.messages, key = { it.id }) { message ->
                        ChatMessageBubble(message = message)
                    }
                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }
        }
    }
}

@Composable
fun ServerOfflineBanner(context: Context) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CloudOff, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        "后端 Agent 服务未连接 (127.0.0.1:8088)",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        "如果虚拟机尚未启动，点击启动终端将自动激活后台服务",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = { launchTerminalApp(context) },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("启动终端", fontSize = 12.sp)
            }
        }
    }
}

fun launchTerminalApp(context: Context) {
    val candidates = listOf(
        "com.android.virtualization.terminal",
        "com.google.android.terminal",
        "org.chromium.arc.terminal",
        "com.termux"
    )
    for (pkg in candidates) {
        val intent = context.packageManager.getLaunchIntentForPackage(pkg)
        if (intent != null) {
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            return
        }
    }
    Toast.makeText(context, "请在系统应用列表中启动 Terminal 终端", Toast.LENGTH_LONG).show()
}

@Composable
fun WelcomeHero(
    modifier: Modifier = Modifier,
    onQuickPrompt: (String) -> Unit
) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Grok Build Agent Workbench",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Googlebook OS 专属前端客户端 · 深度思考与全自动代码开发",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(32.dp))

        // Quick suggestions
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SuggestionChip(
                onClick = { onQuickPrompt("检查当前项目代码架构并给出优化建议") },
                label = { Text("项目分析与优化") }
            )
            SuggestionChip(
                onClick = { onQuickPrompt("为当前项目编写单元测试用例") },
                label = { Text("生成单元测试") }
            )
            SuggestionChip(
                onClick = { onQuickPrompt("重构当前模块为模块化结构") },
                label = { Text("代码重构") }
            )
        }
    }
}

@Composable
fun ChatMessageBubble(message: ChatMessage) {
    val isUser = message.role == "user"

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Terminal,
                    contentDescription = "Grok",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
        }

        Column(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxWidth(if (isUser) 0.85f else 1f),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            // Thinking Accordion Block
            if (!message.thinking.isNullOrBlank() || message.isThinking) {
                ThinkingAccordion(
                    thinking = message.thinking ?: "",
                    isThinking = message.isThinking
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Tool Execution Cards
            if (message.toolCalls.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    message.toolCalls.forEach { tool ->
                        ToolCallCard(tool = tool)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Message Bubble Body
            if (message.content.isNotBlank() || (message.thinking.isNullOrBlank() && !message.isThinking)) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                ) {
                    Box(modifier = Modifier.padding(14.dp)) {
                        MarkdownMessageContent(
                            text = message.content.ifBlank { "..." },
                            isUser = isUser
                        )
                    }
                }
            }
        }

        if (isUser) {
            Spacer(modifier = Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.tertiaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = "User",
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun ThinkingAccordion(
    thinking: String,
    isThinking: Boolean
) {
    var expanded by remember { mutableStateOf(isThinking) }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isThinking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "正在深度思考...",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    } else {
                        Icon(
                            Icons.Outlined.Psychology,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "深度思考过程",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = "Toggle",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(6.dp))
                    SelectionContainer {
                        Text(
                            text = thinking.ifBlank { "思考分析中..." },
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ToolCallCard(tool: ToolCallInfo) {
    var expanded by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Code,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "工具: ${tool.name}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = when (tool.status) {
                            ToolStatus.RUNNING -> "执行中"
                            ToolStatus.SUCCESS -> "完成"
                            ToolStatus.FAILED -> "失败"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = when (tool.status) {
                            ToolStatus.RUNNING -> MaterialTheme.colorScheme.primary
                            ToolStatus.SUCCESS -> Color(0xFF10B981)
                            ToolStatus.FAILED -> MaterialTheme.colorScheme.error
                        }
                    )
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 6.dp)) {
                    if (tool.args.isNotBlank()) {
                        Text("参数: ${tool.args}", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                    }
                    if (!tool.result.isNullOrBlank()) {
                        Text("输出: ${tool.result}", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
    }
}

@Composable
fun MarkdownMessageContent(text: String, isUser: Boolean) {
    val context = LocalContext.current
    val chunks = remember(text) { parseCodeBlocks(text) }

    SelectionContainer {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            chunks.forEach { chunk ->
                when (chunk) {
                    is ContentChunk.Text -> {
                        Text(
                            text = chunk.content,
                            style = MaterialTheme.typography.bodyMedium,
                            lineHeight = 22.sp
                        )
                    }
                    is ContentChunk.CodeBlock -> {
                        CodeBlockView(
                            lang = chunk.lang,
                            code = chunk.code,
                            onCopy = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Code", chunk.code)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "代码已复制到剪贴板", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }
        }
    }
}

sealed class ContentChunk {
    data class Text(val content: String) : ContentChunk()
    data class CodeBlock(val lang: String, val code: String) : ContentChunk()
}

fun parseCodeBlocks(raw: String): List<ContentChunk> {
    val chunks = mutableListOf<ContentChunk>()
    val regex = "```(\\w*)\\n?([\\s\\S]*?)```".toRegex()
    var lastIndex = 0

    for (match in regex.findAll(raw)) {
        if (match.range.first > lastIndex) {
            val plain = raw.substring(lastIndex, match.range.first).trim()
            if (plain.isNotEmpty()) chunks.add(ContentChunk.Text(plain))
        }
        val lang = match.groupValues[1].ifBlank { "code" }
        val code = match.groupValues[2].trimEnd()
        chunks.add(ContentChunk.CodeBlock(lang, code))
        lastIndex = match.range.last + 1
    }

    if (lastIndex < raw.length) {
        val remaining = raw.substring(lastIndex).trim()
        if (remaining.isNotEmpty()) chunks.add(ContentChunk.Text(remaining))
    }

    if (chunks.isEmpty() && raw.isNotEmpty()) {
        chunks.add(ContentChunk.Text(raw))
    }
    return chunks
}

@Composable
fun CodeBlockView(
    lang: String,
    code: String,
    onCopy: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF1E1E1E),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF2D2D2D))
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = lang.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.LightGray,
                    fontWeight = FontWeight.Bold
                )
                TextButton(
                    onClick = onCopy,
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("复制", color = Color.LightGray, fontSize = 12.sp)
                }
            }
            Text(
                text = code,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFE0E0E0),
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}

@Composable
fun ComposerBar(
    inputText: String,
    onInputTextChange: (String) -> Unit,
    isGenerating: Boolean,
    selectedModel: String,
    selectedReasoning: String,
    selectedPermission: String,
    onModelSelect: (String) -> Unit,
    onReasoningSelect: (String) -> Unit,
    onPermissionSelect: (String) -> Unit,
    onSend: () -> Unit,
    onCancel: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Options Row: Model / Reasoning / Permission
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Model Dropdown
                QuickDropdownChip(
                    label = "模型",
                    currentValue = selectedModel,
                    options = listOf(
                        "grok-4.7" to "Grok 4.7 (旗舰)",
                        "grok-4.7-build-fast" to "Grok 4.7 Fast (极速)",
                        "grok-4.6" to "Grok 4.6",
                        "grok-4.5" to "Grok 4.5"
                    ),
                    onSelect = onModelSelect
                )

                // Reasoning Effort Dropdown
                QuickDropdownChip(
                    label = "思考预算",
                    currentValue = selectedReasoning,
                    options = listOf(
                        "high" to "高 (High)",
                        "xhigh" to "极高 (Extra High)",
                        "medium" to "中 (Medium)",
                        "low" to "低 (Low)"
                    ),
                    onSelect = onReasoningSelect
                )

                // Permission Mode Dropdown
                QuickDropdownChip(
                    label = "权限模式",
                    currentValue = selectedPermission,
                    options = listOf(
                        "always-approve" to "全自动 (YOLO)",
                        "acceptEdits" to "自动写代码",
                        "plan" to "计划模式 (Plan)",
                        "default" to "标准安全模式"
                    ),
                    onSelect = onPermissionSelect
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Input Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = onInputTextChange,
                    placeholder = { Text("输入开发任务或指令... (按 Ctrl+Enter 发送)", fontSize = 14.sp) },
                    modifier = Modifier
                        .weight(1f)
                        .onPreviewKeyEvent { event ->
                            if (event.key == Key.Enter && (event.isCtrlPressed || event.isMetaPressed) && event.type == KeyEventType.KeyUp) {
                                onSend()
                                true
                            } else {
                                false
                            }
                        },
                    maxLines = 6,
                    shape = RoundedCornerShape(16.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                if (isGenerating) {
                    IconButton(
                        onClick = onCancel,
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = "Stop", tint = MaterialTheme.colorScheme.onError)
                    }
                } else {
                    IconButton(
                        onClick = onSend,
                        enabled = inputText.isNotBlank(),
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(
                                if (inputText.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                            )
                    ) {
                        Icon(
                            Icons.Default.Send,
                            contentDescription = "Send",
                            tint = if (inputText.isNotBlank()) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun QuickDropdownChip(
    label: String,
    currentValue: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val displayTitle = options.find { it.first == currentValue }?.second ?: currentValue

    Box {
        FilterChip(
            selected = true,
            onClick = { expanded = true },
            label = {
                Text(
                    text = "$label: $displayTitle",
                    style = MaterialTheme.typography.labelSmall
                )
            },
            trailingIcon = {
                Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
            }
        )

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { (id, title) ->
                DropdownMenuItem(
                    text = { Text(title) },
                    onClick = {
                        expanded = false
                        onSelect(id)
                    }
                )
            }
        }
    }
}
