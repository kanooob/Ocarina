package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RadioStationDao {

    @Query("SELECT * FROM radio_stations ORDER BY createdAt ASC")
    fun getAllStations(): Flow<List<RadioStation>>

    @Query("SELECT * FROM radio_stations WHERE isSelected = 1 LIMIT 1")
    fun getSelectedStation(): Flow<RadioStation?>

    @Query("SELECT * FROM radio_stations WHERE isSelected = 1 LIMIT 1")
    suspend fun getSelectedStationSync(): RadioStation?

    @Query("SELECT * FROM radio_stations WHERE id = :id LIMIT 1")
    suspend fun getStationById(id: Long): RadioStation?

    @Query("SELECT COUNT(*) FROM radio_stations")
    suspend fun getStationCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStation(station: RadioStation): Long

    @Update
    suspend fun updateStation(station: RadioStation)

    @Delete
    suspend fun deleteStation(station: RadioStation)

    @Query("UPDATE radio_stations SET isSelected = 0")
    suspend fun clearSelection()

    @Query("UPDATE radio_stations SET isSelected = 1 WHERE id = :id")
    suspend fun setStationSelected(id: Long)

    @Transaction
    suspend fun selectOnlyStation(id: Long) {
        clearSelection()
        setStationSelected(id)
    }
}
