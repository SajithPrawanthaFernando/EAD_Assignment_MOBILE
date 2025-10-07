package com.ead.evcharge.data.repository

import android.util.Log
import com.ead.evcharge.data.local.dao.StationDao
import com.ead.evcharge.data.local.entity.SlotEntity
import com.ead.evcharge.data.local.entity.StationEntity
import com.ead.evcharge.data.model.StationResponse
import com.ead.evcharge.data.remote.ApiService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class StationRepository(
    private val stationDao: StationDao,
    private val apiService: ApiService
) {

    companion object {
        private const val TAG = "StationRepository"
    }

    // Get all stations from local database (offline-first)
    fun getAllStations(): Flow<List<StationEntity>> {
        return stationDao.getAllStations()
    }

    // Get active stations only
    fun getActiveStations(): Flow<List<StationEntity>> {
        return stationDao.getActiveStations()
    }

    // Get station by ID
    fun getStationById(stationId: String): Flow<StationEntity?> {
        return stationDao.getStationById(stationId)
    }

    // Get stations in map bounds
    fun getStationsInBounds(
        minLat: Double,
        maxLat: Double,
        minLng: Double,
        maxLng: Double
    ): Flow<List<StationEntity>> {
        return stationDao.getStationsInBounds(minLat, maxLat, minLng, maxLng)
    }

    // Sync stations from server
    suspend fun syncStationsFromServer(): Result<List<StationEntity>> {
        return try {

            val response = apiService.getAllStations()

            if (response.isSuccessful) {
                response.body()?.let { stationResponses ->

                    // Convert API response to Room entities
                    val stationEntities = stationResponses.map { it.toEntity() }

                    stationDao.deleteAllStations()

                    // Save new data to local database
                    stationDao.insertStations(stationEntities)

                    Result.success(stationEntities)
                } ?: run {
                    // Fallback to local data (don't clear if API fails)
                    val localStations = stationDao.getAllStations().first()
                    Result.success(localStations)
                }
            } else {
                // Fallback to local data (don't clear if API fails)
                val localStations = stationDao.getAllStations().first()
                if (localStations.isNotEmpty()) {
                    Log.d(TAG, "📱 Using ${localStations.size} cached stations")
                    Result.success(localStations)
                } else {
                    Result.failure(Exception("API error: ${response.code()}"))
                }
            }
        } catch (e: UnknownHostException) {
            Log.w(TAG, "⚠️ No internet connection, using cached data")
            handleOfflineSync()
        } catch (e: SocketTimeoutException) {
            Log.w(TAG, "⚠️ Request timeout, using cached data")
            handleOfflineSync()
        } catch (e: Exception) {
            Log.e(TAG, "💥 Error syncing stations: ${e.message}", e)
            e.printStackTrace()
            handleOfflineSync()
        }
    }


    // Handle offline sync - return cached data
    private suspend fun handleOfflineSync(): Result<List<StationEntity>> {
        return try {
            val localStations = stationDao.getAllStations().first()
            if (localStations.isNotEmpty()) {
                Log.d(TAG, "Returning ${localStations.size} cached stations (offline)")
                Result.success(localStations)
            } else {
                Result.failure(Exception("No cached station data available"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting cached stations: ${e.message}", e)
            Result.failure(e)
        }
    }

    // Save single station
    suspend fun saveStation(station: StationEntity) {
        try {
            stationDao.insertStation(station)
            Log.d(TAG, "Station saved: ${station.name}")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving station: ${e.message}", e)
        }
    }

    // Clear all stations
    suspend fun clearAllStations() {
        try {
            stationDao.deleteAllStations()
            Log.d(TAG, "All stations cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing stations: ${e.message}", e)
        }
    }

    // Check if we have local stations
    suspend fun hasLocalStations(): Boolean {
        return try {
            stationDao.getStationCount() > 0
        } catch (e: Exception) {
            Log.e(TAG, "Error checking local stations: ${e.message}", e)
            false
        }
    }
}

// Extension function to convert API response to Room entity
private fun StationResponse.toEntity(): StationEntity {
    return StationEntity(
        id = this.id,
        name = this.name,
        type = this.type,
        active = this.active,
        lat = this.lat,
        lng = this.lng,
        slots = this.slots.map {
            SlotEntity(
                slotId = it.slotId,
                label = it.label,
                available = it.available
            )
        },
        lastSyncTimestamp = System.currentTimeMillis()
    )
}