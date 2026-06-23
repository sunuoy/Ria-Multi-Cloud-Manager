package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Loop
import androidx.compose.material.icons.outlined.SyncLock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.database.CloudAccount
import com.example.data.database.SyncJob
import com.example.ui.viewmodel.RiaViewModel

@Composable
fun SyncScreen(viewModel: RiaViewModel) {
    val accounts by viewModel.accounts.collectAsState()
    val syncJobs by viewModel.syncJobs.collectAsState()

    var showCreateSyncDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("sync_screen")
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // HEADER ROW WITH ACTION
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Transfer & Sync",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Configure custom backups & automatic schedules",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Button(
                onClick = { showCreateSyncDialog = true },
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("add_sync_job_btn")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                    Text("New Sync")
                }
            }
        }

        // SYNC JOBS MAIN SCROLL VIEW
        if (syncJobs.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(syncJobs) { job ->
                    val sourceAccount = if (job.sourceAccountId == -1) "Local Device" else accounts.find { it.id == job.sourceAccountId }?.name ?: "Unknown Cloud"
                    val destAccount = if (job.destAccountId == -1) "Local Device" else accounts.find { it.id == job.destAccountId }?.name ?: "Unknown Cloud"

                    SyncJobCard(
                        job = job,
                        sourceAccountName = sourceAccount,
                        destAccountName = destAccount,
                        onRunNow = { viewModel.triggerSyncImmediately(job) },
                        onDelete = { viewModel.deleteSyncJob(job) }
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                SyncEmptyState(onAddClicked = { showCreateSyncDialog = true })
            }
        }
    }

    // CREATE SYNC TASK FULL DIALOG
    if (showCreateSyncDialog) {
        var syncName by remember { mutableStateOf("") }
        var selectedSourceId by remember { mutableStateOf(-1) }
        var sourcePath by remember { mutableStateOf("") }
        var selectedDestId by remember { mutableStateOf(-1) }
        var destPath by remember { mutableStateOf("") }
        var syncFreq by remember { mutableStateOf("Manual") }
        var encryptEnabled by remember { mutableStateOf(false) }
        var encryptPassword by remember { mutableStateOf("") }

        var expandedSrcGroup by remember { mutableStateOf(false) }
        var expandedDestGroup by remember { mutableStateOf(false) }
        var expandedFreqGroup by remember { mutableStateOf(false) }

        var isInputValidationError by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showCreateSyncDialog = false },
            title = { Text("Configure Multi-Cloud Sync Tunnel") },
            text = {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        OutlinedTextField(
                            value = syncName,
                            onValueChange = { syncName = it },
                            label = { Text("Sync Tunnel Name") },
                            placeholder = { Text("e.g. Weekly Photos Backup") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("sync_name_input")
                        )
                    }

                    // SOURCE SELECTOR SECTION
                    item {
                        Text("Source Parameters", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Box(modifier = Modifier.fillMaxWidth()) {
                            val selectedSrcItem = if (selectedSourceId == -1) "Local Storage" else accounts.find { it.id == selectedSourceId }?.name ?: "Local Storage"
                            OutlinedButton(
                                onClick = { expandedSrcGroup = true },
                                modifier = Modifier.fillMaxWidth().testTag("source_account_dropdown_btn")
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("From: $selectedSrcItem", fontSize = 13.sp)
                                    Icon(Icons.Default.ArrowDropDown, null)
                                }
                            }

                            DropdownMenu(
                                expanded = expandedSrcGroup,
                                onDismissRequest = { expandedSrcGroup = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Local Storage") },
                                    onClick = { selectedSourceId = -1; expandedSrcGroup = false }
                                )
                                accounts.forEach { acc ->
                                    DropdownMenuItem(
                                        text = { Text(acc.name) },
                                        onClick = { selectedSourceId = acc.id; expandedSrcGroup = false }
                                    )
                                }
                            }
                        }
                    }

                    item {
                        OutlinedTextField(
                            value = sourcePath,
                            onValueChange = { sourcePath = it },
                            label = { Text("Source Folder Catalog Path") },
                            placeholder = { Text("e.g. /Documents") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("source_path_input")
                        )
                    }

                    // DESTINATION SELECTOR SECTION
                    item {
                        Text("Destination Parameters", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Box(modifier = Modifier.fillMaxWidth()) {
                            val selectedDestItem = if (selectedDestId == -1) "Local Storage" else accounts.find { it.id == selectedDestId }?.name ?: "Local Storage"
                            OutlinedButton(
                                onClick = { expandedDestGroup = true },
                                modifier = Modifier.fillMaxWidth().testTag("dest_account_dropdown_btn")
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("To: $selectedDestItem", fontSize = 13.sp)
                                    Icon(Icons.Default.ArrowDropDown, null)
                                }
                            }

                            DropdownMenu(
                                expanded = expandedDestGroup,
                                onDismissRequest = { expandedDestGroup = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Local Storage") },
                                    onClick = { selectedDestId = -1; expandedDestGroup = false }
                                )
                                accounts.forEach { acc ->
                                    DropdownMenuItem(
                                        text = { Text(acc.name) },
                                        onClick = { selectedDestId = acc.id; expandedDestGroup = false }
                                    )
                                }
                            }
                        }
                    }

                    item {
                        OutlinedTextField(
                            value = destPath,
                            onValueChange = { destPath = it },
                            label = { Text("Destination Storage Path") },
                            placeholder = { Text("e.g. /RemoteBackup") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("dest_path_input")
                        )
                    }

                    // FREQUENCY SELECTOR
                    item {
                        Text("Scheduler Configuration", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = { expandedFreqGroup = true },
                                modifier = Modifier.fillMaxWidth().testTag("freq_dropdown_btn")
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Frequency: $syncFreq", fontSize = 13.sp)
                                    Icon(Icons.Default.ArrowDropDown, null)
                                }
                            }

                            DropdownMenu(
                                expanded = expandedFreqGroup,
                                onDismissRequest = { expandedFreqGroup = false }
                            ) {
                                val options = listOf("Manual", "Hourly", "Daily", "Continuous")
                                options.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option) },
                                        onClick = { syncFreq = option; expandedFreqGroup = false }
                                    )
                                }
                            }
                        }
                    }

                    // ENCRYPTION METADATA TOGGLER
                    item {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(16.dp))
                                    Text("Password Encryption", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }

                                Switch(
                                    checked = encryptEnabled,
                                    onCheckedChange = { encryptEnabled = it },
                                    modifier = Modifier.testTag("encrypt_toggle")
                                )
                            }

                            if (encryptEnabled) {
                                OutlinedTextField(
                                    value = encryptPassword,
                                    onValueChange = { encryptPassword = it },
                                    label = { Text("Encryption password key") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().testTag("encrypt_password_input")
                                )
                            }
                        }
                    }

                    if (isInputValidationError) {
                        item {
                            Text(
                                "Please verify source/destination paths and secure credentials!",
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (syncName.isNotBlank() && sourcePath.isNotBlank() && destPath.isNotBlank()) {
                            if (encryptEnabled && encryptPassword.isBlank()) {
                                isInputValidationError = true
                            } else {
                                viewModel.addSyncJob(
                                    name = syncName,
                                    srcAccId = selectedSourceId,
                                    srcPath = sourcePath,
                                    destAccId = selectedDestId,
                                    destPath = destPath,
                                    freq = syncFreq,
                                    encrypt = encryptEnabled,
                                    psswd = encryptPassword
                                )
                                showCreateSyncDialog = false
                            }
                        } else {
                            isInputValidationError = true
                        }
                    },
                    modifier = Modifier.testTag("confirm_create_sync")
                ) { Text("Deploy Tunnel") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateSyncDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun SyncJobCard(
    job: SyncJob,
    sourceAccountName: String,
    destAccountName: String,
    onRunNow: () -> Unit,
    onDelete: () -> Unit
) {
    var expandedJobOptions by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Main row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (job.encryptWithPassword) Icons.Default.SyncLock else Icons.Default.Sync,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Column {
                        Text(job.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("Frequency: ${job.frequency}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Box {
                    IconButton(
                        onClick = { expandedJobOptions = true },
                        modifier = Modifier.size(32.dp).testTag("sync_job_opt_${job.name}")
                    ) {
                        Icon(Icons.Default.MoreVert, "Sync Options", modifier = Modifier.size(18.dp))
                    }

                    DropdownMenu(
                        expanded = expandedJobOptions,
                        onDismissRequest = { expandedJobOptions = false }
                    ) {
                        DropdownMenuItem(
                            text = { Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp)); Text("Delete Job", color = MaterialTheme.colorScheme.error) } },
                            onClick = { onDelete(); expandedJobOptions = false }
                        )
                    }
                }
            }

            // Connection representation: Source -> Dest
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("SOURCE", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text(sourceAccountName, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 1)
                    Text(job.sourcePath, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }

                Icon(
                    imageVector = Icons.Default.DoubleArrow,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )

                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text("DESTINATION", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text(destAccountName, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 1)
                    Text(job.destPath, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }

            // Status and actions row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val badgeColor = when (job.status) {
                        "Syncing" -> MaterialTheme.colorScheme.primary
                        "Completed" -> MaterialTheme.colorScheme.primary
                        "Failed" -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.outline
                    }
                    Box(
                        modifier = Modifier
                            .background(badgeColor.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = job.status.uppercase(),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = badgeColor
                        )
                    }

                    if (job.encryptWithPassword) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Encrypted Connection",
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (job.lastRunTime > 0L) "Last run: just recently" else "Last run: Never",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )

                    Button(
                        onClick = onRunNow,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                        shape = RoundedCornerShape(6.dp),
                        enabled = job.status != "Syncing",
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier
                            .height(32.dp)
                            .testTag("run_sync_job_${job.name}")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(14.dp))
                            Text("Run Now", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SyncEmptyState(onAddClicked: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(24.dp)
    ) {
        Icon(
            imageVector = Icons.Outlined.Loop,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(48.dp)
        )
        Text(
            text = "No active Sync Tunnels",
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
        Text(
            text = "Automate file replication across multiple storage catalogs dynamically with optional, offline cryptography protection.",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = onAddClicked,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.testTag("empty_sync_redirect")
        ) {
            Text("Create First sync Connection")
        }
    }
}
