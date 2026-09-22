package com.shangkeschedule.widget

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.shangkeschedule.data.repository.ApiConfigRepository
import com.shangkeschedule.data.repository.BackupRepository
import kotlinx.coroutines.flow.first
import org.koin.android.annotation.KoinWorker

/**
 * WebDAV 自动同步后台任务。
 *
 * 每次执行都读取最新配置与最新本地数据，因此周期兜底任务和一次性的数据变更任务
 * 可以共用同一个 Worker；关闭开关后由 SyncManager 取消调度。
 */
@KoinWorker
class WebDavAutoSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters,
    private val apiConfigRepository: ApiConfigRepository,
    private val backupRepository: BackupRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val enabled = apiConfigRepository.webDavAutoSyncEnabledFlow.first()
        val config = apiConfigRepository.webDavConfigFlow.first()
        if (!enabled || config == null) return Result.success()

        return backupRepository.uploadFullBackupToWebDav().fold(
            onSuccess = {
                Log.d(TAG, "WebDAV 自动同步完成")
                Result.success()
            },
            onFailure = { error ->
                Log.e(TAG, "WebDAV 自动同步失败（第 ${runAttemptCount + 1} 次）", error)
                if (runAttemptCount < MAX_RETRY_ATTEMPTS) Result.retry() else Result.failure()
            }
        )
    }

    private companion object {
        const val TAG = "WebDavAutoSync"
        const val MAX_RETRY_ATTEMPTS = 3
    }
}
