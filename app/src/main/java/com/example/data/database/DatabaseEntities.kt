package com.example.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

// ==========================================
// 1. CLOUD ACCOUNT ENTITY
// ==========================================

@Entity(tableName = "cloud_accounts")
data class CloudAccount(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val type: String, // "Local", "Google Drive", "OneDrive", "S3", "FTP", "SFTP", "WebDAV", "Dropbox", "Mega", "Terabox", "Pikpak"
    val host: String = "",
    val port: Int = 0,
    val username: String = "",
    val password: String = "",
    val bucketOrPath: String = "",
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface CloudAccountDao {
    @Query("SELECT * FROM cloud_accounts ORDER BY name ASC")
    fun getAllAccounts(): Flow<List<CloudAccount>>

    @Query("SELECT * FROM cloud_accounts WHERE id = :id")
    suspend fun getAccountById(id: Int): CloudAccount?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccount(account: CloudAccount): Long

    @Delete
    suspend fun deleteAccount(account: CloudAccount)

    @Query("DELETE FROM cloud_accounts WHERE id = :id")
    suspend fun deleteAccountById(id: Int)
}

// ==========================================
// 2. SYNCHRONIZATION JOB ENTITY
// ==========================================

@Entity(tableName = "sync_jobs")
data class SyncJob(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val sourceAccountId: Int, // Refers to CloudAccount.id, or -1 for local
    val sourcePath: String,
    val destAccountId: Int, // Refers to CloudAccount.id, or -1 for local
    val destPath: String,
    val frequency: String, // "Manual", "Hourly", "Daily", "Continuous"
    val lastRunTime: Long = 0L,
    val status: String = "Idle", // "Idle", "Syncing", "Completed", "Failed"
    val encryptWithPassword: Boolean = false,
    val encryptionPassword: String = "",
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface SyncJobDao {
    @Query("SELECT * FROM sync_jobs ORDER BY createdAt DESC")
    fun getAllJobs(): Flow<List<SyncJob>>

    @Query("SELECT * FROM sync_jobs WHERE id = :id")
    suspend fun getJobById(id: Int): SyncJob?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJob(job: SyncJob): Long

    @Delete
    suspend fun deleteJob(job: SyncJob)

    @Query("UPDATE sync_jobs SET lastRunTime = :lastRun, status = :status WHERE id = :id")
    suspend fun updateJobStatus(id: Int, lastRun: Long, status: String)
}

// ==========================================
// 3. TRANSFER MONITORED ITEM ENTITY
// ==========================================

@Entity(tableName = "transfer_items")
data class TransferItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val fileName: String,
    val sourceAccountName: String,
    val destAccountName: String,
    val sourcePath: String,
    val destPath: String,
    val status: String, // "Pending", "Transferring", "Completed", "Failed"
    val progress: Float, // 0.0f to 1.0f
    val speed: String = "0 KB/s",
    val totalSize: Long = 0L,
    val transferredSize: Long = 0L,
    val isUpload: Boolean = true,
    val isEncrypted: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface TransferItemDao {
    @Query("SELECT * FROM transfer_items ORDER BY timestamp DESC")
    fun getAllTransfers(): Flow<List<TransferItem>>

    @Query("SELECT * FROM transfer_items WHERE status = 'Transferring' OR status = 'Pending' ORDER BY timestamp DESC")
    fun getActiveTransfers(): Flow<List<TransferItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransfer(item: TransferItem): Long

    @Query("UPDATE transfer_items SET status = :status, progress = :progress, transferredSize = :transferredSize, speed = :speed WHERE id = :id")
    suspend fun updateProgress(id: Int, status: String, progress: Float, transferredSize: Long, speed: String)

    @Query("DELETE FROM transfer_items")
    suspend fun clearHistory()

    @Delete
    suspend fun deleteTransfer(item: TransferItem)
}
