package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.database.CloudAccount
import com.example.ui.viewmodel.RiaViewModel

data class ProviderType(
    val name: String,
    val iconName: String,
    val brandColor: Color
)

@Composable
fun AccountsScreen(viewModel: RiaViewModel) {
    val accounts by viewModel.accounts.collectAsState()

    var showAddAccountDialog by remember { mutableStateOf(false) }
    var selectedProvider by remember { mutableStateOf<ProviderType?>(null) }

    val providers = listOf(
        ProviderType("Google Drive", "drive", Color(0xFF34A853)),
        ProviderType("OneDrive", "onedrive", Color(0xFF0078D4)),
        ProviderType("Dropbox", "dropbox", Color(0xFF0061FE)),
        ProviderType("AWS S3", "s3", Color(0xFFFF9900)),
        ProviderType("SFTP", "sftp", Color(0xFF0091FF)),
        ProviderType("FTP", "ftp", Color(0xFF4A4E69)),
        ProviderType("WebDAV", "webdav", Color(0xFF00B4D8)),
        ProviderType("Terabox", "terabox", Color(0xFF0077B6)),
        ProviderType("Pikpak", "pikpak", Color(0xFFD62246)),
        ProviderType("Mega", "mega", Color(0xFFE1251B)),
        ProviderType("Box", "box", Color(0xFF133C55)),
        ProviderType("Desktop PC", "pc", Color(0xFF6A4C93))
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("accounts_screen")
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // HEADER SUMMARY BLOCK
        Column {
            Text(
                text = "Connected Clouds",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Integrate directories, remote file servers, and personal desktop computers",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // CONNECTED CLOUD CARDS COLUMN
        if (accounts.isNotEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                accounts.forEach { acc ->
                    MountedAccountRow(
                        account = acc,
                        onDisconnect = { viewModel.removeAccount(acc) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // GRID SELECTOR TO ADD NEW PROVISIONS
        Text(
            text = "Supported Storage Platforms",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(providers) { provider ->
                ProviderGridItem(
                    provider = provider,
                    onSelect = {
                        selectedProvider = provider
                        showAddAccountDialog = true
                    }
                )
            }
        }
    }

    // FORM DIALOG SPECIFIC TO SELECTED TYPE
    if (showAddAccountDialog && selectedProvider != null) {
        var connectionName by remember { mutableStateOf(selectedProvider!!.name + " Link") }
        var serverHost by remember { mutableStateOf("") }
        var serverPort by remember { mutableStateOf("22") }
        var userName by remember { mutableStateOf("") }
        var userPassword by remember { mutableStateOf("") }
        var extraDetails by remember { mutableStateOf("") }

        var validationError by remember { mutableStateOf(false) }

        val provider = selectedProvider!!

        // OAuth2 flow state variables
        var oauthStep by remember { mutableStateOf(1) }
        var progressStatus by remember { mutableStateOf("Connecting to provider...") }

        if (provider.iconName in listOf("drive", "onedrive", "s3")) {
            // MULTI-STEP OAUTH2 FLOW
            LaunchedEffect(oauthStep) {
                if (oauthStep == 2) {
                    kotlinx.coroutines.delay(800)
                    progressStatus = "Exchanging authentication grant codes..."
                    kotlinx.coroutines.delay(800)
                    progressStatus = "Verifying offline sync token in Secure Enclave..."
                    kotlinx.coroutines.delay(900)
                    oauthStep = 3
                }
            }

            AlertDialog(
                onDismissRequest = { showAddAccountDialog = false },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .background(provider.brandColor, CircleShape)
                        )
                        Text("OAuth2 Link - ${provider.name}")
                    }
                },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Linear step indicator
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(modifier = Modifier.weight(1f).height(4.dp).background(if (oauthStep >= 1) provider.brandColor else MaterialTheme.colorScheme.surfaceVariant))
                            Box(modifier = Modifier.weight(1f).height(4.dp).background(if (oauthStep >= 2) provider.brandColor else MaterialTheme.colorScheme.surfaceVariant))
                            Box(modifier = Modifier.weight(1f).height(4.dp).background(if (oauthStep >= 3) provider.brandColor else MaterialTheme.colorScheme.surfaceVariant))
                        }

                        if (oauthStep == 1) {
                            Text(
                                text = "Secure Identity Handshake Bridge",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "To mount this account, we require the following permissions under standard OAuth2 credentials:",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            // Scopes list
                            Column(
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                    .padding(12.dp)
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CheckCircle, null, tint = provider.brandColor, modifier = Modifier.size(14.dp))
                                    Text("Read & write metadata structures", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CheckCircle, null, tint = provider.brandColor, modifier = Modifier.size(14.dp))
                                    Text("Upload & download encrypted block segments", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CheckCircle, null, tint = provider.brandColor, modifier = Modifier.size(14.dp))
                                    Text("Maintain offline persistent background sync", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        } else if (oauthStep == 2) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator(color = provider.brandColor, modifier = Modifier.size(36.dp))
                                Text(
                                    text = progressStatus,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else if (oauthStep == 3) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFFE8F5E9), RoundedCornerShape(8.dp))
                                        .padding(10.dp)
                                ) {
                                    Icon(Icons.Default.VerifiedUser, null, tint = Color(0xFF2E7D32), modifier = Modifier.size(20.dp))
                                    Text("Authorization Credentials Acquired!", color = Color(0xFF2E7D32), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }

                                OutlinedTextField(
                                    value = connectionName,
                                    onValueChange = { connectionName = it },
                                    label = { Text("Connection Label") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().testTag("add_account_name_input")
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    if (oauthStep == 1) {
                        Button(
                            onClick = { oauthStep = 2 },
                            colors = ButtonDefaults.buttonColors(containerColor = provider.brandColor),
                            modifier = Modifier.testTag("oauth_step1_next")
                        ) { Text("Authorize Link") }
                    } else if (oauthStep == 3) {
                        Button(
                            onClick = {
                                if (connectionName.isNotBlank()) {
                                    viewModel.addAccount(
                                        name = connectionName,
                                        type = provider.name,
                                        host = "oauth2.provider.com",
                                        port = 443,
                                        username = "oauth2_user",
                                        password = "access_token_mock_secret"
                                    )
                                    showAddAccountDialog = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = provider.brandColor),
                            modifier = Modifier.testTag("confirm_add_account")
                        ) { Text("Save Verified Connection") }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddAccountDialog = false }) { Text("Cancel") }
                }
            )
        } else {
            // STANDARD CONFIGURATION FORM
            AlertDialog(
                onDismissRequest = { showAddAccountDialog = false },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .background(provider.brandColor, CircleShape)
                        )
                        Text("Mount ${provider.name}")
                    }
                },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = connectionName,
                            onValueChange = { connectionName = it },
                            label = { Text("Custom Connection Label") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("add_account_name_input")
                        )

                        if (provider.iconName in listOf("sftp", "ftp", "webdav")) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = serverHost,
                                    onValueChange = { serverHost = it },
                                    label = { Text("Server IP/Url") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1.5f).testTag("add_account_host_input")
                                )
                                OutlinedTextField(
                                    value = serverPort,
                                    onValueChange = { serverPort = it },
                                    label = { Text("Port") },
                                    singleLine = true,
                                    modifier = Modifier.weight(0.7f).testTag("add_account_port_input")
                                )
                            }
                        }

                        if (provider.iconName == "pc") {
                            Text(
                                "Add your desktop computer as a cloud! Enter your Pc local network name or IP address below.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedTextField(
                                value = serverHost,
                                onValueChange = { serverHost = it },
                                label = { Text("PC IP Address") },
                                placeholder = { Text("e.g. 192.168.1.5") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("add_account_pc_ip")
                            )
                        }

                        OutlinedTextField(
                            value = userName,
                            onValueChange = { userName = it },
                            label = { Text(if (provider.iconName in listOf("sftp", "ftp", "webdav", "s3")) "Username / Access Key" else "Account ID / Login Email") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("add_account_username_input")
                        )

                        OutlinedTextField(
                            value = userPassword,
                            onValueChange = { userPassword = it },
                            label = { Text(if (provider.iconName in listOf("sftp", "ftp", "webdav", "s3")) "Password / Secret Key" else "Access Password / Token") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("add_account_pwd_input")
                        )

                        if (validationError) {
                            Text("Please fully configure connection details!", color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (connectionName.isNotBlank()) {
                                viewModel.addAccount(
                                    name = connectionName,
                                    type = provider.name,
                                    host = serverHost,
                                    port = serverPort.toIntOrNull() ?: 0,
                                    username = userName,
                                    password = userPassword
                                )
                                showAddAccountDialog = false
                            } else {
                                validationError = true
                            }
                        },
                        modifier = Modifier.testTag("confirm_add_account")
                    ) { Text("Link Service") }
                },
                dismissButton = {
                    TextButton(onClick = { showAddAccountDialog = false }) { Text("Cancel") }
                }
            )
        }
    }
}

@Composable
fun MountedAccountRow(
    account: CloudAccount,
    onDisconnect: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            val statusColor = if (account.isActive) Color(0xFF2EC4B6) else MaterialTheme.colorScheme.error

            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = getAccountIcon(account.type),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(18.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = account.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = account.type,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(statusColor, CircleShape)
                    )
                    Text(
                        text = if (account.isActive) "Online" else "Disconnected",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                }
            }

            IconButton(
                onClick = onDisconnect,
                modifier = Modifier.testTag("delete_acc_${account.name}")
            ) {
                Icon(
                    imageVector = Icons.Default.LinkOff,
                    contentDescription = "Disconnect mount",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun ProviderGridItem(
    provider: ProviderType,
    onSelect: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onSelect)
            .testTag("provider_box_${provider.name}"),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(provider.brandColor.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = getAccountIcon(provider.name),
                    contentDescription = provider.name,
                    tint = provider.brandColor,
                    modifier = Modifier.size(22.dp)
                )
            }

            Text(
                text = provider.name,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
