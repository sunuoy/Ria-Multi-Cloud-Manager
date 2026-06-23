package com.example.data.repository

import com.example.data.database.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

// Representation of a virtual file/folder in our filesystem
data class VFile(
    val name: String,
    val path: String, // absolute path from virtual root (e.g., "/Photos/vacation.jpg")
    val size: Long, // in bytes
    val isFolder: Boolean,
    val isEncrypted: Boolean = false,
    val lastModified: Long = System.currentTimeMillis()
)

// Active layout tab in our carousel explorer
data class ExplorerTab(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val accountId: Int, // Refers to CloudAccount.id. -1 for Local Storage
    val currentPath: String = "/",
    val history: List<String> = listOf("/"),
    val historyIndex: Int = 0
)

class RiaRepository(
    private val accountDao: CloudAccountDao,
    private val syncJobDao: SyncJobDao,
    private val transferDao: TransferItemDao
) {
    // Flows exposed to UI
    val allAccounts: Flow<List<CloudAccount>> = accountDao.getAllAccounts()
    val allSyncJobs: Flow<List<SyncJob>> = syncJobDao.getAllJobs()
    val allTransfers: Flow<List<TransferItem>> = transferDao.getAllTransfers()
    val activeTransfers: Flow<List<TransferItem>> = transferDao.getActiveTransfers()

    // Explorer Tabs State
    private val _tabs = MutableStateFlow<List<ExplorerTab>>(emptyList())
    val tabs: StateFlow<List<ExplorerTab>> = _tabs.asStateFlow()

    private val _selectedTabId = MutableStateFlow<String>("")
    val selectedTabId: StateFlow<String> = _selectedTabId.asStateFlow()

    // Clipboard State
    data class ClipboardItem(
        val file: VFile,
        val sourceAccountId: Int,
        val isCut: Boolean = false
    )
    private val _clipboard = MutableStateFlow<ClipboardItem?>(null)
    val clipboard: StateFlow<ClipboardItem?> = _clipboard.asStateFlow()

    // In-memory virtual storage system indexed as: accountId -> mapOf(path -> list of files)
    private val virtualStorage = MutableStateFlow<Map<Int, Map<String, List<VFile>>>>(emptyMap())

    init {
        // Initialize default tabs and pre-fill some accounts if empty
        CoroutineScope(Dispatchers.IO).launch {
            // Check if we need to seed accounts
            val initialAccounts = mutableListOf<CloudAccount>()
                
            // First time setup - Seed standard demo file locations
            val filesDirAccount = CloudAccount(id = 1, name = "Internal Storage", type = "Local", bucketOrPath = "/local")
            val driveAccount = CloudAccount(id = 2, name = "Google Drive (Personal)", type = "Google Drive", username = "younusM33@gmail.com")
            val sftpAccount = CloudAccount(id = 3, name = "Home SFTP Backups", type = "SFTP", host = "192.168.1.102", port = 22, username = "ria_user")

            accountDao.insertAccount(filesDirAccount)
            accountDao.insertAccount(driveAccount)
            accountDao.insertAccount(sftpAccount)

            seedVirtualSystem()

            // Initialize default tabs (two tabs initially)
            val tab1 = ExplorerTab(title = "Local Storage", accountId = 1, currentPath = "/")
            val tab2 = ExplorerTab(title = "Google Drive", accountId = 2, currentPath = "/")
            _tabs.value = listOf(tab1, tab2)
            _selectedTabId.value = tab1.id
        }
    }

    // Prepopulate filesystem trees
    private fun seedVirtualSystem() {
        val storageMap = mutableMapOf<Int, Map<String, List<VFile>>>()

        // 1. Seed Local Storage (ID = 1)
        storageMap[1] = mapOf(
            "/" to listOf(
                VFile("Documents", "/Documents", 0, isFolder = true),
                VFile("Downloads", "/Downloads", 0, isFolder = true),
                VFile("SecureVault.enc", "/SecureVault.enc", 1024 * 1024 * 25, isFolder = false, isEncrypted = true),
                VFile("app_config.json", "/app_config.json", 1024 * 4, isFolder = false)
            ),
            "/Documents" to listOf(
                VFile("TaxReturn_2025.pdf", "/Documents/TaxReturn_2025.pdf", 1024 * 1024 * 3, isFolder = false),
                VFile("ProductSpecs.docx", "/Documents/ProductSpecs.docx", 1024 * 720, isFolder = false)
            ),
            "/Downloads" to listOf(
                VFile("image_editor_apk.zip", "/Downloads/image_editor_apk.zip", 1024 * 1024 * 48, isFolder = false),
                VFile("dashboard_draft.png", "/Downloads/dashboard_draft.png", 1024 * 350, isFolder = false)
            )
        )

        // 2. Seed Google Drive (ID = 2)
        storageMap[2] = mapOf(
            "/" to listOf(
                VFile("Assets", "/Assets", 0, isFolder = true),
                VFile("CloudBackups", "/CloudBackups", 0, isFolder = true),
                VFile("ria_setup_guide.pdf", "/ria_setup_guide.pdf", 1024 * 1024 * 12, isFolder = false)
            ),
            "/Assets" to listOf(
                VFile("hero_background.jpg", "/Assets/hero_background.jpg", 1024 * 1024 * 2, isFolder = false),
                VFile("brand_logo.svg", "/Assets/brand_logo.svg", 1024 * 45, isFolder = false)
            ),
            "/CloudBackups" to listOf(
                VFile("databases_backup.tar.gz", "/CloudBackups/databases_backup.tar.gz", 1024 * 1024 * 230, isFolder = false)
            )
        )

        // 3. Seed Home SFTP (ID = 3)
        storageMap[3] = mapOf(
            "/" to listOf(
                VFile("StorageVolume", "/StorageVolume", 0, isFolder = true),
                VFile("server_status_log.txt", "/server_status_log.txt", 1024 * 85, isFolder = false)
            ),
            "/StorageVolume" to listOf(
                VFile("HomeMedia", "/StorageVolume/HomeMedia", 0, isFolder = true),
                VFile("pc_system_shadow.img", "/StorageVolume/pc_system_shadow.img", 1024L * 1024 * 1024 * 4, isFolder = false)
            ),
            "/StorageVolume/HomeMedia" to listOf(
                VFile("family_vacation_4K.mov", "/StorageVolume/HomeMedia/family_vacation_4K.mov", 1024L * 1024 * 920, isFolder = false)
            )
        )

        virtualStorage.value = storageMap
    }

    // Dynamic Seed when adding a custom account from UI
    fun seedAccountFileSystem(accountId: Int, accountType: String) {
        val currentMap = virtualStorage.value.toMutableMap()
        if (!currentMap.containsKey(accountId)) {
            val genericFiles = listOf(
                VFile("My Documents", "/My Documents", 0, isFolder = true),
                VFile("ReadMe.txt", "/ReadMe.txt", 1234, isFolder = false),
                VFile("encrypted_backup.bin", "/encrypted_backup.bin", 102 * 1024 * 15, isFolder = false, isEncrypted = true)
            )
            val subMap = mapOf(
                "/" to genericFiles,
                "/My Documents" to listOf(
                    VFile("WelcomeNote.pdf", "/My Documents/WelcomeNote.pdf", 45 * 1024, isFolder = false)
                )
            )
            currentMap[accountId] = subMap
            virtualStorage.value = currentMap
        }
    }

    // Get files for specific tab/path
    fun getFiles(accountId: Int, path: String): List<VFile> {
        val accMap = virtualStorage.value[accountId] ?: return emptyList()
        return accMap[path] ?: emptyList()
    }

    // Tab CRUD
    fun addNewTab(accountId: Int, title: String) {
        val current = _tabs.value.toMutableList()
        val newTab = ExplorerTab(title = title, accountId = accountId, currentPath = "/")
        current.add(newTab)
        _tabs.value = current
        _selectedTabId.value = newTab.id
    }

    fun removeTab(tabId: String) {
        val current = _tabs.value.toMutableList()
        if (current.size > 1) {
            val targetIdx = current.indexOfFirst { it.id == tabId }
            current.removeAt(targetIdx)
            _tabs.value = current
            if (_selectedTabId.value == tabId) {
                // select another tab
                _selectedTabId.value = current[0].id
            }
        }
    }

    fun selectTab(tabId: String) {
        _selectedTabId.value = tabId
    }

    fun navigateToPath(tabId: String, newPath: String) {
        val current = _tabs.value.map { item ->
            if (item.id == tabId) {
                val newHistory = item.history.subList(0, item.historyIndex + 1) + newPath
                item.copy(
                    currentPath = newPath,
                    history = newHistory,
                    historyIndex = newHistory.lastIndex,
                    title = if (newPath == "/") item.title.split(":").first() else newPath.substringAfterLast("/")
                )
            } else {
                item
            }
        }
        _tabs.value = current
    }

    fun navigateBack(tabId: String) {
        val current = _tabs.value.map { item ->
            if (item.id == tabId && item.historyIndex > 0) {
                val newIndex = item.historyIndex - 1
                val targetPath = item.history[newIndex]
                item.copy(
                    currentPath = targetPath,
                    historyIndex = newIndex,
                    title = if (targetPath == "/") item.title.split(":").first() else targetPath.substringAfterLast("/")
                )
            } else {
                item
            }
        }
        _tabs.value = current
    }

    // Database Actions: Accounts
    suspend fun insertAccount(account: CloudAccount): Int {
        val id = accountDao.insertAccount(account).toInt()
        seedAccountFileSystem(id, account.type)
        return id
    }

    suspend fun deleteAccount(account: CloudAccount) {
        accountDao.deleteAccount(account)
        // Also remove tabs pointing to this account to prevent crashes
        val filtered = _tabs.value.filter { it.accountId != account.id }
        if (filtered.isNotEmpty()) {
            _tabs.value = filtered
            if (!_tabs.value.any { it.id == _selectedTabId.value }) {
                _selectedTabId.value = filtered.first().id
            }
        } else {
            // Re-add Local tab as fallback
            val localTab = ExplorerTab(title = "Local Storage", accountId = 1, currentPath = "/")
            _tabs.value = listOf(localTab)
            _selectedTabId.value = localTab.id
        }
    }

    // Database Actions: Sync Jobs
    suspend fun insertSyncJob(job: SyncJob) = syncJobDao.insertJob(job)
    suspend fun deleteSyncJob(job: SyncJob) = syncJobDao.deleteJob(job)

    // File Actions: Create Folder
    fun createFolder(accountId: Int, parentPath: String, name: String) {
        val completePath = if (parentPath == "/") "/$name" else "$parentPath/$name"
        val newFolderFile = VFile(name = name, path = completePath, size = 0, isFolder = true)

        val currentMap = virtualStorage.value.toMutableMap()
        val accountFiles = currentMap[accountId]?.toMutableMap() ?: mutableMapOf()

        // 1. Add to parent folder list
        val parentFiles = accountFiles[parentPath]?.toMutableList() ?: mutableListOf()
        if (!parentFiles.any { it.name == name }) {
            parentFiles.add(newFolderFile)
            accountFiles[parentPath] = parentFiles
        }

        // 2. Initialize empty space for new folder contents
        accountFiles[completePath] = emptyList()

        currentMap[accountId] = accountFiles
        virtualStorage.value = currentMap
    }

    // File Actions: Rename
    fun renameFile(accountId: Int, parentPath: String, oldName: String, newName: String) {
        val currentMap = virtualStorage.value.toMutableMap()
        val accountFiles = currentMap[accountId]?.toMutableMap() ?: return

        // 1. Update in the parent listing
        val parentFiles = accountFiles[parentPath]?.map { file ->
            if (file.name == oldName) {
                val newPath = if (parentPath == "/") "/$newName" else "$parentPath/$newName"
                file.copy(name = newName, path = newPath)
            } else file
        } ?: return
        accountFiles[parentPath] = parentFiles

        // 2. If it is a folder, migrate its internal mapping to the new path
        val oldFolderFullPath = if (parentPath == "/") "/$oldName" else "$parentPath/$oldName"
        val newFolderFullPath = if (parentPath == "/") "/$newName" else "$parentPath/$newName"
        if (accountFiles.containsKey(oldFolderFullPath)) {
            val contentList = accountFiles[oldFolderFullPath] ?: emptyList()
            accountFiles.remove(oldFolderFullPath)
            accountFiles[newFolderFullPath] = contentList.map { subFile ->
                val remainingSubPath = subFile.path.removePrefix(oldFolderFullPath)
                subFile.copy(path = newFolderFullPath + remainingSubPath)
            }
        }

        currentMap[accountId] = accountFiles
        virtualStorage.value = currentMap
    }

    // File Actions: Delete
    fun deleteFile(accountId: Int, parentPath: String, name: String) {
        val currentMap = virtualStorage.value.toMutableMap()
        val accountFiles = currentMap[accountId]?.toMutableMap() ?: return

        // Remove from parent
        val parentFiles = accountFiles[parentPath]?.filter { it.name != name } ?: return
        accountFiles[parentPath] = parentFiles

        // Remove sub-paths if folder
        val targetFullPath = if (parentPath == "/") "/$name" else "$parentPath/$name"
        accountFiles.remove(targetFullPath)
        accountFiles.keys.removeAll { it.startsWith("$targetFullPath/") }

        currentMap[accountId] = accountFiles
        virtualStorage.value = currentMap
    }

    // Clipboard: Copy & Cut
    fun copyToClipboard(file: VFile, accountId: Int) {
        _clipboard.value = ClipboardItem(file = file, sourceAccountId = accountId, isCut = false)
    }

    fun cutToClipboard(file: VFile, accountId: Int) {
        _clipboard.value = ClipboardItem(file = file, sourceAccountId = accountId, isCut = true)
    }

    // Paste file (triggers file copy and an active transfer dashboard job)
    fun pasteFile(destinationAccountId: Int, destParentPath: String, customizeEncryption: Boolean = false, password: String = "") {
        val clipboard = _clipboard.value ?: return
        val sourceFile = clipboard.file
        val isCut = clipboard.isCut

        // Identify account names for Transfer log
        CoroutineScope(Dispatchers.IO).launch {
            val sourceAcc = accountDao.getAccountById(clipboard.sourceAccountId)
            val destAcc = accountDao.getAccountById(destinationAccountId)

            val sourceName = sourceAcc?.name ?: "Unknown"
            val destName = destAcc?.name ?: "Unknown"

            // Construct pasted file paths
            val targetFullPath = if (destParentPath == "/") "/${sourceFile.name}" else "$destParentPath/${sourceFile.name}"
            val isTargetEncrypted = sourceFile.isEncrypted || (customizeEncryption && password.isNotEmpty())
            
            // Name adjustment in case of encryption
            val finalName = if (customizeEncryption && !sourceFile.name.endsWith(".enc")) "${sourceFile.name}.enc" else sourceFile.name
            val adjustedFullPath = if (destParentPath == "/") "/$finalName" else "$destParentPath/$finalName"

            val pastedFile = sourceFile.copy(
                name = finalName,
                path = adjustedFullPath,
                isEncrypted = isTargetEncrypted,
                lastModified = System.currentTimeMillis()
            )

            // Trigger active transfer log item with incremental animation
            val transferId = transferDao.insertTransfer(
                TransferItem(
                    fileName = finalName,
                    sourceAccountName = sourceName,
                    destAccountName = destName,
                    sourcePath = sourceFile.path,
                    destPath = adjustedFullPath,
                    status = "Pending",
                    progress = 0.0f,
                    speed = "Calculating...",
                    totalSize = sourceFile.size,
                    transferredSize = 0L,
                    isUpload = clipboard.sourceAccountId != destinationAccountId,
                    isEncrypted = isTargetEncrypted
                )
            ).toInt()

            // Perform transfer animation simulation
            launchTransferCoroutine(transferId, finalName, postedAction = {
                // Actually add to the destination system state
                val currentMap = virtualStorage.value.toMutableMap()
                val destAccFiles = currentMap[destinationAccountId]?.toMutableMap() ?: mutableMapOf()

                val targetList = destAccFiles[destParentPath]?.toMutableList() ?: mutableListOf()
                // Avoid conflicts
                targetList.removeAll { it.name == finalName }
                targetList.add(pastedFile)
                destAccFiles[destParentPath] = targetList

                // If folder, mock copying child folders as well
                if (sourceFile.isFolder) {
                    val srcAccFiles = currentMap[clipboard.sourceAccountId] ?: emptyMap()
                    val srcFullPathMatched = sourceFile.path
                    srcAccFiles.forEach { (subPathKey, subFilesList) ->
                        if (subPathKey.startsWith(srcFullPathMatched)) {
                            val subRelative = subPathKey.removePrefix(srcFullPathMatched)
                            val targetSubKey = adjustedFullPath + subRelative
                            destAccFiles[targetSubKey] = subFilesList.map { item ->
                                item.copy(path = targetSubKey + "/" + item.name)
                            }
                        }
                    }
                }

                currentMap[destinationAccountId] = destAccFiles
                virtualStorage.value = currentMap

                // If Cut operation, delete the file from source storage
                if (isCut) {
                    val parentOfSource = if (sourceFile.path.contains("/")) sourceFile.path.substringBeforeLast("/") else "/"
                    val adjustedParentOfSource = if (parentOfSource.isEmpty()) "/" else parentOfSource
                    deleteFile(clipboard.sourceAccountId, adjustedParentOfSource, sourceFile.name)
                    _clipboard.value = null // reset clip
                }
            })
        }
    }

    // Background animation runner for file transfer operations
    private fun launchTransferCoroutine(transferId: Int, filename: String, postedAction: () -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            delay(400) // Initial delay
            var progress = 0.0f
            val totalSize = 25 * 1024 * 1024L // Simulation size: 25MB
            var speedRate = 2.4f // MB/s

            while (progress < 1.0f) {
                progress += (0.15f + (Math.random() * 0.1f).toFloat())
                if (progress > 1.0f) progress = 1.0f

                val bytesTransferred = (totalSize * progress).toLong()
                speedRate = (1.5f + Math.random() * 3.0f).toFloat() // speed ticks
                val speedText = String.format("%.1f MB/s", speedRate)

                transferDao.updateProgress(
                    id = transferId,
                    status = "Transferring",
                    progress = progress,
                    transferredSize = bytesTransferred,
                    speed = speedText
                )
                delay(350)
            }

            // Fire real storage manipulation
            postedAction()

            // Completion logging
            transferDao.updateProgress(
                id = transferId,
                status = "Completed",
                progress = 1.0f,
                transferredSize = totalSize,
                speed = "0 KB/s"
            )
        }
    }

    // Trigger explicit sync job running simulation
    fun runSyncJob(job: SyncJob) {
        CoroutineScope(Dispatchers.IO).launch {
            syncJobDao.updateJobStatus(job.id, System.currentTimeMillis(), "Syncing")

            // Create transfer logs
            val syncTransferId = transferDao.insertTransfer(
                TransferItem(
                    fileName = "AutoSync_${job.name}.enc",
                    sourceAccountName = if (job.sourceAccountId == -1) "Local" else accountDao.getAccountById(job.sourceAccountId)?.name ?: "Source",
                    destAccountName = if (job.destAccountId == -1) "Local" else accountDao.getAccountById(job.destAccountId)?.name ?: "Destination",
                    sourcePath = job.sourcePath,
                    destPath = job.destPath,
                    status = "Transferring",
                    progress = 0.0f,
                    speed = "Starting...",
                    totalSize = 450 * 1024 * 1024L, // 450 MB big sync
                    transferredSize = 0,
                    isUpload = true,
                    isEncrypted = job.encryptWithPassword
                )
            ).toInt()

            // Animate
            var progress = 0.0f
            while (progress < 1.0f) {
                progress += 0.2f
                if (progress > 1.0f) progress = 1.0f
                val bytesDone = (450 * 1024 * 1024L * progress).toLong()
                val speed = String.format("%.1f MB/s", 12.0f + Math.random() * 5.0f)

                transferDao.updateProgress(
                    id = syncTransferId,
                    status = "Transferring",
                    progress = progress,
                    transferredSize = bytesDone,
                    speed = speed
                )
                delay(400)
            }

            transferDao.updateProgress(
                id = syncTransferId,
                status = "Completed",
                progress = 1.0f,
                transferredSize = 450 * 1024 * 1024L,
                speed = "Done"
            )

            // Dynamic folder seeding in destination cloud to reflect the synced files
            val destFiles = virtualStorage.value[job.destAccountId]?.toMutableMap() ?: mutableMapOf()
            val syncRootPath = job.destPath
            
            val seedSyncedItems = listOf(
                VFile(
                    name = "Encrypted_Backup_Archive.enc", 
                    path = if (syncRootPath == "/") "/Encrypted_Backup_Archive.enc" else "$syncRootPath/Encrypted_Backup_Archive.enc", 
                    size = 1024 * 1024 * 450, 
                    isFolder = false, 
                    isEncrypted = job.encryptWithPassword
                )
            )
            destFiles[syncRootPath] = (destFiles[syncRootPath] ?: emptyList()) + seedSyncedItems
            val updatedMap = virtualStorage.value.toMutableMap()
            updatedMap[job.destAccountId] = destFiles
            virtualStorage.value = updatedMap

            // Update job database row
            syncJobDao.updateJobStatus(job.id, System.currentTimeMillis(), "Completed")
        }
    }

    suspend fun clearTransferHistory() {
        transferDao.clearHistory()
    }
}
