package com.example.data

import kotlinx.coroutines.flow.Flow

class RadioRepository(private val dao: RadioStationDao) {

    val allStations: Flow<List<RadioStation>> = dao.getAllStations()
    val selectedStation: Flow<RadioStation?> = dao.getSelectedStation()

    suspend fun getSelectedStationSync(): RadioStation? = dao.getSelectedStationSync()

    suspend fun getStationById(id: Long): RadioStation? = dao.getStationById(id)

    suspend fun getStationCount(): Int = dao.getStationCount()

    suspend fun insertStation(station: RadioStation): Long {
        val count = dao.getStationCount()
        // If this is the very first station inserted, automatically mark it selected
        val stationToInsert = if (count == 0) station.copy(isSelected = true) else station
        val insertedId = dao.insertStation(stationToInsert)
        if (stationToInsert.isSelected) {
            dao.selectOnlyStation(insertedId)
        }
        return insertedId
    }

    suspend fun updateStation(station: RadioStation) {
        dao.updateStation(station)
        if (station.isSelected) {
            dao.selectOnlyStation(station.id)
        }
    }

    suspend fun deleteStation(station: RadioStation) {
        val wasSelected = station.isSelected
        dao.deleteStation(station)
        if (wasSelected) {
            // Select the next remaining station if available
            val count = dao.getStationCount()
            if (count > 0) {
                // Select first available
                // We'll update the first one
                dao.clearSelection()
            }
        }
    }

    suspend fun selectStation(id: Long) {
        dao.selectOnlyStation(id)
    }
}
