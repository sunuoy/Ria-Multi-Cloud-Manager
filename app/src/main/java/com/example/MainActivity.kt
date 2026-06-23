package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.screens.AccountsScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.ExplorerScreen
import com.example.ui.screens.SyncScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.RiaViewModel
import com.example.ui.viewmodel.RiaViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val viewModel: RiaViewModel = viewModel(
                    factory = RiaViewModelFactory(application)
                )

                var currentScreen by remember { mutableStateOf("dashboard") }
                val activeTransfers by viewModel.activeTransfers.collectAsState()

                Scaffold(
                    modifier = Modifier.fillMaxSize().testTag("main_activity_scaffold"),
                    bottomBar = {
                        NavigationBar(
                            modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars).testTag("bottom_nav_bar")
                        ) {
                            NavigationBarItem(
                                selected = currentScreen == "dashboard",
                                onClick = { currentScreen = "dashboard" },
                                icon = {
                                    BadgedBox(
                                        badge = {
                                            if (activeTransfers.isNotEmpty()) {
                                                Badge {
                                                    Text(
                                                        "${activeTransfers.size}",
                                                        fontSize = 10.sp,
                                                        modifier = Modifier.testTag("active_transfers_count_badge")
                                                    )
                                                }
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Dataset,
                                            contentDescription = "Dashboard"
                                        )
                                    }
                                },
                                label = { Text("Dashboard", fontSize = 11.sp) },
                                modifier = Modifier.testTag("nav_dashboard")
                            )

                            NavigationBarItem(
                                selected = currentScreen == "explorer",
                                onClick = { currentScreen = "explorer" },
                                icon = {
                                    Icon(
                                        imageVector = Icons.Default.Folder,
                                        contentDescription = "Cloud Explorer"
                                    )
                                },
                                label = { Text("Explorer", fontSize = 11.sp) },
                                modifier = Modifier.testTag("nav_explorer")
                            )

                            NavigationBarItem(
                                selected = currentScreen == "sync",
                                onClick = { currentScreen = "sync" },
                                icon = {
                                    Icon(
                                        imageVector = Icons.Default.Sync,
                                        contentDescription = "Sync Manager"
                                    )
                                },
                                label = { Text("Sync", fontSize = 11.sp) },
                                modifier = Modifier.testTag("nav_sync")
                            )

                            NavigationBarItem(
                                selected = currentScreen == "accounts",
                                onClick = { currentScreen = "accounts" },
                                icon = {
                                    Icon(
                                        imageVector = Icons.Default.CloudQueue,
                                        contentDescription = "Server Accounts"
                                    )
                                },
                                label = { Text("Accounts", fontSize = 11.sp) },
                                modifier = Modifier.testTag("nav_accounts")
                            )
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .windowInsetsPadding(WindowInsets.statusBars)
                    ) {
                        when (currentScreen) {
                            "dashboard" -> DashboardScreen(
                                viewModel = viewModel,
                                onNavigateToExplorer = { currentScreen = "explorer" },
                                onNavigateToSync = { currentScreen = "sync" }
                            )
                            "explorer" -> ExplorerScreen(
                                viewModel = viewModel
                            )
                            "sync" -> SyncScreen(
                                viewModel = viewModel
                            )
                            "accounts" -> AccountsScreen(
                                viewModel = viewModel
                            )
                        }
                    }
                }
            }
        }
    }
}
