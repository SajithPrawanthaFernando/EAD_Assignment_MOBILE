package com.ead.evcharge.data.local.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import com.ead.evcharge.data.local.entity.BookingEntity

@Dao
interface BookingDao {
    @Query("SELECT * FROM bookings WHERE ownerNic = :ownerNic ORDER BY startTimeUtc DESC")
    fun getBookingsForOwner(ownerNic: String): Flow<List<BookingEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(bookings: List<BookingEntity>)

    @Query("DELETE FROM bookings WHERE ownerNic = :ownerNic")
    suspend fun deleteForOwner(ownerNic: String)
}