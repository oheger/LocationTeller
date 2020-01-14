/*
 * Copyright 2019-2020 The Developers.
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

import com.github.oheger.locationteller.server.ServerConfig
import com.github.oheger.locationteller.server.TrackService
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * An object providing functionality to update a map with the most recent
 * locations loaded from the server.
 *
 * This object defines a function that loads the current status of location
 * files from the server. If there is a change compared with the current state,
 * the map is updated by adding markers for the new locations.
 */
object MapUpdater {
    /**
     * A default function for creating a _TrackService_ from a server
     * configuration. This function just directly creates the service.
     */
    val defaultTrackServerFactory: (ServerConfig) -> TrackService =
        { config -> TrackService.create(config) }

    /**
     * Constant for the default zoom level. This is used when there are not
     * enough markers available to calculate a bounding box.
     */
    private const val defaultZoomLevel = 15f

    /**
     * Updates the given map with the new state fetched from the server if
     * necessary. The updated state is returned which becomes the current
     * state for the next update.
     * @param config the server configuration
     * @param map the map to be updated
     * @param currentState the current state of location data
     * @param markerFactory the factory for creating markers
     * @param currentTime the current time
     * @param trackServerFactory the factory for a _TrackService_ instance
     * @return the updated state of location data
     */
    suspend fun updateMap(
        config: ServerConfig, map: GoogleMap, currentState: LocationFileState,
        markerFactory: MarkerFactory, currentTime: Long,
        trackServerFactory: (ServerConfig) -> TrackService = defaultTrackServerFactory
    ):
            LocationFileState = withContext(Dispatchers.IO) {
        val trackService = trackServerFactory(config)
        val filesOnServer = trackService.filesOnServer()
        if (currentState.stateChanged(filesOnServer)) {
            val knownData = createMarkerDataMap(currentState, filesOnServer, trackService)
            val newState = createNewLocationState(filesOnServer, knownData)
            updateMarkers(map, newState, markerFactory, currentTime)
            newState
        } else currentState
    }

    /**
     * Sets the map to a zoom level and position so that all the markers in the
     * given state are visible. Note that this function must be called on the
     * main thread.
     * @param map the map
     * @param state the state with all the markers in question
     */
    fun zoomToAllMarkers(map: GoogleMap, state: LocationFileState) {
        if (state.markerData.isNotEmpty()) {
            if (state.markerData.size == 1) {
                val position = state.markerData.values.iterator().next().position
                val cameraUpdate = CameraUpdateFactory.newLatLngZoom(position, defaultZoomLevel)
                map.moveCamera(cameraUpdate)
            } else {
                val boundsBuilder = LatLngBounds.builder().apply {
                    for (markerData in state.markerData.values) {
                        include(markerData.position)
                    }
                }
                val cameraUpdate = CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 0)
                map.moveCamera(cameraUpdate)
            }
        }
    }

    /**
     * Moves the camera of the map, so that the most recent marker is in the
     * center. The zoom level is not changed. If the state is empty, this
     * function has no effect.
     * @param map the map
     * @param state the current state
     */
    fun centerRecentMarker(map: GoogleMap, state: LocationFileState) {
        val recentMarker = state.recentMarker()
        if (recentMarker != null) {
            val currentZoom = map.cameraPosition.zoom
            val cameraPosition = CameraPosition.Builder()
                .target(recentMarker.position)
                .zoom(currentZoom)
                .build()
            val cameraUpdate = CameraUpdateFactory.newCameraPosition(cameraPosition)
            map.moveCamera(cameraUpdate)
        }
    }

    /**
     * Creates a map with information about the new markers to be placed on the
     * map. Based on the delta to the last state the track service is invoked
     * to load new location data. Then the existing and new information is
     * combined.
     * @param currentState the current state of location data
     * @param filesOnServer the current list of files on the server
     * @param trackService the track service
     * @return a map with data about all known markers
     */
    private suspend fun createMarkerDataMap(
        currentState: LocationFileState,
        filesOnServer: List<String>,
        trackService: TrackService
    ): MutableMap<String, MarkerData> {
        val newFiles = currentState.filterNewFiles(filesOnServer)
        val knownData = currentState.getKnownMarkers(filesOnServer)
        if (newFiles.isNotEmpty()) {
            val newMarkers = trackService.readLocations(newFiles).mapValues { e ->
                val position = LatLng(e.value.latitude, e.value.longitude)
                MarkerData(e.value, position)
            }
            knownData.putAll(newMarkers)
        }
        return knownData
    }

    /**
     * Creates a new state object from the resolved location files loaded from
     * the server. Note that this object contains only the files for which
     * location data could be loaded successfully.
     * @param filesOnServer list of location files found on the server
     * @param markerData map with resolved marker data
     * @return the resulting new location state
     */
    private fun createNewLocationState(
        filesOnServer: List<String>,
        markerData: MutableMap<String, MarkerData>
    ) = LocationFileState(filesOnServer.filter(markerData::containsKey), markerData)

    /**
     * Draws markers on the given map according to the given state.
     * @param map the map to be updated
     * @param state the current state of location data
     */
    private suspend fun updateMarkers(
        map: GoogleMap, state: LocationFileState, markerFactory: MarkerFactory,
        time: Long
    ) = withContext(Dispatchers.Main) {
        map.clear()
        state.files.forEach { file ->
            val options = markerFactory.createMarker(state, file, time)
            map.addMarker(options)
        }
    }
}