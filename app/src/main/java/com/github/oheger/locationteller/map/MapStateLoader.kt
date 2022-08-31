/*
 * Copyright 2019-2022 The Developers.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.oheger.locationteller.map

import com.github.oheger.locationteller.server.TrackService

import com.google.android.gms.maps.model.LatLng

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A helper class that is able to load the recorded locations from the server and to produce a new [LocationFileState]
 * object based on this information.
 */
class MapStateLoader(
    /** The [TrackService] instance for loading data from the server. */
    val trackService: TrackService
) {
    /**
     * Return an updated [LocationFileState] from location information retrieved from the server. Use the information
     * already available in [currentState] and only load new information from the server.
     */
    suspend fun loadMapState(currentState: LocationFileState): LocationFileState = withContext(Dispatchers.IO) {
        val filesOnServer = trackService.filesOnServer()
        if (currentState.stateChanged(filesOnServer)) {
            val knownData = createMarkerDataMap(currentState, filesOnServer, trackService)
            val newState = createNewLocationState(filesOnServer, knownData)
            newState
        } else currentState
    }

    /**
     * Create a map with information about all markers to be placed on the map. Based on the delta between
     * [currentState] and [filesOnServer], the [trackService] is invoked to load new location data. Then the existing
     * and new information is combined.
     */
    private suspend fun createMarkerDataMap(
        currentState: LocationFileState,
        filesOnServer: List<String>,
        trackService: TrackService
    ): Map<String, MarkerData> {
        val newFiles = currentState.filterNewFiles(filesOnServer)
        val knownData = currentState.getKnownMarkers(filesOnServer)

        return newFiles.takeUnless { it.isEmpty() }?.let { files ->
            val newMarkers = trackService.readLocations(files).mapValues { e ->
                val position = LatLng(e.value.latitude, e.value.longitude)
                MarkerData(e.value, position)
            }
            knownData + newMarkers
        } ?: knownData
    }

    /**
     * Create a new [LocationFileState] object from the [location files][filesOnServer] loaded from
     * the server and the resolved [markerData]. Note that this object contains only the files for which
     * location data could be loaded successfully.
     */
    private fun createNewLocationState(
        filesOnServer: List<String>,
        markerData: Map<String, MarkerData>
    ): LocationFileState = LocationFileState(filesOnServer.filter(markerData::containsKey), markerData)
}
