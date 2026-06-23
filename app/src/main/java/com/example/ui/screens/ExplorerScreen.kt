@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.database.CloudAccount
import com.example.data.repository.ExplorerTab
import com.example.data.repository.VFile
import com.example.ui.viewmodel.RiaViewModel

@Composable
fun ExplorerScreen(viewModel: RiaViewModel) {
    val accounts by viewModel.accounts.collectAsState()
    val tabs by viewModel.tabs.collectAsState()
    val selectedTabId by viewModel.selectedTabId.collectAsState()
    val currentFiles by viewModel.currentTabFiles.collectAsState()
    val currentTab by viewModel.currentTab.collectAsState()
    val clipboard by viewModel.clipboard.collectAsState()

    // Global Search State Flows from ViewModel
    val globalSearchQuery by viewModel.searchQuery.collectAsState()
    val globalSearchResults by viewModel.searchResults.collectAsState()

    // Dialog state controllers
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var selectedRenameFile by remember { mutableStateOf<VFile?>(null) }
    var showAddTabDialog by remember { mutableStateOf(false) }
    var showEncryptPasteDialog by remember { mutableStateOf(false) }

    // Search filter state
    var searchQuery by remember { mutableStateOf("") }
    var isGlobalSearchEnabled by remember { mutableStateOf(false) }

    // Android File System API state variables
    val context = androidx.compose.ui.platform.LocalContext.current
    var isRealLocalFSEnabled by remember { mutableStateOf(false) }
    var realLocalPath by remember { mutableStateOf(context.filesDir.absolutePath) }

    // Multi-Selection State
    var isMultiSelectActive by remember { mutableStateOf(false) }
    var selectedFiles by remember { mutableStateOf(setOf<VFile>()) }

    // Helper to refresh files when doing file system operations
    var fsChangeCounter by remember { mutableStateOf(0) }

    val filesToDisplay = remember(currentFiles, isRealLocalFSEnabled, realLocalPath, searchQuery, isGlobalSearchEnabled, globalSearchResults, fsChangeCounter) {
        if (isGlobalSearchEnabled) {
            emptyList() // Rendered separately by group
        } else if (isRealLocalFSEnabled && currentTab?.accountId == 1) {
            // Read from actual filesDir using Android File System APIs
            getRealLocalFilesList(realLocalPath, context).filter {
                it.name.contains(searchQuery, ignoreCase = true)
            }
        } else {
            currentFiles.filter {
                it.name.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("explorer_screen")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 1. CAROUSEL OF MULTIPLE CHANNELS/TABS
            TabCarouselSection(
                tabs = tabs,
                selectedTabId = selectedTabId,
                onTabSelected = { 
                    viewModel.selectTab(it)
                    isMultiSelectActive = false
                    selectedFiles = emptySet()
                },
                onTabClosed = { viewModel.closeTab(it) },
                onAddTabClicked = { showAddTabDialog = true }
            )

            Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

            if (currentTab != null) {
                // 2. PATH BREADCRUMBS & NAVIGATION TOOLS
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(
                            onClick = {
                                if (isRealLocalFSEnabled && currentTab?.accountId == 1) {
                                    if (realLocalPath != context.filesDir.absolutePath) {
                                        realLocalPath = java.io.File(realLocalPath).parent ?: context.filesDir.absolutePath
                                    }
                                } else {
                                    viewModel.goBackInTab(currentTab!!.id)
                                }
                            },
                            enabled = if (isRealLocalFSEnabled && currentTab?.accountId == 1) realLocalPath != context.filesDir.absolutePath else currentTab!!.historyIndex > 0,
                            modifier = Modifier.size(32.dp).testTag("explorer_back")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ChevronLeft,
                                contentDescription = "Go back",
                                tint = if (isRealLocalFSEnabled && currentTab?.accountId == 1) {
                                    if (realLocalPath != context.filesDir.absolutePath) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                } else {
                                    if (currentTab!!.historyIndex > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                }
                            )
                        }

                        // Path Breadcrumbs text representation
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = if (isRealLocalFSEnabled && currentTab?.accountId == 1) {
                                    realLocalPath.removePrefix(context.filesDir.parent ?: "")
                                } else {
                                    currentTab!!.currentPath
                                },
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = FontWeight.Medium,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (isMultiSelectActive) {
                            IconButton(
                                onClick = {
                                    isMultiSelectActive = false
                                    selectedFiles = emptySet()
                                },
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(8.dp))
                                    .size(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Cancel,
                                    contentDescription = "Cancel Multi-Select",
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        } else {
                            IconButton(
                                onClick = { showCreateFolderDialog = true },
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(8.dp))
                                    .size(34.dp)
                                    .testTag("create_folder_fab")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CreateNewFolder,
                                    contentDescription = "Create Folder",
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    // SEARCH & INTERACTIVE TOGGLES
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = {
                                searchQuery = it
                                if (isGlobalSearchEnabled) {
                                    viewModel.updateSearchQuery(it)
                                }
                            },
                            placeholder = { 
                                Text(
                                    if (isGlobalSearchEnabled) "Search globally across all clouds..." 
                                    else if (isRealLocalFSEnabled && currentTab?.accountId == 1) "Search real local files..."
                                    else "Filter current directory...", 
                                    fontSize = 12.sp
                                ) 
                            },
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Default.Search, null, modifier = Modifier.size(18.dp)) },
                            trailingIcon = {
                                FilterChip(
                                    selected = isGlobalSearchEnabled,
                                    onClick = { 
                                        isGlobalSearchEnabled = !isGlobalSearchEnabled
                                        if (isGlobalSearchEnabled) {
                                            viewModel.updateSearchQuery(searchQuery)
                                        }
                                    },
                                    label = { Text("Global", fontSize = 10.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    ),
                                    modifier = Modifier.height(28.dp).padding(end = 4.dp).testTag("global_search_chip")
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("explorer_search"),
                            shape = RoundedCornerShape(8.dp),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent
                            )
                        )
                    }

                    // Local FS Mode Selector (only show in Local Tab)
                    if (currentTab?.accountId == 1) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Local Mode:",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            FilterChip(
                                selected = !isRealLocalFSEnabled,
                                onClick = { 
                                    isRealLocalFSEnabled = false 
                                    isMultiSelectActive = false
                                    selectedFiles = emptySet()
                                    fsChangeCounter++
                                },
                                label = { Text("Virtual Cloud Seeding", fontSize = 11.sp) }
                            )
                            FilterChip(
                                selected = isRealLocalFSEnabled,
                                onClick = { 
                                    isRealLocalFSEnabled = true 
                                    isMultiSelectActive = false
                                    selectedFiles = emptySet()
                                    fsChangeCounter++
                                },
                                label = { Text("Android System Files", fontSize = 11.sp) }
                            )
                        }
                    }
                }

                // 3. MAIN CONTAINER (GLOBAL SEARCH RESULTS OR CURRENT DIRECTORY LIST)
                if (isGlobalSearchEnabled) {
                    val groupedResults = remember(globalSearchResults) {
                        globalSearchResults.groupBy { it.first }
                    }

                    if (groupedResults.isNotEmpty()) {
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentPadding = PaddingValues(bottom = 96.dp)
                        ) {
                            groupedResults.forEach { (accId, resultsList) ->
                                val accName = accounts.find { it.id == accId }?.name ?: "Local Storage"
                                item {
                                    Surface(
                                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (accId == 1) Icons.Default.FolderOpen else Icons.Default.CloudQueue,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = accName,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                        }
                                    }
                                }

                                items(resultsList) { (_, file) ->
                                    FileRowItem(
                                        file = file,
                                        onClick = {
                                            // Quick navigate to folder or copy
                                            if (file.isFolder) {
                                                val matchingTab = tabs.find { it.accountId == accId }
                                                if (matchingTab != null) {
                                                    viewModel.selectTab(matchingTab.id)
                                                    viewModel.enterFolder(matchingTab.id, file.path)
                                                } else {
                                                    viewModel.createNewTab(accId, accName)
                                                }
                                                isGlobalSearchEnabled = false
                                            } else {
                                                viewModel.copyToClipboard(file, accId)
                                            }
                                        },
                                        onCopy = { viewModel.copyToClipboard(file, accId) },
                                        onCut = { viewModel.cutToClipboard(file, accId) },
                                        onDelete = { 
                                            viewModel.deleteFile(file.name)
                                            fsChangeCounter++
                                        },
                                        onRename = {
                                            selectedRenameFile = file
                                            showRenameDialog = true
                                        }
                                    )
                                }
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            EmptyFolderState(isSearchActive = true)
                        }
                    }
                } else {
                    // MAIN DIRECTORY LIST
                    if (filesToDisplay.isNotEmpty()) {
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentPadding = PaddingValues(bottom = 96.dp)
                        ) {
                            items(filesToDisplay) { file ->
                                val isSelected = selectedFiles.contains(file)
                                FileRowItem(
                                    file = file,
                                    isMultiSelectActive = isMultiSelectActive,
                                    isSelected = isSelected,
                                    onSelectedChange = { selected ->
                                        isMultiSelectActive = true
                                        val updated = selectedFiles.toMutableSet()
                                        if (selected) updated.add(file) else updated.remove(file)
                                        selectedFiles = updated
                                        if (selectedFiles.isEmpty()) {
                                            isMultiSelectActive = false
                                        }
                                    },
                                    onClick = {
                                        if (file.isFolder) {
                                            if (isRealLocalFSEnabled && currentTab?.accountId == 1) {
                                                realLocalPath = file.path
                                            } else {
                                                viewModel.enterFolder(currentTab!!.id, file.path)
                                            }
                                        }
                                    },
                                    onCopy = { viewModel.copyToClipboard(file, currentTab!!.accountId) },
                                    onCut = { viewModel.cutToClipboard(file, currentTab!!.accountId) },
                                    onDelete = {
                                        if (isRealLocalFSEnabled && currentTab?.accountId == 1) {
                                            java.io.File(file.path).delete()
                                            fsChangeCounter++
                                        } else {
                                            viewModel.deleteFile(file.name)
                                        }
                                    },
                                    onRename = {
                                        selectedRenameFile = file
                                        showRenameDialog = true
                                    }
                                )
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            EmptyFolderState(isSearchActive = searchQuery.isNotEmpty())
                        }
                    }
                }
            }
        }

        // FLOATING MULTI-SELECT COMMAND BAR
        if (isMultiSelectActive && selectedFiles.isNotEmpty()) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 80.dp, start = 16.dp, end = 16.dp)
                    .fillMaxWidth()
                    .testTag("multi_select_bar"),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Selected: ${selectedFiles.size} items",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(
                            onClick = {
                                val firstFile = selectedFiles.first()
                                viewModel.copyToClipboard(firstFile, currentTab!!.accountId)
                                isMultiSelectActive = false
                                selectedFiles = emptySet()
                            }
                        ) {
                            Icon(Icons.Default.ContentCopy, "Bulk Copy", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }

                        IconButton(
                            onClick = {
                                if (isRealLocalFSEnabled && currentTab?.accountId == 1) {
                                    selectedFiles.forEach { file ->
                                        java.io.File(file.path).delete()
                                    }
                                    fsChangeCounter++
                                } else {
                                    val names = selectedFiles.map { it.name }
                                    viewModel.deleteFilesBulk(names)
                                }
                                isMultiSelectActive = false
                                selectedFiles = emptySet()
                            }
                        ) {
                            Icon(Icons.Default.Delete, "Bulk Delete", tint = MaterialTheme.colorScheme.error)
                        }

                        IconButton(
                            onClick = {
                                isMultiSelectActive = false
                                selectedFiles = emptySet()
                            }
                        ) {
                            Icon(Icons.Default.Close, "Clear Selection", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                }
            }
        }

        // 4. FLOATING CLIPBOARD STICKY BANNER
        if (clipboard != null) {
            val sourceAccountName = accounts.find { it.id == clipboard!!.sourceAccountId }?.name ?: "Remote"
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp, start = 16.dp, end = 16.dp)
            ) {
                ClipboardStickyBanner(
                    fileName = clipboard!!.file.name,
                    sourceAccountName = sourceAccountName,
                    isCut = clipboard!!.isCut,
                    onPasteNormal = { viewModel.pasteClipboard() },
                    onPasteEncrypted = { showEncryptPasteDialog = true },
                    onCancel = { viewModel.copyToClipboard(currentFiles.first(), -1) /* reset */ }
                )
            }
        }
    }

    // ==========================================
    // OVERLAY DIALOGS DEFINITIONS
    // ==========================================

    if (showCreateFolderDialog) {
        var folderName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateFolderDialog = false },
            title = { Text("Create Folder") },
            text = {
                OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it },
                    label = { Text("Folder Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("create_folder_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (folderName.isNotBlank()) {
                            if (isRealLocalFSEnabled && currentTab?.accountId == 1) {
                                java.io.File(realLocalPath, folderName).mkdir()
                                fsChangeCounter++
                            } else {
                                viewModel.createNewFolder(folderName)
                            }
                            showCreateFolderDialog = false
                        }
                    },
                    modifier = Modifier.testTag("confirm_create_folder")
                ) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFolderDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showRenameDialog && selectedRenameFile != null) {
        var newName by remember { mutableStateOf(selectedRenameFile!!.name) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename File/Folder") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("rename_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newName.isNotBlank() && newName != selectedRenameFile!!.name) {
                            if (isRealLocalFSEnabled && currentTab?.accountId == 1) {
                                java.io.File(selectedRenameFile!!.path).renameTo(java.io.File(realLocalPath, newName))
                                fsChangeCounter++
                            } else {
                                viewModel.renameFile(selectedRenameFile!!.name, newName)
                            }
                            showRenameDialog = false
                        }
                    },
                    modifier = Modifier.testTag("confirm_rename")
                ) { Text("Rename") }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showAddTabDialog) {
        AlertDialog(
            onDismissRequest = { showAddTabDialog = false },
            title = { Text("Select Cloud Account for New Tab") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    accounts.forEach { account ->
                        Card(
                            onClick = {
                                viewModel.createNewTab(account.id, account.name)
                                showAddTabDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = getAccountIcon(account.type),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Column {
                                    Text(account.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text(account.type, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showAddTabDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showEncryptPasteDialog) {
        var password by remember { mutableStateOf("") }
        var confirmPassword by remember { mutableStateOf("") }
        var isError by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showEncryptPasteDialog = false },
            title = { Text("Encryption Upload Password") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Configure a secure password. The pasted file will be robustly password-encrypted during upload to keep your data safe.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it; isError = false },
                        label = { Text("Password") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("encrypt_paste_pwd")
                    )
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it; isError = false },
                        label = { Text("Confirm Password") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("encrypt_paste_pwd_confirm")
                    )
                    if (isError) {
                        Text("Passwords do not match or are empty!", color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (password.isNotEmpty() && password == confirmPassword) {
                            viewModel.pasteClipboard(customizeEncryption = true, password = password)
                            showEncryptPasteDialog = false
                        } else {
                            isError = true
                        }
                    },
                    modifier = Modifier.testTag("confirm_encrypt_paste")
                ) { Text("Safe Upload") }
            },
            dismissButton = {
                TextButton(onClick = { showEncryptPasteDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun TabCarouselSection(
    tabs: List<ExplorerTab>,
    selectedTabId: String,
    onTabSelected: (String) -> Unit,
    onTabClosed: (String) -> Unit,
    onAddTabClicked: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 12.dp, horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        tabs.forEach { tab ->
            val isSelected = tab.id == selectedTabId
            val containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
            val contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant

            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .combinedClickable(
                        onClick = { onTabSelected(tab.id) }
                    ),
                color = containerColor,
                contentColor = contentColor,
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (tab.accountId == 1) Icons.Default.FolderOpen else Icons.Default.CloudQueue,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )

                    Text(
                        text = tab.title,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        modifier = Modifier.widthIn(max = 100.dp),
                        overflow = TextOverflow.Ellipsis
                    )

                    if (tabs.size > 1) {
                        IconButton(
                            onClick = { onTabClosed(tab.id) },
                            modifier = Modifier.size(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close tab",
                                modifier = Modifier.size(12.dp),
                                tint = contentColor.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        }

        // Add Tab Button "+"
        IconButton(
            onClick = onAddTabClicked,
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), CircleShape)
                .size(36.dp)
                .testTag("add_explorer_tab_btn")
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "New Exploration Tab",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
fun ExplorerNavigationHeader(
    currentTab: ExplorerTab,
    onBackClicked: () -> Unit,
    onNewFolderClicked: () -> Unit,
    searchQuery: String,
    onSearchQueryChanged: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = onBackClicked,
                enabled = currentTab.historyIndex > 0,
                modifier = Modifier.size(32.dp).testTag("explorer_back")
            ) {
                Icon(
                    imageVector = Icons.Default.ChevronLeft,
                    contentDescription = "Go back",
                    tint = if (currentTab.historyIndex > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                )
            }

            // Path Breadcrumbs text representation
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = currentTab.currentPath,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Medium,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(
                onClick = onNewFolderClicked,
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(8.dp))
                    .size(34.dp)
                    .testTag("create_folder_fab")
            ) {
                Icon(
                    imageVector = Icons.Default.CreateNewFolder,
                    contentDescription = "Create Folder",
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // Real-time search bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChanged,
            placeholder = { Text("Filter current directory...", fontSize = 12.sp) },
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, null, modifier = Modifier.size(18.dp)) },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("explorer_search"),
            shape = RoundedCornerShape(8.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent
            )
        )
    }
}

@Composable
fun FileRowItem(
    file: VFile,
    isMultiSelectActive: Boolean = false,
    isSelected: Boolean = false,
    onSelectedChange: (Boolean) -> Unit = {},
    onClick: () -> Unit,
    onCopy: () -> Unit,
    onCut: () -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit
) {
    var expandedMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {
                    if (isMultiSelectActive) {
                        onSelectedChange(!isSelected)
                    } else {
                        onClick()
                    }
                },
                onLongClick = {
                    if (!isMultiSelectActive) {
                        onSelectedChange(true)
                    } else {
                        expandedMenu = true
                    }
                }
            )
            .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (isMultiSelectActive) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = onSelectedChange,
                modifier = Modifier.testTag("file_checkbox_${file.name}")
            )
        }

        val fileIcon = when {
            file.isFolder -> Icons.Default.Folder
            file.isEncrypted -> Icons.Default.Lock
            file.name.endsWith(".zip") || file.name.endsWith(".tar.gz") -> Icons.Default.FolderZip
            file.name.endsWith(".pdf") -> Icons.Outlined.PictureAsPdf
            file.name.endsWith(".jpg") || file.name.endsWith(".png") -> Icons.Default.Image
            else -> Icons.Default.InsertDriveFile
        }

        val iconTint = when {
            file.isFolder -> MaterialTheme.colorScheme.primary
            file.isEncrypted -> MaterialTheme.colorScheme.tertiary
            else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
        }

        Box(
            modifier = Modifier
                .size(40.dp)
                .background(iconTint.copy(alpha = 0.08f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = fileIcon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = file.name,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (file.isFolder) "Folder" else String.format("%.2f MB", file.size / (1024.0 * 1024.0)),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }

        Box {
            IconButton(
                onClick = { expandedMenu = true },
                modifier = Modifier.size(32.dp).testTag("file_options_${file.name}")
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "File Options",
                    modifier = Modifier.size(18.dp)
                )
            }

            DropdownMenu(
                expanded = expandedMenu,
                onDismissRequest = { expandedMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(16.dp)); Text("Copy") } },
                    onClick = { onCopy(); expandedMenu = false }
                )
                DropdownMenuItem(
                    text = { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Icon(Icons.Default.ContentCut, null, modifier = Modifier.size(16.dp)); Text("Cut") } },
                    onClick = { onCut(); expandedMenu = false }
                )
                DropdownMenuItem(
                    text = { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Icon(Icons.Default.Edit, null, modifier = Modifier.size(16.dp)); Text("Rename") } },
                    onClick = { onRename(); expandedMenu = false }
                )
                DropdownMenuItem(
                    text = { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp)); Text("Delete", color = MaterialTheme.colorScheme.error) } },
                    onClick = { onDelete(); expandedMenu = false }
                )
            }
        }
    }
}

@Composable
fun ClipboardStickyBanner(
    fileName: String,
    sourceAccountName: String,
    isCut: Boolean,
    onPasteNormal: () -> Unit,
    onPasteEncrypted: () -> Unit,
    onCancel: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("clipboard_sticky_banner"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isCut) Icons.Default.ContentCut else Icons.Default.ContentCopy,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isCut) "Cut: $fileName" else "Copied: $fileName",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = "Source: $sourceAccountName",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                )
            }

            // Normal Paste
            IconButton(
                onClick = onPasteNormal,
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                    .size(32.dp)
                    .testTag("paste_normal_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.ContentPaste,
                    contentDescription = "Paste Here",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(16.dp)
                )
            }

            // Encrypted Paste with Lock trigger
            IconButton(
                onClick = onPasteEncrypted,
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.tertiary, CircleShape)
                    .size(32.dp)
                    .testTag("paste_encrypted_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Paste Encrypted",
                    tint = MaterialTheme.colorScheme.onTertiary,
                    modifier = Modifier.size(16.dp)
                )
            }

            // Cancel paste container Clear clipboard
            IconButton(
                onClick = onCancel,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Cancel clipboard action",
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
fun EmptyFolderState(isSearchActive: Boolean) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(24.dp)
    ) {
        Icon(
            imageVector = if (isSearchActive) Icons.Default.SearchOff else Icons.Outlined.FolderOpen,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(48.dp)
        )
        Text(
            text = if (isSearchActive) "No matches found" else "This folder is empty",
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
        Text(
            text = if (isSearchActive) "Try redefining your search query." else "You can create directory levels or copy files into this path.",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// Helpers for type matching
fun getAccountIcon(type: String): ImageVector {
    return when (type) {
        "Local" -> Icons.Default.FolderOpen
        "Google Drive" -> Icons.Default.CloudQueue
        "OneDrive" -> Icons.Default.CloudUpload
        "S3" -> Icons.Default.Storage
        "FTP" -> Icons.Default.Dns
        "SFTP" -> Icons.Default.Security
        "WebDAV" -> Icons.Default.SettingsEthernet
        "Dropbox" -> Icons.Default.FolderShared
        else -> Icons.Default.Cloud
    }
}

// Android File System API - Reads actual files and directories in standard context.filesDir sandbox
fun getRealLocalFilesList(currentPath: String, context: android.content.Context): List<VFile> {
    val dir = java.io.File(currentPath)
    if (!dir.exists() || !dir.isDirectory) {
        return emptyList()
    }
    // Seed some directories under filesDir if we are there to give a beautiful starting point
    if (currentPath == context.filesDir.absolutePath) {
        val docs = java.io.File(dir, "Documents")
        if (!docs.exists()) docs.mkdir()
        val dls = java.io.File(dir, "Downloads")
        if (!dls.exists()) dls.mkdir()
        val backups = java.io.File(dir, "Backups")
        if (!backups.exists()) backups.mkdir()
        
        val doc1 = java.io.File(docs, "cloud_vault_seed_ledger.enc")
        if (!doc1.exists()) doc1.writeText("RSA PRIVATE KEY - SYSTEM ACCESS")
        val doc2 = java.io.File(docs, "audit_checklist.txt")
        if (!doc2.exists()) doc2.writeText("All secure connections verified successfully on local storage.")
    }

    val results = mutableListOf<VFile>()
    dir.listFiles()?.forEach { file ->
        results.add(
            VFile(
                name = file.name,
                path = file.absolutePath,
                size = file.length(),
                isFolder = file.isDirectory,
                isEncrypted = file.name.endsWith(".enc") || file.name.endsWith(".pem"),
                lastModified = file.lastModified()
            )
        )
    }
    // Sort directories first, then files
    return results.sortedWith(compareBy({ !it.isFolder }, { it.name }))
}
