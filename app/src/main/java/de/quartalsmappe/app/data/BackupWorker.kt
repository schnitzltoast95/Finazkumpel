package de.quartalsmappe.app.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

internal object BackupSchedule {
    private const val WORK_NAME = "quartalsmappe_auto_backup"
    val generationLock = Any()
    val backupLock = Any()

    fun update(context: Context, folderConfigured: Boolean, intervalDays: Int) {
        val manager = WorkManager.getInstance(context.applicationContext)
        if (!folderConfigured) {
            manager.cancelUniqueWork(WORK_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<BackupWorker>(intervalDays.toLong(), TimeUnit.DAYS)
            .build()
        manager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}

internal class BackupWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val store = AppStore(applicationContext, scheduleBackups = false)
        if (store.backupFolderUri == null || store.loadError != null) {
            return Result.success()
        }
        return runCatching { store.updateBackupIfPending() }
            .fold(onSuccess = { Result.success() }, onFailure = { Result.retry() })
    }
}
