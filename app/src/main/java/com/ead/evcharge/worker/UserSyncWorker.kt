package com.ead.evcharge.worker

import android.content.Context
import android.util.Log
import androidx.work.*
import com.ead.evcharge.data.local.AppDatabase
import com.ead.evcharge.data.local.TokenManager
import com.ead.evcharge.data.remote.RetrofitInstance
import com.ead.evcharge.data.repository.UserRepository
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class UserSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "UserSyncWorker"
        const val WORK_NAME = "user_sync_work"

        fun scheduleSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val syncRequest = PeriodicWorkRequestBuilder<UserSyncWorker>(
                15, TimeUnit.MINUTES // Sync every 15 minutes when online
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.LINEAR,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                syncRequest
            )

            Log.d(TAG, "User sync scheduled")
        }

        fun cancelSync(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.d(TAG, "User sync cancelled")
        }
    }

    override suspend fun doWork(): Result {
        return try {
            Log.d(TAG, "Starting user sync...")

            val tokenManager = TokenManager(applicationContext)
            val database = AppDatabase.getDatabase(applicationContext)
            val repository = UserRepository(database.userDao(), RetrofitInstance.api)

            // Get userId from TokenManager
            val userId = tokenManager.getUserId().first()

            if (userId.isNullOrEmpty()) {
                Log.w(TAG, "No user ID found, skipping sync")
                return Result.success()
            }

            // Sync user from server
            val result = repository.syncUserFromServer(userId)

            if (result.isSuccess) {
                Log.d(TAG, "User sync completed successfully")
                Result.success()
            } else {
                Log.e(TAG, "User sync failed: ${result.exceptionOrNull()?.message}")
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during user sync: ${e.message}", e)
            Result.retry()
        }
    }
}