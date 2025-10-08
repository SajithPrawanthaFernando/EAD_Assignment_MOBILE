// data/repository/BookingRepository.kt
package com.ead.evcharge.data.repository

import android.util.Log
import com.ead.evcharge.data.local.dao.BookingDao
import com.ead.evcharge.data.local.entity.BookingEntity
import com.ead.evcharge.data.model.BookingResponse
import com.ead.evcharge.data.remote.ApiService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class BookingRepository(
    private val bookingDao: BookingDao,
    private val apiService: ApiService
) {

    companion object {
        private const val TAG = "BookingRepository"
    }

    fun getBookings(ownerNic: String): Flow<List<BookingEntity>> =
        bookingDao.getBookingsForOwner(ownerNic)

    suspend fun syncBookings(ownerNic: String): Result<List<BookingEntity>> {
        return try {
            Log.d(TAG, "🌐 Syncing bookings for NIC: $ownerNic")

            val response = apiService.getBookingsForOwner(ownerNic)

            if (response.isSuccessful) {
                response.body()?.let { bookings ->
                    Log.d(TAG, "✅ Fetched ${bookings.size} bookings from API")

                    // Clear old bookings
                    bookingDao.deleteForOwner(ownerNic)

                    // Convert to entities and save
                    val entities = bookings.map { it.toEntity() }
                    bookingDao.insertAll(entities)

                    Log.d(TAG, "💾 Saved ${entities.size} bookings to database")
                    Result.success(entities)
                } ?: run {
                    Log.w(TAG, "⚠️ Empty response body, using cached data")
                    Result.success(bookingDao.getBookingsForOwner(ownerNic).first())
                }
            } else {
                Log.e(TAG, "❌ API error: ${response.code()} - ${response.message()}")
                Result.success(bookingDao.getBookingsForOwner(ownerNic).first())
            }
        } catch (e: Exception) {
            Log.e(TAG, "💥 Error syncing bookings: ${e.message}", e)
            Result.success(bookingDao.getBookingsForOwner(ownerNic).first())
        }
    }
}

// Extension function to map BookingResponse to BookingEntity
private fun BookingResponse.toEntity(): BookingEntity {
    // Find the slot to get its label
    val slot = station.slots.find { it.slotId == slotId }

    return BookingEntity(
        id = this.id,
        ownerNic = this.ownerNic,
        stationId = this.station.id,           // Extract from nested station
        stationName = this.station.name,       // Extract station name
        slotId = this.slotId,
        slotLabel = slot?.label ?: "Unknown",  // Extract slot label
        startTimeUtc = this.startTimeUtc,
        status = this.status,
        lastSyncTimestamp = System.currentTimeMillis()
    )
}
