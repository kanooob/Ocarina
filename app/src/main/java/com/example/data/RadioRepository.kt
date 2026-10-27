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

    suspend fun getAllStationsSync(): List<RadioStation> = dao.getAllStationsSync()

    suspend fun replaceAllStations(newStations: List<RadioStation>) {
        dao.deleteAllStations()
        if (newStations.isNotEmpty()) {
            val stationsWithSelection = newStations.mapIndexed { index, station ->
                station.copy(id = 0, isSelected = index == 0)
            }
            dao.insertStations(stationsWithSelection)
        }
    }

    suspend fun mergeStations(incomingStations: List<RadioStation>) {
        val current = dao.getAllStationsSync()
        val currentUrls = current.map { it.streamUrl.trim().lowercase() }.toSet()
        val toInsert = incomingStations.filter { it.streamUrl.trim().lowercase() !in currentUrls }
        if (toInsert.isNotEmpty()) {
            val markSelected = current.isEmpty()
            val mapped = toInsert.mapIndexed { index, s ->
                s.copy(id = 0, isSelected = markSelected && index == 0)
            }
            dao.insertStations(mapped)
        }
    }
}
