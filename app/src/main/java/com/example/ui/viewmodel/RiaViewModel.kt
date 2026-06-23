package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.database.AppDatabase
import com.example.data.database.CloudAccount
import com.example.data.database.SyncJob
import com.example.data.database.TransferItem
import com.example.data.repository.ExplorerTab
import com.example.data.repository.RiaRepository
import com.example.data.repository.VFile
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class RiaViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val repository = RiaRepository(
        accountDao = db.cloudAccountDao(),
        syncJobDao = db.syncJobDao(),
        transferDao = db.transferItemDao()
    )

    // Data streams
    val accounts: StateFlow<List<CloudAccount>> = repository.allAccounts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val syncJobs: StateFlow<List<SyncJob>> = repository.allSyncJobs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allTransfers: StateFlow<List<TransferItem>> = repository.allTransfers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeTransfers: StateFlow<List<TransferItem>> = repository.activeTransfers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val tabs: StateFlow<List<ExplorerTab>> = repository.tabs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val selectedTabId: StateFlow<String> = repository.selectedTabId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val clipboard = repository.clipboard

    // Global file search states
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val searchResults: StateFlow<List<Pair<Int, VFile>>> = _searchQuery
        .map { query -> repository.searchFiles(query) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    // Bulk action helper for multi-selection
    fun deleteFilesBulk(fileNames: List<String>) {
        val active = currentTab.value ?: return
        fileNames.forEach { name ->
            repository.deleteFile(active.accountId, active.currentPath, name)
        }
    }

    // Current files being viewed in the active tab
    val currentTabFiles: StateFlow<List<VFile>> = combine(tabs, selectedTabId) { tabList, activeId ->
        val activeTab = tabList.find { it.id == activeId }
        if (activeTab != null) {
            repository.getFiles(activeTab.accountId, activeTab.currentPath)
        } else {
            emptyList()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Currently selected explorer tab model
    val currentTab: StateFlow<ExplorerTab?> = combine(tabs, selectedTabId) { tabList, activeId ->
        tabList.find { it.id == activeId }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Account CRUD
    fun addAccount(name: String, type: String, host: String = "", port: Int = 0, username: String = "", password: String = "") {
        viewModelScope.launch {
            repository.insertAccount(
                CloudAccount(
                    name = name,
                    type = type,
                    host = host,
                    port = port,
                    username = username,
                    password = password
                )
            )
        }
    }

    fun removeAccount(account: CloudAccount) {
        viewModelScope.launch {
            repository.deleteAccount(account)
        }
    }

    // Sync Jobs CRUD
    fun addSyncJob(name: String, srcAccId: Int, srcPath: String, destAccId: Int, destPath: String, freq: String, encrypt: Boolean, psswd: String) {
        viewModelScope.launch {
            repository.insertSyncJob(
                SyncJob(
                    name = name,
                    sourceAccountId = srcAccId,
                    sourcePath = srcPath,
                    destAccountId = destAccId,
                    destPath = destPath,
                    frequency = freq,
                    encryptWithPassword = encrypt,
                    encryptionPassword = psswd
                )
            )
        }
    }

    fun deleteSyncJob(job: SyncJob) {
        viewModelScope.launch {
            repository.deleteSyncJob(job)
        }
    }

    fun triggerSyncImmediately(job: SyncJob) {
        repository.runSyncJob(job)
    }

    // Tab CRUD
    fun createNewTab(accountId: Int, name: String) {
        repository.addNewTab(accountId, name)
    }

    fun closeTab(tabId: String) {
        repository.removeTab(tabId)
    }

    fun selectTab(tabId: String) {
        repository.selectTab(tabId)
    }

    // Explorer Navigations
    fun enterFolder(tabId: String, path: String) {
        repository.navigateToPath(tabId, path)
    }

    fun goBackInTab(tabId: String) {
        repository.navigateBack(tabId)
    }

    // Clipboard operations
    fun copyToClipboard(file: VFile, accountId: Int) {
        repository.copyToClipboard(file, accountId)
    }

    fun cutToClipboard(file: VFile, accountId: Int) {
        repository.cutToClipboard(file, accountId)
    }

    // Paste file (optionally with encryption settings)
    fun pasteClipboard(customizeEncryption: Boolean = false, password: String = "") {
        val active = currentTab.value ?: return
        repository.pasteFile(active.accountId, active.currentPath, customizeEncryption, password)
    }

    // File manipulation
    fun createNewFolder(name: String) {
        val active = currentTab.value ?: return
        repository.createFolder(active.accountId, active.currentPath, name)
    }

    fun renameFile(oldName: String, newName: String) {
        val active = currentTab.value ?: return
        repository.renameFile(active.accountId, active.currentPath, oldName, newName)
    }

    fun deleteFile(name: String) {
        val active = currentTab.value ?: return
        repository.deleteFile(active.accountId, active.currentPath, name)
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearTransferHistory()
        }
    }
}

// ViewModelFactory helper
class RiaViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(RiaViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return RiaViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
