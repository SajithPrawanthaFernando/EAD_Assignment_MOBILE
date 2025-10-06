package com.ead.evcharge.worker

import android.content.Context
import android.util.Log
import androidx.work.*
import com.ead.evcharge.data.local.AppDatabase
import com.ead.evcharge.data.remote.RetrofitInstance
import com.ead.evcharge.data.repository.StationRepository
import java.util.concurrent.TimeUnit

class StationSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "StationSyncWorker"
        const val WORK_NAME = "station_sync_work"

        fun scheduleSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val syncRequest = PeriodicWorkRequestBuilder<StationSyncWorker>(
                30, TimeUnit.MINUTES // Sync every 30 minutes
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

            Log.d(TAG, "Station sync scheduled")
        }

        fun cancelSync(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.d(TAG, "Station sync cancelled")
        }
    }

    override suspend fun doWork(): Result {
        return try {
            Log.d(TAG, "Starting station sync...")

            val database = AppDatabase.getDatabase(applicationContext)
            val repository = StationRepository(database.stationDao(), RetrofitInstance.api)

            val result = repository.syncStationsFromServer()

            if (result.isSuccess) {
                val stations = result.getOrNull()
                Log.d(TAG, "Station sync completed: ${stations?.size ?: 0} stations")
                Result.success()
            } else {
                Log.e(TAG, "Station sync failed: ${result.exceptionOrNull()?.message}")
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during station sync: ${e.message}", e)
            Result.retry()
        }
    }
}