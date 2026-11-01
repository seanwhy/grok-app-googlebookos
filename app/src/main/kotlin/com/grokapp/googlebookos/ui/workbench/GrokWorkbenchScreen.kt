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
import com.grokapp.googlebookos.util.StorageHelper
import java.io.File
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
                        onRefreshQuota = { viewModel.loadSubscriptionQuota() }
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
                            onRefreshQuota = { viewModel.loadSubscriptionQuota() }
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
    onRefreshQuota: () -> Unit = {}
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

@Composable
fun ToolCallCard(tool: ToolCallInfo) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }

    val (toolIcon, iconTint) = remember(tool.name) {
        when {
            tool.name.contains("终端") || tool.name.contains("terminal") ->
                Icons.Default.Terminal to Color(0xFF38BDF8)
            tool.name.contains("读取") || tool.name.contains("read") ->
                Icons.Default.Description to Color(0xFF818CF8)
            tool.name.contains("修改") || tool.name.contains("编辑") || tool.name.contains("replace") ->
                Icons.Default.Edit to Color(0xFFF59E0B)
            tool.name.contains("新建") || tool.name.contains("write") ->
                Icons.Default.NoteAdd to Color(0xFF10B981)
            tool.name.contains("目录") || tool.name.contains("dir") ->
                Icons.Default.Folder to Color(0xFFFBBF24)
            tool.name.contains("搜索") || tool.name.contains("grep") ->
                Icons.Default.Search to Color(0xFFA78BFA)
            tool.name.contains("联网") || tool.name.contains("web") ->
                Icons.Default.Language to Color(0xFF34D399)
            tool.name.contains("智能体") || tool.name.contains("agent") ->
                Icons.Default.SmartToy to Color(0xFFEC4899)
            tool.name.contains("计划") || tool.name.contains("plan") ->
                Icons.Default.Assignment to Color(0xFF60A5FA)
            else ->
                Icons.Default.Build to Color(0xFF94A3B8)
        }
    }

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

