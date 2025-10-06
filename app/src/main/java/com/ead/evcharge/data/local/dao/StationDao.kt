package com.ead.evcharge.data.local.dao

import androidx.room.*
import com.ead.evcharge.data.local.entity.StationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StationDao {

    @Query("SELECT * FROM stations")
    fun getAllStations(): Flow<List<StationEntity>>

    @Query("SELECT * FROM stations WHERE active = 1")
    fun getActiveStations(): Flow<List<StationEntity>>

    @Query("SELECT * FROM stations WHERE id = :stationId LIMIT 1")
    fun getStationById(stationId: String): Flow<StationEntity?>

    @Query("SELECT * FROM stations WHERE id = :stationId LIMIT 1")
    suspend fun getStationByIdSync(stationId: String): StationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStation(station: StationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStations(stations: List<StationEntity>)

    @Update
    suspend fun updateStation(station: StationEntity)

    @Query("DELETE FROM stations")
    suspend fun deleteAllStations()

    @Query("DELETE FROM stations WHERE id = :stationId")
    suspend fun deleteStationById(stationId: String)

    @Query("SELECT COUNT(*) FROM stations")
    suspend fun getStationCount(): Int

    @Query("SELECT * FROM stations WHERE lat BETWEEN :minLat AND :maxLat AND lng BETWEEN :minLng AND :maxLng")
    fun getStationsInBounds(minLat: Double, maxLat: Double, minLng: Double, maxLng: Double): Flow<List<StationEntity>>
}