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
import androidx.compose.foundation.lazy.itemsIndexed
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
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.verticalScroll
import com.grokapp.googlebookos.data.model.ChatMessage
import com.grokapp.googlebookos.data.model.SessionSummary
import com.grokapp.googlebookos.data.model.ToolCallInfo
import com.grokapp.googlebookos.data.model.ToolStatus
import com.grokapp.googlebookos.data.model.ProjectItem
import com.grokapp.googlebookos.data.model.FsDirectoryItem
import com.grokapp.googlebookos.data.model.SubscriptionQuota
import com.grokapp.googlebookos.data.model.ProductUsageItem
import com.grokapp.googlebookos.data.model.CodeReviewState
import com.grokapp.googlebookos.data.model.GitDiffFile
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.grokapp.googlebookos.util.StorageHelper
import com.grokapp.googlebookos.util.ChromeHelper
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import java.io.File
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

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

    val systemFolderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            val path = StorageHelper.resolveTreeUriToPath(context, uri)
            if (path != null) {
                if (uiState.folderPickerTarget == FolderPickerTarget.SWITCH_PROJECT) {
                    viewModel.setActiveCwd(path)
                    viewModel.closeFolderPickerDialog()
                    Toast.makeText(context, "已切换工作区: $path", Toast.LENGTH_SHORT).show()
                } else {
                    viewModel.setPendingParentDir(path)
                    viewModel.closeFolderPickerDialog()
                }
            } else {
                Toast.makeText(context, "无法解析所选目录路径", Toast.LENGTH_SHORT).show()
            }
        }
    }

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
                        onNewProject = { viewModel.openNewProjectDialog() },
                        onSwitchProject = { viewModel.openFolderPickerDialog(FolderPickerTarget.SWITCH_PROJECT) },
                        onRenameSession = { sid, currentTitle ->
                            renameInputText = currentTitle
                            showRenameDialog = sid
                        },
                        onDeleteSession = { viewModel.deleteSession(it) },
                        onToggleArchive = { viewModel.toggleArchive(it) },
                        onSearchChange = { viewModel.setSearchQuery(it) },
                        onShowArchivedToggle = { viewModel.setShowArchived(it) },
                        onOpenSettings = { showServerDialog = true },
                        onRefreshQuota = { viewModel.loadSubscriptionQuota() },
                        onOpenCodeReview = { viewModel.openCodeReview() }
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
                    onSwitchProject = { viewModel.openFolderPickerDialog(FolderPickerTarget.SWITCH_PROJECT) },
                    onOpenCodeReview = { viewModel.openCodeReview() },
                    onToggleTimeline = { viewModel.toggleActivityTimeline() },
                    onSelectTool = { viewModel.selectToolCall(it) },
                    onPreviewInChrome = { html -> viewModel.previewHtmlInChrome(context, html) },
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
                            onNewProject = {
                                viewModel.openNewProjectDialog()
                                scope.launch { drawerState.close() }
                            },
                            onSwitchProject = {
                                viewModel.openFolderPickerDialog(FolderPickerTarget.SWITCH_PROJECT)
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
                            onOpenSettings = { showServerDialog = true },
                            onRefreshQuota = { viewModel.loadSubscriptionQuota() },
                            onOpenCodeReview = {
                                viewModel.openCodeReview()
                                scope.launch { drawerState.close() }
                            }
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
                    onSwitchProject = { viewModel.openFolderPickerDialog(FolderPickerTarget.SWITCH_PROJECT) },
                    onOpenCodeReview = { viewModel.openCodeReview() },
                    onToggleTimeline = { viewModel.toggleActivityTimeline() },
                    onSelectTool = { viewModel.selectToolCall(it) },
                    onPreviewInChrome = { html -> viewModel.previewHtmlInChrome(context, html) },
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

    if (uiState.showNewProjectDialog) {
        NewProjectDialog(
            initialParentDir = uiState.pendingParentDir,
            onDismiss = { viewModel.closeNewProjectDialog() },
            onSelectParentFolder = {
                viewModel.openFolderPickerDialog(FolderPickerTarget.NEW_PROJECT_PARENT, uiState.pendingParentDir)
            },
            onCreate = { name, parentDir, template, initGit, createReadme ->
                viewModel.createNewProject(name, parentDir, template, initGit, createReadme)
            }
        )
    }

    if (uiState.showFolderPickerDialog) {
        FolderPickerDialog(
            uiState = uiState,
            onDismiss = { viewModel.closeFolderPickerDialog() },
            onSelectFolder = { selectedPath ->
                if (uiState.folderPickerTarget == FolderPickerTarget.SWITCH_PROJECT) {
                    viewModel.setActiveCwd(selectedPath)
                    viewModel.closeFolderPickerDialog()
                    Toast.makeText(context, "已切换工作区: $selectedPath", Toast.LENGTH_SHORT).show()
                } else {
                    viewModel.setPendingParentDir(selectedPath)
                    viewModel.closeFolderPickerDialog()
                }
            },
            onNavigateTo = { dirPath ->
                viewModel.loadFsList(dirPath)
            },
            onLaunchSystemPicker = {
                systemFolderPickerLauncher.launch(null)
            }
        )
    }

    if (uiState.showCodeReviewDialog) {
        CodeReviewDialog(
            state = uiState.codeReviewState,
            projectName = uiState.activeProjectName,
            onDismiss = { viewModel.closeCodeReview() },
            onRefresh = { scope -> viewModel.loadGitDiff(scope) },
            onScopeChange = { scope -> viewModel.loadGitDiff(scope) },
            onSelectFile = { path -> viewModel.selectDiffFile(path) },
            onRevertFile = { path -> viewModel.revertDiffFile(path) },
            onStartAiReview = { viewModel.startAiCodeReview() }
        )
    }
}


@Composable
fun SidebarContent(
    uiState: WorkbenchUiState,
    onNewSession: () -> Unit,
    onSelectSession: (String) -> Unit,
    onNewProject: () -> Unit,
    onSwitchProject: () -> Unit,
    onRenameSession: (String, String) -> Unit,
    onDeleteSession: (String) -> Unit,
    onToggleArchive: (String) -> Unit,
    onSearchChange: (String) -> Unit,
    onShowArchivedToggle: (Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    onRefreshQuota: () -> Unit = {},
    onOpenCodeReview: () -> Unit = {}
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

        Spacer(modifier = Modifier.height(4.dp))

        // Project Workspace Card
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Folder,
                            contentDescription = "Project",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = uiState.activeProjectName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = onSwitchProject,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.SwapHoriz,
                            contentDescription = "切换文件夹",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = uiState.activeCwd,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp
                )

                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onNewProject,
                        modifier = Modifier.weight(1f).height(32.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.CreateNewFolder, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("新建项目", fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = onSwitchProject,
                        modifier = Modifier.weight(1f).height(32.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("打开目录", fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                FilledTonalButton(
                    onClick = onOpenCodeReview,
                    modifier = Modifier.fillMaxWidth().height(32.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Difference, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("代码审查 (Git Review)", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

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

        // Subscription Quota Card (Bottom-Left Corner)
        SubscriptionQuotaCard(
            quota = uiState.subscriptionQuota,
            onRefresh = onRefreshQuota
        )
    }
}

@Composable
fun SubscriptionQuotaCard(
    quota: SubscriptionQuota,
    onRefresh: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }

    val remainingPercent = quota.creditRemainingPercent.coerceIn(0f, 100f)
    val usedPercent = quota.creditUsagePercent.coerceIn(0f, 100f)

    val statusColor = when {
        remainingPercent >= 30f -> Color(0xFF10B981) // Emerald Green
        remainingPercent >= 10f -> Color(0xFFF59E0B) // Amber
        else -> Color(0xFFEF4444) // Red
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { isExpanded = !isExpanded },
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            // Header Row: Tier name, remaining percent chip, and refresh button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF38BDF8).copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                            tint = Color(0xFF38BDF8)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = quota.tier.ifBlank { "Grok 订阅" },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        if (quota.email.isNotBlank()) {
                            Text(
                                text = quota.email,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = statusColor.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "余 ${remainingPercent.toInt()}%",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = statusColor,
                            fontSize = 11.sp
                        )
                    }

                    if (quota.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(24.dp)
                                .padding(4.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        IconButton(
                            onClick = onRefresh,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "刷新用量",
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Progress Bar
            LinearProgressIndicator(
                progress = { remainingPercent / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = statusColor,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Summary Footer: Used % and reset info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "已用 ${usedPercent.toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.5.sp
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = formatPeriodDate(quota.periodEnd),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.5.sp
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Expandable Product Breakdown
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "用量分布明细",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.5.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    if (quota.productUsage.isNotEmpty()) {
                        quota.productUsage.forEach { item ->
                            val prodLabel = when (item.product) {
                                "GrokBuild" -> "Grok Build (编程智能体)"
                                "GrokChat" -> "Grok Chat (问答)"
                                "GrokVoice" -> "Grok Voice (语音)"
                                else -> item.product
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = prodLabel,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${item.usagePercent.toInt()}%",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        Text(
                            text = "暂无细项数据",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (quota.periodStart.isNotBlank() && quota.periodEnd.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "周期: ${quota.periodStart.take(10)} 至 ${quota.periodEnd.take(10)} (周度)",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 9.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }
    }
}

fun formatPeriodDate(isoString: String): String {
    if (isoString.isBlank()) return "周期刷新"
    return try {
        val parts = isoString.split("T")
        if (parts.size >= 2) {
            val date = parts[0].substringAfter("-")
            val time = parts[1].take(5)
            "$date $time 重置"
        } else {
            isoString.take(10)
        }
    } catch (e: Exception) {
        "每周重置"
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
    onSwitchProject: () -> Unit,
    onOpenCodeReview: () -> Unit = {},
    onToggleTimeline: () -> Unit = {},
    onSelectTool: (String?) -> Unit = {},
    onPreviewInChrome: (String) -> Unit = {},
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
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            onClick = onSwitchProject,
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.padding(start = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Folder,
                                    contentDescription = null,
                                    modifier = Modifier.size(13.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = uiState.activeProjectName,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
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
                        modifier = Modifier.padding(end = 4.dp)
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

                    // Activity Timeline Toggle Button (时间流工作台)
                    IconButton(
                        onClick = onToggleTimeline,
                        modifier = Modifier.size(36.dp)
                    ) {
                        BadgedBox(
                            badge = {
                                val totalTools = uiState.messages.sumOf { it.toolCalls.size }
                                if (totalTools > 0) {
                                    Badge(
                                        containerColor = if (uiState.showActivityTimeline) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    ) {
                                        Text("$totalTools", fontSize = 9.sp)
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (uiState.showActivityTimeline) Icons.Default.ViewSidebar else Icons.Outlined.ViewSidebar,
                                contentDescription = "时间流工作台",
                                tint = if (uiState.showActivityTimeline) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Code Review Action Button
                    IconButton(
                        onClick = onOpenCodeReview,
                        modifier = Modifier.size(36.dp)
                    ) {
                        BadgedBox(
                            badge = {
                                val count = uiState.codeReviewState.files.size
                                if (count > 0) {
                                    Badge(
                                        containerColor = MaterialTheme.colorScheme.tertiary,
                                        contentColor = MaterialTheme.colorScheme.onTertiary
                                    ) {
                                        Text("$count", fontSize = 9.sp)
                                    }
                                }
                            }
                        ) {
                            Icon(Icons.Default.Difference, contentDescription = "代码审查 (Git Review)")
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
        }
    ) { innerPadding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Main Conversation Flow Column
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
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
                            ChatMessageBubble(
                                message = message,
                                selectedToolId = uiState.selectedToolCallId,
                                onSelectTool = { tool -> onSelectTool(tool.id) },
                                onToggleRightPanel = onToggleTimeline,
                                isRightPanelOpen = isWideScreen && uiState.showActivityTimeline,
                                onPreviewInChrome = onPreviewInChrome
                            )
                        }
                        item { Spacer(modifier = Modifier.height(16.dp)) }
                    }
                }

                // Composer at bottom of conversation
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

            // Right Column: Docked / Frozen Activity Timeline Panel on Wide Screen
            if (isWideScreen && uiState.showActivityTimeline) {
                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                AgentActivityTimelinePanel(
                    modifier = Modifier
                        .width(360.dp)
                        .fillMaxHeight(),
                    messages = uiState.messages,
                    isGenerating = uiState.isGenerating,
                    selectedToolId = uiState.selectedToolCallId,
                    onSelectTool = onSelectTool,
                    onClose = onToggleTimeline
                )
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
fun LocalWebServerBanner(
    urls: List<String>,
    onOpenUrl: (String) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Language,
                        contentDescription = "Web",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "本地 Web 服务已就绪",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "已检测到正在运行的本地服务，可在 Google Chrome 中直接测试与交互",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                urls.forEach { url ->
                    Button(
                        onClick = { onOpenUrl(url) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.OpenInBrowser,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "在 Chrome 中打开 $url",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MessageActionBar(
    content: String,
    thinking: String? = null,
    isUser: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isCopied by remember { mutableStateOf(false) }
    var isCopiedFull by remember { mutableStateOf(false) }

    LaunchedEffect(isCopied) {
        if (isCopied) {
            delay(2000)
            isCopied = false
        }
    }

    LaunchedEffect(isCopiedFull) {
        if (isCopiedFull) {
            delay(2000)
            isCopiedFull = false
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 4.dp, start = 2.dp, end = 2.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. Copy Content Button (复制正文)
        Surface(
            onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Message", content)
                clipboard.setPrimaryClip(clip)
                isCopied = true
                Toast.makeText(context, if (isUser) "提问内容已复制" else "回答内容已复制", Toast.LENGTH_SHORT).show()
            },
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.height(26.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                    contentDescription = "复制内容",
                    modifier = Modifier.size(13.dp),
                    tint = if (isCopied) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (isCopied) "已复制" else "复制内容",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 11.sp,
                    color = if (isCopied) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 2. If message has thinking chain, provide "复制含思维链" button (复制含思考过程)
        if (!isUser && !thinking.isNullOrBlank()) {
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                onClick = {
                    val fullText = buildString {
                        append("【思考过程】\n")
                        append(thinking.trim())
                        append("\n\n【正式回复】\n")
                        append(content.trim())
                    }
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("FullMessage", fullText)
                    clipboard.setPrimaryClip(clip)
                    isCopiedFull = true
                    Toast.makeText(context, "思考过程与正文已完整复制", Toast.LENGTH_SHORT).show()
                },
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.height(26.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isCopiedFull) Icons.Default.Check else Icons.Outlined.Psychology,
                        contentDescription = "复制含思维链",
                        modifier = Modifier.size(14.dp),
                        tint = if (isCopiedFull) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isCopiedFull) "已复制全部" else "复制含思考过程",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 11.sp,
                        color = if (isCopiedFull) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun ChatMessageBubble(
    message: ChatMessage,
    selectedToolId: String? = null,
    onSelectTool: (ToolCallInfo) -> Unit = {},
    onToggleRightPanel: () -> Unit = {},
    isRightPanelOpen: Boolean = false,
    onPreviewInChrome: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val isUser = message.role == "user"
    val localUrls = remember(message.content) {
        ChromeHelper.extractUrls(message.content).filter { ChromeHelper.isLocalWebUrl(it) }
    }

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
                .widthIn(max = 880.dp)
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

            // Tool Execution: Horizontal Timeline Stream (横向时间流工作形态)
            if (message.toolCalls.isNotEmpty()) {
                HorizontalToolTimeline(
                    toolCalls = message.toolCalls,
                    selectedToolId = selectedToolId,
                    onSelectTool = onSelectTool,
                    onToggleRightPanel = onToggleRightPanel,
                    isRightPanelOpen = isRightPanelOpen
                )
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
                            isUser = isUser,
                            onPreviewInChrome = onPreviewInChrome
                        )
                    }
                }

                // Convenient Content Copy Bar (便捷内容复制栏)
                if (message.content.isNotBlank()) {
                    MessageActionBar(
                        content = message.content,
                        thinking = message.thinking,
                        isUser = isUser
                    )
                }
            }

            // Local Web Server Banner: If local dev server / local URL detected
            if (!isUser && localUrls.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                LocalWebServerBanner(
                    urls = localUrls,
                    onOpenUrl = { ChromeHelper.openUrl(context, it) }
                )
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
    val context = LocalContext.current
    var isExpanded by remember { mutableStateOf(isThinking) }

    LaunchedEffect(isThinking) {
        if (isThinking) {
            isExpanded = true
        }
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { isExpanded = !isExpanded }
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    if (isThinking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(15.dp),
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
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "深度思考过程",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    if (thinking.isNotBlank()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        ) {
                            Text(
                                text = "${thinking.length} 字",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!isThinking && thinking.isNotBlank()) {
                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Thinking", thinking)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "思考过程已复制", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(26.dp)
                        ) {
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = "复制思考",
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "展开折叠",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), RoundedCornerShape(2.dp))
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        SelectionContainer(modifier = Modifier.weight(1f)) {
                            Text(
                                text = thinking.ifBlank { "正在整理思路..." },
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 12.5.sp,
                                    lineHeight = 19.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

fun getToolIconAndTint(toolName: String): Pair<androidx.compose.ui.graphics.vector.ImageVector, Color> {
    return when {
        toolName.contains("终端") || toolName.contains("terminal") || toolName.contains("bash") || toolName.contains("cmd") ->
            Icons.Default.Terminal to Color(0xFF38BDF8)
        toolName.contains("读取") || toolName.contains("read") ->
            Icons.Default.Description to Color(0xFF818CF8)
        toolName.contains("修改") || toolName.contains("编辑") || toolName.contains("replace") ->
            Icons.Default.Edit to Color(0xFFF59E0B)
        toolName.contains("新建") || toolName.contains("write") ->
            Icons.Default.NoteAdd to Color(0xFF10B981)
        toolName.contains("目录") || toolName.contains("dir") || toolName.contains("list") ->
            Icons.Default.Folder to Color(0xFFFBBF24)
        toolName.contains("搜索") || toolName.contains("grep") || toolName.contains("search") ->
            Icons.Default.Search to Color(0xFFA78BFA)
        toolName.contains("联网") || toolName.contains("web") ->
            Icons.Default.Language to Color(0xFF34D399)
        toolName.contains("智能体") || toolName.contains("agent") ->
            Icons.Default.SmartToy to Color(0xFFEC4899)
        toolName.contains("计划") || toolName.contains("plan") ->
            Icons.Default.Assignment to Color(0xFF60A5FA)
        else ->
            Icons.Default.Build to Color(0xFF94A3B8)
    }
}

@Composable
fun HorizontalToolTimeline(
    toolCalls: List<ToolCallInfo>,
    selectedToolId: String?,
    onSelectTool: (ToolCallInfo) -> Unit,
    onToggleRightPanel: () -> Unit,
    isRightPanelOpen: Boolean
) {
    var isExpandedInline by remember { mutableStateOf(false) }
    val isAnyRunning = toolCalls.any { it.status == ToolStatus.RUNNING }
    val isAnyFailed = toolCalls.any { it.status == ToolStatus.FAILED }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Timeline,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "操作时间流",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "${toolCalls.size} 步",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    // Status Badge
                    if (isAnyRunning) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(10.dp),
                                strokeWidth = 1.5.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("执行中", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                        }
                    } else if (isAnyFailed) {
                        Text("有异常", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Medium)
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(12.dp), tint = Color(0xFF10B981))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("全部完成", fontSize = 10.5.sp, color = Color(0xFF10B981), fontWeight = FontWeight.Medium)
                        }
                    }
                }

                // Action Buttons: Toggle Right Panel / Inline Expand
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = onToggleRightPanel,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                    ) {
                        Icon(
                            imageVector = if (isRightPanelOpen) Icons.Default.ViewSidebar else Icons.Outlined.ViewSidebar,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(if (isRightPanelOpen) "侧栏工作台" else "查看工作台", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                    }

                    IconButton(
                        onClick = { isExpandedInline = !isExpandedInline },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = if (isExpandedInline) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = "展开折叠",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Horizontal Timeline Stream Row
            val scrollState = rememberScrollState()
            LaunchedEffect(toolCalls.size) {
                scrollState.animateScrollTo(scrollState.maxValue)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scrollState)
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                toolCalls.forEachIndexed { index, tool ->
                    val isSelected = tool.id == selectedToolId
                    HorizontalToolPill(
                        stepIndex = index + 1,
                        tool = tool,
                        isSelected = isSelected,
                        onClick = { onSelectTool(tool) }
                    )
                    if (index < toolCalls.size - 1) {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.outlineVariant
                        )
                    }
                }
            }

            // Inline Expansion (Optional)
            AnimatedVisibility(
                visible = isExpandedInline,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    toolCalls.forEach { tool ->
                        ToolCallCard(tool = tool)
                    }
                }
            }
        }
    }
}

@Composable
fun HorizontalToolPill(
    stepIndex: Int,
    tool: ToolCallInfo,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val (icon, tint) = remember(tool.name) { getToolIconAndTint(tool.name) }
    val shortLabel = remember(tool.name) {
        when {
            tool.name.contains("读取文件:") -> "读取 " + tool.name.substringAfter("读取文件:").trim().substringAfterLast("/")
            tool.name.contains("终端执行:") -> "终端 " + tool.name.substringAfter("终端执行:").trim().take(18)
            tool.name.contains("搜索代码:") -> "搜索 " + tool.name.substringAfter("搜索代码:").trim().take(16)
            tool.name.contains("调度子智能体") -> "调度子智能体"
            tool.name.contains("修改文件:") -> "编辑 " + tool.name.substringAfter("修改文件:").trim().substringAfterLast("/")
            tool.name.contains("新建文件:") -> "新建 " + tool.name.substringAfter("新建文件:").trim().substringAfterLast("/")
            else -> tool.name.take(18)
        }
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(
            1.dp,
            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$stepIndex",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = shortLabel,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1
            )
            Spacer(modifier = Modifier.width(5.dp))
            when (tool.status) {
                ToolStatus.RUNNING -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(9.dp),
                        strokeWidth = 1.2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                ToolStatus.SUCCESS -> {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(11.dp)
                    )
                }
                ToolStatus.FAILED -> {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(11.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun AgentActivityTimelinePanel(
    modifier: Modifier = Modifier,
    messages: List<ChatMessage>,
    isGenerating: Boolean,
    selectedToolId: String?,
    onSelectTool: (String?) -> Unit,
    onClose: () -> Unit
) {
    var selectedScopeTab by remember { mutableIntStateOf(0) }

    val allTools = remember(messages) {
        messages.flatMap { it.toolCalls }
    }
    val latestTools = remember(messages) {
        messages.lastOrNull { it.role == "assistant" && it.toolCalls.isNotEmpty() }?.toolCalls ?: emptyList()
    }

    val displayTools = if (selectedScopeTab == 0 && latestTools.isNotEmpty()) latestTools else allTools

    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Timeline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "时间流工作台",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "${displayTools.size} 步",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isGenerating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "关闭工作台", modifier = Modifier.size(16.dp))
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // Sub-tabs: 本轮操作 vs 全部操作
            if (allTools.size > latestTools.size && latestTools.isNotEmpty()) {
                TabRow(
                    selectedTabIndex = selectedScopeTab,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Tab(
                        selected = selectedScopeTab == 0,
                        onClick = { selectedScopeTab = 0 },
                        text = { Text("本轮 (${latestTools.size})", fontSize = 12.sp) }
                    )
                    Tab(
                        selected = selectedScopeTab == 1,
                        onClick = { selectedScopeTab = 1 },
                        text = { Text("全部 (${allTools.size})", fontSize = 12.sp) }
                    )
                }
            }

            // Timeline List Content
            if (displayTools.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Text(
                            text = "暂无操作时间流",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "当智能体执行读取文件、终端命令、代码搜索时，操作过程将在此处实时冻结展示。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
            } else {
                val listState = rememberLazyListState()
                LaunchedEffect(displayTools.size) {
                    listState.animateScrollToItem(displayTools.size - 1)
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    itemsIndexed(displayTools, key = { index, tool -> tool.id }) { index, tool ->
                        val isSelected = tool.id == selectedToolId
                        TimelineStepCard(
                            stepIndex = index + 1,
                            tool = tool,
                            isLast = index == displayTools.size - 1,
                            isSelected = isSelected,
                            onToggleSelect = { onSelectTool(tool.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TimelineStepCard(
    stepIndex: Int,
    tool: ToolCallInfo,
    isLast: Boolean,
    isSelected: Boolean,
    onToggleSelect: () -> Unit
) {
    val context = LocalContext.current
    var isExpanded by remember { mutableStateOf(isSelected) }

    LaunchedEffect(isSelected) {
        if (isSelected) isExpanded = true
    }

    val (toolIcon, iconTint) = remember(tool.name) { getToolIconAndTint(tool.name) }

    val statusColor = when (tool.status) {
        ToolStatus.RUNNING -> MaterialTheme.colorScheme.primary
        ToolStatus.SUCCESS -> Color(0xFF10B981)
        ToolStatus.FAILED -> MaterialTheme.colorScheme.error
    }

    Row(modifier = Modifier.fillMaxWidth()) {
        // Left Column: Step node + Timeline connector
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(28.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(statusColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                if (tool.status == ToolStatus.RUNNING) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 1.5.dp,
                        color = statusColor
                    )
                } else {
                    Text(
                        text = "$stepIndex",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                }
            }

            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(if (isExpanded) 120.dp else 40.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                )
            }
        }

        Spacer(modifier = Modifier.width(6.dp))

        // Right Column: Card
        Surface(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(10.dp))
                .clickable {
                    isExpanded = !isExpanded
                    onToggleSelect()
                },
            shape = RoundedCornerShape(10.dp),
            color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f) else MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(
                1.dp,
                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            )
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                // Title Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = toolIcon,
                            contentDescription = null,
                            tint = iconTint,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = tool.name,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = if (isExpanded) 3 else 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        when (tool.status) {
                            ToolStatus.RUNNING -> {
                                Text("执行中", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                            }
                            ToolStatus.SUCCESS -> {
                                Text("已完成", fontSize = 10.5.sp, color = Color(0xFF10B981), fontWeight = FontWeight.Medium)
                            }
                            ToolStatus.FAILED -> {
                                Text("失败", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Medium)
                            }
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Expanded Args and Results
                AnimatedVisibility(
                    visible = isExpanded,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                        if (tool.args.isNotBlank()) {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("参数", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.5.sp)
                                    IconButton(
                                        onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            val clip = ClipData.newPlainText("ToolArgs", tool.args)
                                            clipboard.setPrimaryClip(clip)
                                            Toast.makeText(context, "参数已复制", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(20.dp)
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = "复制参数", modifier = Modifier.size(11.dp))
                                    }
                                }
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFF0D1117),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    SelectionContainer {
                                        Text(
                                            text = tool.args,
                                            modifier = Modifier.padding(6.dp),
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.5.sp,
                                            lineHeight = 15.sp,
                                            color = Color(0xFFC9D1D9)
                                        )
                                    }
                                }
                            }
                        }

                        if (!tool.result.isNullOrBlank()) {
                            val resultLocalUrls = remember(tool.result) {
                                ChromeHelper.extractUrls(tool.result).filter { ChromeHelper.isLocalWebUrl(it) }
                            }
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("结果日志", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.5.sp)
                                    IconButton(
                                        onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            val clip = ClipData.newPlainText("ToolResult", tool.result)
                                            clipboard.setPrimaryClip(clip)
                                            Toast.makeText(context, "结果已复制", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(20.dp)
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = "复制结果", modifier = Modifier.size(11.dp))
                                    }
                                }
                                if (resultLocalUrls.isNotEmpty()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState())
                                            .padding(bottom = 6.dp),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        resultLocalUrls.forEach { localUrl ->
                                            FilledTonalButton(
                                                onClick = { ChromeHelper.openUrl(context, localUrl) },
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                shape = RoundedCornerShape(6.dp),
                                                modifier = Modifier.height(26.dp)
                                            ) {
                                                Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(12.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("在 Chrome 中打开: $localUrl", fontSize = 10.5.sp, fontWeight = FontWeight.Medium)
                                            }
                                        }
                                    }
                                }
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFF0D1117),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    SelectionContainer {
                                        Text(
                                            text = tool.result,
                                            modifier = Modifier.padding(6.dp),
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.5.sp,
                                            lineHeight = 15.sp,
                                            color = Color(0xFF7EE787)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ToolCallCard(tool: ToolCallInfo) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    val (toolIcon, iconTint) = remember(tool.name) { getToolIconAndTint(tool.name) }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(iconTint.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = toolIcon,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = iconTint
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = tool.name.ifBlank { "执行工具" },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    when (tool.status) {
                        ToolStatus.RUNNING -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                strokeWidth = 1.5.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "执行中",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        ToolStatus.SUCCESS -> {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                                tint = Color(0xFF10B981)
                            )
                            Text(
                                text = "已完成",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF10B981),
                                fontWeight = FontWeight.Medium
                            )
                        }
                        ToolStatus.FAILED -> {
                            Icon(
                                Icons.Default.Cancel,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = "失败",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "展开折叠",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    if (tool.args.isNotBlank()) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "调用参数",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.SemiBold
                                )
                                IconButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = ClipData.newPlainText("ToolArgs", tool.args)
                                        clipboard.setPrimaryClip(clip)
                                        Toast.makeText(context, "参数已复制", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(22.dp)
                                ) {
                                    Icon(
                                        Icons.Default.ContentCopy,
                                        contentDescription = "复制参数",
                                        modifier = Modifier.size(12.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF0D1117),
                                border = BorderStroke(1.dp, Color(0xFF30363D)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                SelectionContainer {
                                    Text(
                                        text = tool.args,
                                        modifier = Modifier.padding(8.dp),
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        lineHeight = 16.sp,
                                        color = Color(0xFFC9D1D9)
                                    )
                                }
                            }
                        }
                    }

                    if (!tool.result.isNullOrBlank()) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "执行结果",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.SemiBold
                                )
                                IconButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = ClipData.newPlainText("ToolResult", tool.result)
                                        clipboard.setPrimaryClip(clip)
                                        Toast.makeText(context, "结果已复制", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(22.dp)
                                ) {
                                    Icon(
                                        Icons.Default.ContentCopy,
                                        contentDescription = "复制结果",
                                        modifier = Modifier.size(12.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF0D1117),
                                border = BorderStroke(1.dp, Color(0xFF30363D)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                SelectionContainer {
                                    Text(
                                        text = tool.result,
                                        modifier = Modifier.padding(8.dp),
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        lineHeight = 16.sp,
                                        color = Color(0xFF7EE787)
                                    )
                                }
                            }
                        }
                    } else if (tool.status == ToolStatus.RUNNING) {
                        Text(
                            text = "正在执行，等待输出...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MarkdownMessageContent(
    text: String,
    isUser: Boolean,
    onPreviewInChrome: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val chunks = remember(text) { parseCodeBlocks(text) }
    val linkColor = if (isUser) MaterialTheme.colorScheme.inversePrimary else MaterialTheme.colorScheme.primary

    SelectionContainer {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            chunks.forEach { chunk ->
                when (chunk) {
                    is ContentChunk.Text -> {
                        val annotated = remember(chunk.content, linkColor) {
                            buildMarkdownAnnotatedString(
                                text = chunk.content,
                                linkColor = linkColor,
                                onLinkClick = { url ->
                                    ChromeHelper.openUrl(context, url)
                                }
                            )
                        }
                        Text(
                            text = annotated,
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
                            },
                            onPreviewInChrome = onPreviewInChrome
                        )
                    }
                }
            }
        }
    }
}

fun buildMarkdownAnnotatedString(
    text: String,
    linkColor: Color,
    onLinkClick: (String) -> Unit
): AnnotatedString {
    return buildAnnotatedString {
        val linkPattern = "(?:\\[([^\\]]+)\\]\\(([^\\)]+)\\))|(https?://[^\\s)\\]>\"']+)|((?:localhost|127\\.0\\.0\\.1):\\d+[^\\s)\\]>\"']*)".toRegex(RegexOption.IGNORE_CASE)
        var lastIdx = 0

        for (match in linkPattern.findAll(text)) {
            val start = match.range.first
            val end = match.range.last + 1

            if (start > lastIdx) {
                append(text.substring(lastIdx, start))
            }

            val mdTitle = match.groups[1]?.value
            val mdUrl = match.groups[2]?.value
            val rawHttpUrl = match.groups[3]?.value
            val rawLocalhostUrl = match.groups[4]?.value

            val (displayText, targetUrl) = when {
                mdTitle != null && mdUrl != null -> Pair(mdTitle, ChromeHelper.normalizeUrl(mdUrl))
                rawHttpUrl != null -> Pair(rawHttpUrl, ChromeHelper.normalizeUrl(rawHttpUrl))
                rawLocalhostUrl != null -> Pair(rawLocalhostUrl, ChromeHelper.normalizeUrl(rawLocalhostUrl))
                else -> Pair(match.value, ChromeHelper.normalizeUrl(match.value))
            }

            pushLink(
                LinkAnnotation.Url(
                    url = targetUrl,
                    styles = TextLinkStyles(
                        style = SpanStyle(
                            color = linkColor,
                            textDecoration = TextDecoration.Underline,
                            fontWeight = FontWeight.SemiBold
                        )
                    ),
                    linkInteractionListener = { annotation ->
                        val url = (annotation as LinkAnnotation.Url).url
                        onLinkClick(url)
                    }
                )
            )
            append(displayText)
            pop()

            lastIdx = end
        }

        if (lastIdx < text.length) {
            append(text.substring(lastIdx))
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
    onCopy: () -> Unit,
    onPreviewInChrome: ((String) -> Unit)? = null
) {
    val isWeb = remember(lang, code) { ChromeHelper.isHtmlOrWebCode(lang, code) }

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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = lang.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.LightGray,
                        fontWeight = FontWeight.Bold
                    )
                    if (isWeb) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFF10B981).copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = "Web 页面",
                                fontSize = 10.sp,
                                color = Color(0xFF10B981),
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isWeb && onPreviewInChrome != null) {
                        FilledTonalButton(
                            onClick = { onPreviewInChrome(code) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            shape = RoundedCornerShape(6.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            modifier = Modifier.height(26.dp)
                        ) {
                            Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("在 Chrome 中运行", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    TextButton(
                        onClick = onCopy,
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("复制", color = Color.LightGray, fontSize = 12.sp)
                    }
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

@Composable
fun NewProjectDialog(
    initialParentDir: String,
    onDismiss: () -> Unit,
    onSelectParentFolder: () -> Unit,
    onCreate: (name: String, parentDir: String, template: String, initGit: Boolean, createReadme: Boolean) -> Unit
) {
    var projectName by remember { mutableStateOf("") }
    var parentDir by remember { mutableStateOf(initialParentDir) }
    var selectedTemplate by remember { mutableStateOf("empty") }
    var initGit by remember { mutableStateOf(true) }
    var createReadme by remember { mutableStateOf(true) }

    LaunchedEffect(initialParentDir) {
        parentDir = initialParentDir
    }

    val fullPathPreview = remember(projectName, parentDir) {
        val cleanParent = if (parentDir.endsWith("/") && parentDir.length > 1) parentDir.dropLast(1) else parentDir
        if (projectName.isBlank()) cleanParent else "$cleanParent/$projectName"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CreateNewFolder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("新建项目", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Project Name
                OutlinedTextField(
                    value = projectName,
                    onValueChange = { projectName = it.trim().replace(" ", "-") },
                    label = { Text("项目名称") },
                    placeholder = { Text("例如：MyAwesomeApp") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Parent Directory & Browse Button
                Column {
                    Text(
                        text = "存放位置 (Googlebook OS 文件夹):",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = parentDir,
                            onValueChange = { parentDir = it },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Button(
                            onClick = onSelectParentFolder,
                            contentPadding = PaddingValues(horizontal = 10.dp)
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("浏览", fontSize = 12.sp)
                        }
                    }
                }

                // Full Path Preview
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text("完整路径预览：", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        Text(
                            text = fullPathPreview,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Template selection
                Text("项目模版类型:", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedTemplate == "empty",
                        onClick = { selectedTemplate = "empty" },
                        label = { Text("空白项目") }
                    )
                    FilterChip(
                        selected = selectedTemplate == "android",
                        onClick = { selectedTemplate = "android" },
                        label = { Text("Android 应用") }
                    )
                    FilterChip(
                        selected = selectedTemplate == "python",
                        onClick = { selectedTemplate = "python" },
                        label = { Text("Python 项目") }
                    )
                    FilterChip(
                        selected = selectedTemplate == "web",
                        onClick = { selectedTemplate = "web" },
                        label = { Text("Web 前端") }
                    )
                }

                // Checkboxes
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = initGit, onCheckedChange = { initGit = it })
                    Text("初始化 Git 仓库 (git init)", style = MaterialTheme.typography.bodyMedium)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = createReadme, onCheckedChange = { createReadme = it })
                    Text("生成 README.md 与 .gitignore", style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (projectName.isNotBlank()) {
                        onCreate(projectName, parentDir, selectedTemplate, initGit, createReadme)
                    }
                },
                enabled = projectName.isNotBlank()
            ) {
                Text("创建并打开工作区")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
fun FolderPickerDialog(
    uiState: WorkbenchUiState,
    onDismiss: () -> Unit,
    onSelectFolder: (String) -> Unit,
    onNavigateTo: (String) -> Unit,
    onLaunchSystemPicker: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) } // 0: 目录浏览, 1: 已有项目
    var showCreateSubfolderDialog by remember { mutableStateOf(false) }
    var newSubfolderName by remember { mutableStateOf("") }

    val currentPath = uiState.currentFsList?.currentPath ?: uiState.activeCwd
    val parentPath = uiState.currentFsList?.parentPath

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("选择系统文件夹", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp)) {
                // System Document Picker Action Button (Top highlight)
                FilledTonalButton(
                    onClick = onLaunchSystemPicker,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("📂 打开系统文件选择器 (SAF)")
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Quick Storage Bookmarks
                Text("快捷位置:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val root = StorageHelper.getExternalStorageRoot()
                    SuggestionChip(
                        onClick = { onNavigateTo(StorageHelper.getDefaultCodesDir()) },
                        label = { Text("⚡ Codes") }
                    )
                    SuggestionChip(
                        onClick = { onNavigateTo(root) },
                        label = { Text("🏠 内部存储") }
                    )
                    SuggestionChip(
                        onClick = { onNavigateTo("$root/Download") },
                        label = { Text("📥 Download") }
                    )
                    SuggestionChip(
                        onClick = { onNavigateTo("$root/Documents") },
                        label = { Text("📄 Documents") }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Tabs: 目录浏览 vs 已有项目
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("目录树浏览") }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("已有项目 (${uiState.recentProjects.size})") }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (selectedTab == 0) {
                    // Current Path & Up navigation
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (parentPath != null) {
                                IconButton(
                                    onClick = { onNavigateTo(parentPath) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.ArrowUpward, contentDescription = "上一级", modifier = Modifier.size(16.dp))
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            Text(
                                text = currentPath,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            IconButton(
                                onClick = { showCreateSubfolderDialog = true },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.CreateNewFolder, contentDescription = "新建子文件夹", modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (uiState.isFsLoading) {
                        Box(modifier = Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        }
                    } else {
                        val dirs = uiState.currentFsList?.directories ?: emptyList()
                        if (dirs.isEmpty()) {
                            Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                                Text("此目录下没有子文件夹", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                            ) {
                                items(dirs) { dir ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onNavigateTo(dir.path) }
                                            .padding(horizontal = 8.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Folder,
                                            contentDescription = null,
                                            tint = if (dir.isHidden) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = dir.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (dir.isHidden) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.weight(1f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (dir.isGit) {
                                            Surface(
                                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    "git",
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                                    fontSize = 10.sp,
                                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Icon(Icons.Default.ChevronRight, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.outline)
                                    }
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                }
                            }
                        }
                    }
                } else {
                    // Recent / Detected Projects List
                    if (uiState.recentProjects.isEmpty()) {
                        Box(modifier = Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
                            Text("未发现已有项目，可直接浏览目录选择", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(250.dp)
                        ) {
                            items(uiState.recentProjects) { proj ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clickable { onSelectFolder(proj.path) },
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (proj.path == uiState.activeCwd)
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(proj.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                            Text(proj.path, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, fontFamily = FontFamily.Monospace)
                                        }
                                        if (proj.isGit) {
                                            Surface(
                                                color = MaterialTheme.colorScheme.secondaryContainer,
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text("Git", modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp), fontSize = 10.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSelectFolder(currentPath) }) {
                Text("选择当前文件夹")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )

    if (showCreateSubfolderDialog) {
        AlertDialog(
            onDismissRequest = { showCreateSubfolderDialog = false },
            title = { Text("新建子文件夹") },
            text = {
                OutlinedTextField(
                    value = newSubfolderName,
                    onValueChange = { newSubfolderName = it.trim() },
                    label = { Text("文件夹名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newSubfolderName.isNotBlank()) {
                            val newPath = "$currentPath/$newSubfolderName"
                            StorageHelper.createLocalProject(newPath, "empty", false)
                            showCreateSubfolderDialog = false
                            newSubfolderName = ""
                            onNavigateTo(newPath)
                        }
                    },
                    enabled = newSubfolderName.isNotBlank()
                ) {
                    Text("创建")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateSubfolderDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodeReviewDialog(
    state: CodeReviewState,
    projectName: String,
    onDismiss: () -> Unit,
    onRefresh: (String) -> Unit,
    onScopeChange: (String) -> Unit,
    onSelectFile: (String) -> Unit,
    onRevertFile: (String) -> Unit,
    onStartAiReview: () -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    var fileToRevert by remember { mutableStateOf<GitDiffFile?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.94f)
                .clip(RoundedCornerShape(16.dp)),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header Bar
                CodeReviewHeader(
                    projectName = projectName,
                    state = state,
                    onScopeChange = onScopeChange,
                    onRefresh = { onRefresh(state.scope) },
                    onStartAiReview = onStartAiReview,
                    onDismiss = onDismiss
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // Main Content
                if (state.isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("正在读取 Git 变更与分支状态...", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                } else if (!state.error.isNullOrBlank()) {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
                            Text("读取 Git 差异失败", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(state.error, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Button(onClick = { onRefresh(state.scope) }) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("重试")
                            }
                        }
                    }
                } else if (!state.isGit) {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(48.dp))
                            Text("当前目录未初始化 Git 仓库", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                "工作区 ${state.cwd} 尚不是一个 Git 项目。请切换到包含 Git 仓库的项目，或在终端中运行 git init 初始化。",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.widthIn(max = 480.dp)
                            )
                        }
                    }
                } else if (state.files.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(48.dp))
                            Text(
                                text = if (state.scope == "working") "当前工作区干净 (无未提交改动)" else "最新提交没有可对比的代码差异",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (state.scope == "working") "可以切换到「最新提交」查看上一次代码提交的差异，或直接进行 AI 代码分析。" else "当前仓库暂无提交历史差异。",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (state.scope == "working") {
                                    OutlinedButton(onClick = { onScopeChange("latest") }) {
                                        Text("切换到最新提交查看")
                                    }
                                } else {
                                    OutlinedButton(onClick = { onScopeChange("working") }) {
                                        Text("查看工作区改动")
                                    }
                                }
                                Button(onClick = onStartAiReview) {
                                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("针对项目发起 Grok 深度审查")
                                }
                            }
                        }
                    }
                } else {
                    // Split View: Files list (left) & Detail viewer (right)
                    Row(modifier = Modifier.fillMaxSize()) {
                        // Left Column: File List
                        Column(
                            modifier = Modifier
                                .width(310.dp)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        ) {
                            // File list header
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "变更文件 (${state.files.size})",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )

                                val totalAdds = state.files.sumOf { it.additions }
                                val totalDels = state.files.sumOf { it.deletions }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (totalAdds > 0) {
                                        Text("+$totalAdds", style = MaterialTheme.typography.labelSmall, color = Color(0xFF10B981), fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.width(4.dp))
                                    }
                                    if (totalDels > 0) {
                                        Text("-$totalDels", style = MaterialTheme.typography.labelSmall, color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                            // File list
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
                            ) {
                                items(state.files, key = { it.path }) { file ->
                                    val isSelected = file.path == state.selectedFilePath
                                    DiffFileItemRow(
                                        file = file,
                                        isSelected = isSelected,
                                        canRevert = state.scope == "working",
                                        onClick = { onSelectFile(file.path) },
                                        onRevert = { fileToRevert = file }
                                    )
                                }
                            }
                        }

                        VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        // Right Column: Diff / Source / AI Review
                        val currentFile = state.files.find { it.path == state.selectedFilePath } ?: state.files.firstOrNull()

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        ) {
                            // Sub-tabs
                            TabRow(
                                selectedTabIndex = selectedTab,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Tab(
                                    selected = selectedTab == 0,
                                    onClick = { selectedTab = 0 },
                                    text = { Text("代码差异 (Diff)") }
                                )
                                Tab(
                                    selected = selectedTab == 1,
                                    onClick = { selectedTab = 1 },
                                    text = { Text("完整源码 (Source)") }
                                )
                                Tab(
                                    selected = selectedTab == 2,
                                    onClick = { selectedTab = 2 },
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.primary)
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("AI 审查报告")
                                        }
                                    }
                                )
                            }

                            when (selectedTab) {
                                0 -> {
                                    // Diff Tab
                                    DiffViewerTab(
                                        file = currentFile,
                                        onCopyDiff = {
                                            currentFile?.diff?.let { diffText ->
                                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                val clip = ClipData.newPlainText("Diff", diffText)
                                                clipboard.setPrimaryClip(clip)
                                                Toast.makeText(context, "Diff 已复制", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    )
                                }
                                1 -> {
                                    // Source Tab
                                    SourceViewerTab(
                                        filePath = currentFile?.path ?: "",
                                        content = state.selectedFileContent,
                                        onCopyContent = {
                                            state.selectedFileContent?.let { fileText ->
                                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                val clip = ClipData.newPlainText("Source", fileText)
                                                clipboard.setPrimaryClip(clip)
                                                Toast.makeText(context, "文件源码已复制", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    )
                                }
                                2 -> {
                                    // AI Review Tab
                                    AiReviewTab(
                                        state = state,
                                        projectName = projectName,
                                        onStartAiReview = onStartAiReview,
                                        onDismiss = onDismiss
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Confirm Revert Dialog
    if (fileToRevert != null) {
        AlertDialog(
            onDismissRequest = { fileToRevert = null },
            title = { Text("确认撤销修改？") },
            text = {
                Text("即将撤销文件「${fileToRevert?.name}」在工作区中的所有未提交更改。\n此操作不可逆，确定继续吗？")
            },
            confirmButton = {
                Button(
                    onClick = {
                        fileToRevert?.let { onRevertFile(it.path) }
                        fileToRevert = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("确认撤销")
                }
            },
            dismissButton = {
                TextButton(onClick = { fileToRevert = null }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
fun CodeReviewHeader(
    projectName: String,
    state: CodeReviewState,
    onScopeChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onStartAiReview: () -> Unit,
    onDismiss: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: Title & Repo details
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                Icons.Default.Difference,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "代码审查与差异分析",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = projectName,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    if (state.branch.isNotBlank()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = state.branch,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                    if (state.commitId.isNotBlank()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = state.commitId.take(7),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                if (state.commitMsg.isNotBlank()) {
                    Text(
                        text = "最新提交: ${state.commitMsg}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Center / Scope Filter Buttons
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            FilterChip(
                selected = state.scope == "working",
                onClick = { onScopeChange("working") },
                label = { Text("工作区改动", fontSize = 12.sp) },
                leadingIcon = {
                    if (state.hasUncommitted) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF10B981))
                        )
                    }
                }
            )
            FilterChip(
                selected = state.scope == "latest",
                onClick = { onScopeChange("latest") },
                label = { Text("最新提交", fontSize = 12.sp) }
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Right Action Buttons
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = onStartAiReview,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Grok 深度审查", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }

            Spacer(modifier = Modifier.width(6.dp))
            IconButton(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, contentDescription = "刷新")
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "关闭")
            }
        }
    }
}

@Composable
fun DiffFileItemRow(
    file: GitDiffFile,
    isSelected: Boolean,
    canRevert: Boolean,
    onClick: () -> Unit,
    onRevert: () -> Unit
) {
    val statusColor = when (file.status) {
        "A" -> Color(0xFF10B981)
        "D" -> Color(0xFFEF4444)
        "??" -> Color(0xFF06B6D4)
        else -> Color(0xFFF59E0B)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() },
        color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)) else null
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status Tag
            Surface(
                color = statusColor.copy(alpha = 0.15f),
                shape = RoundedCornerShape(4.dp),
                border = BorderStroke(1.dp, statusColor.copy(alpha = 0.5f))
            ) {
                Text(
                    text = file.status,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = statusColor,
                    fontSize = 10.sp
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // File path and name
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val dir = file.path.substringBeforeLast("/", "")
                if (dir.isNotBlank()) {
                    Text(
                        text = dir,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Stat badge (+ -)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (file.additions > 0) {
                    Text("+${file.additions}", fontSize = 10.sp, color = Color(0xFF10B981), fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.width(3.dp))
                }
                if (file.deletions > 0) {
                    Text("-${file.deletions}", fontSize = 10.sp, color = Color(0xFFEF4444), fontWeight = FontWeight.SemiBold)
                }
            }

            // Revert button if in working tree
            if (canRevert) {
                IconButton(
                    onClick = onRevert,
                    modifier = Modifier.size(24.dp).padding(start = 2.dp)
                ) {
                    Icon(
                        Icons.Default.Restore,
                        contentDescription = "撤销",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun DiffViewerTab(
    file: GitDiffFile?,
    onCopyDiff: () -> Unit
) {
    if (file == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("请在左侧选择要查看差异的文件", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // File bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = file.path,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "+${file.additions} -${file.deletions}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            TextButton(
                onClick = onCopyDiff,
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("复制 Diff", fontSize = 12.sp)
            }
        }

        // Diff Lines Content
        val diffLines = remember(file.diff) {
            if (file.diff.isBlank()) emptyList() else file.diff.lines()
        }

        if (diffLines.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("该文件无差异或为新建/空文件/二进制文件", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            val hScroll = rememberScrollState()
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0D1117))
                    .horizontalScroll(hScroll)
            ) {
                items(diffLines) { line ->
                    DiffLineRow(line = line)
                }
            }
        }
    }
}

@Composable
fun DiffLineRow(line: String) {
    val (bgColor, textColor, isBold) = when {
        line.startsWith("@@") -> Triple(Color(0x1F38BDF8), Color(0xFF38BDF8), true)
        line.startsWith("+") && !line.startsWith("+++") -> Triple(Color(0x2E10B981), Color(0xFF34D399), false)
        line.startsWith("-") && !line.startsWith("---") -> Triple(Color(0x2EEF4444), Color(0xFFF87171), false)
        line.startsWith("diff ") || line.startsWith("index ") || line.startsWith("--- ") || line.startsWith("+++ ") -> Triple(Color(0x1A6B7280), Color(0xFF9CA3AF), true)
        else -> Triple(Color.Transparent, Color(0xFFC9D1D9), false)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .padding(horizontal = 8.dp, vertical = 1.dp)
    ) {
        Text(
            text = line.ifEmpty { " " },
            fontFamily = FontFamily.Monospace,
            fontSize = 11.5.sp,
            lineHeight = 16.sp,
            color = textColor,
            fontWeight = if (isBold) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
fun SourceViewerTab(
    filePath: String,
    content: String?,
    onCopyContent: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = filePath,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            if (!content.isNullOrBlank()) {
                TextButton(
                    onClick = onCopyContent,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("复制代码", fontSize = 12.sp)
                }
            }
        }

        if (content == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("正在读取文件源码...", style = MaterialTheme.typography.bodySmall)
            }
        } else {
            val lines = remember(content) { content.lines() }
            val hScroll = rememberScrollState()
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0D1117))
                    .horizontalScroll(hScroll)
            ) {
                items(lines.size) { idx ->
                    val lineNum = idx + 1
                    val lineText = lines[idx]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 1.dp)
                    ) {
                        Text(
                            text = String.format("%4d ", lineNum),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = Color(0xFF6E7681),
                            modifier = Modifier.width(42.dp)
                        )
                        Text(
                            text = lineText.ifEmpty { " " },
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.5.sp,
                            color = Color(0xFFC9D1D9)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AiReviewTab(
    state: CodeReviewState,
    projectName: String,
    onStartAiReview: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Grok Review Header Card
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Grok 智能代码审查体系 (Review Agent)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "基于 Grok Build 原生 /review 规范，全方位审查项目的改动，并由 Grok 智能体直接在当前会话中输出针对性优化方案和代码整改片段。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                )

                Spacer(modifier = Modifier.height(14.dp))

                if (state.isReviewing) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Grok 正在会话中生成代码审查分析...", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = onDismiss,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("关闭面板，进入对话查看实时输出")
                            }
                        }
                    }
                } else {
                    Button(
                        onClick = onStartAiReview,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("立即开始 Grok 深度审查 (${if (state.scope == "working") "工作区改动" else "最新提交"})", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Four Review Pillars Checklist
        Text(
            text = "审查维度与标准 (Review Rubric)",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ReviewPillarCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.AccountTree,
                title = "1. 架构与模块化",
                description = "检查设计模式、是否破坏现有分层、是否过度设计、模块高内聚低耦合。"
            )
            ReviewPillarCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.BugReport,
                title = "2. 缺陷与边界",
                description = "检查空安全、越界访问、并发与竞态、资源泄露以及异常分支处理。"
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ReviewPillarCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.CleaningServices,
                title = "3. 代码异味与冗余",
                description = "识别冗余分支、死代码、深层嵌套、魔数以及不必要的全局状态。"
            )
            ReviewPillarCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Bolt,
                title = "4. 极简化 (Code Judo)",
                description = "通过最简单的语言原生特性替代复杂外挂框架，实现代码行数减半但健壮性翻倍。"
            )
        }
    }
}

@Composable
fun ReviewPillarCard(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.5.sp,
                lineHeight = 16.sp
            )
        }
    }
}

