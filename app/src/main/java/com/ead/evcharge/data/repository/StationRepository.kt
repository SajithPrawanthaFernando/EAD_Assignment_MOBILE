// data/repository/StationRepository.kt
package com.ead.evcharge.data.repository

import android.util.Log
import com.ead.evcharge.data.local.dao.StationDao
import com.ead.evcharge.data.local.entity.SlotEntity
import com.ead.evcharge.data.local.entity.StationEntity
import com.ead.evcharge.data.model.NearbyStationResponse
import com.ead.evcharge.data.model.StationResponse
import com.ead.evcharge.data.remote.ApiService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.math.*

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

    // Calculate distance between two coordinates in kilometers
    fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6371.0 // Earth radius in kilometers

        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)

        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)

        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return earthRadius * c
    }

    // Get nearby station IDs from API
    suspend fun getNearbyStationIds(
        latitude: Double,
        longitude: Double,
        radiusKm: Double = 20.0
    ): Result<Set<String>> {
        return try {
            Log.d(TAG, "🔍 Fetching nearby stations: lat=$latitude, lng=$longitude, radius=${radiusKm}km")

            val response = apiService.getNearbyStations(latitude, longitude, radiusKm)

            if (response.isSuccessful) {
                response.body()?.let { nearbyStations ->
                    val stationIds = nearbyStations.map { it.id }.toSet()
                    Log.d(TAG, "✅ Found ${stationIds.size} nearby station IDs")
                    Result.success(stationIds)
                } ?: run {
                    Log.e(TAG, "❌ Empty response body")
                    Result.failure(Exception("Empty response"))
                }
            } else {
                Log.e(TAG, "❌ API error: ${response.code()}")
                Result.failure(Exception("API error: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "💥 Error fetching nearby stations: ${e.message}", e)
            Result.failure(e)
        }
    }

    // Sync stations from server
    suspend fun syncStationsFromServer(): Result<List<StationEntity>> {
        return try {
            Log.d(TAG, "🔄 Syncing all stations from server")

            val response = apiService.getAllStations()

            if (response.isSuccessful) {
                response.body()?.let { stationResponses ->
                    Log.d(TAG, "✅ Fetched ${stationResponses.size} stations")

                    // Convert API response to Room entities
                    val stationEntities = stationResponses.map { it.toEntity() }

                    stationDao.deleteAllStations()

                    // Save new data to local database
                    stationDao.insertStations(stationEntities)

                    Log.d(TAG, "💾 Saved ${stationEntities.size} stations to database")
                    Result.success(stationEntities)
                } ?: run {
                    Log.e(TAG, "❌ Empty response body")
                    // Fallback to local data (don't clear if API fails)
                    val localStations = stationDao.getAllStations().first()
                    Result.success(localStations)
                }
            } else {
                Log.e(TAG, "❌ API error: ${response.code()}")
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
                Log.d(TAG, "📦 Returning ${localStations.size} cached stations (offline)")
                Result.success(localStations)
            } else {
                Result.failure(Exception("No cached station data available"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "💥 Error getting cached stations: ${e.message}", e)
            Result.failure(e)
        }
    }

    // Save single station
    suspend fun saveStation(station: StationEntity) {
        try {
            stationDao.insertStation(station)
            Log.d(TAG, "💾 Station saved: ${station.name}")
        } catch (e: Exception) {
            Log.e(TAG, "💥 Error saving station: ${e.message}", e)
        }
    }

    // Clear all stations
    suspend fun clearAllStations() {
        try {
            stationDao.deleteAllStations()
            Log.d(TAG, "🗑️ All stations cleared")
        } catch (e: Exception) {
            Log.e(TAG, "💥 Error clearing stations: ${e.message}", e)
        }
    }

    // Check if we have local stations
    suspend fun hasLocalStations(): Boolean {
        return try {
            stationDao.getStationCount() > 0
        } catch (e: Exception) {
            Log.e(TAG, "💥 Error checking local stations: ${e.message}", e)
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
