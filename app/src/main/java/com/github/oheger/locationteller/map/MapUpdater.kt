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

import android.location.Location
import com.github.oheger.locationteller.server.ServerConfig
import com.github.oheger.locationteller.server.TrackService
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*
import kotlin.math.round

/**
 * A class providing functionality to update a map with the most recent
 * locations loaded from the server.
 *
 * This class defines a function that loads the current status of location
 * files from the server. If there is a change compared with the current state,
 * the map is updated by adding markers for the new locations.
 *
 * Optionally, it is possible to display not only locations from the server but
 * also the location of the current device (_own location_) on the map. If this
 * data is passed, a marker with a different color is added to the map; in the
 * text of this marker the distance to the last known server location is
 * displayed.
 *
 * There are some other functions to execute some operations on the map, e.g.
 * setting specific zoom levels.
 *
 * @param serverConfig the configuration of the track server
 * @param distanceTemplate a format string for the distance of the own location
 * to the last known server location
 * @param trackServiceFactory a factory to create new track service instances
 */
class MapUpdater(
    val serverConfig: ServerConfig, val distanceTemplate: String,
    val trackServiceFactory: (ServerConfig) -> TrackService = defaultTrackServerFactory
) {
    /**
     * Updates the given map with the new state fetched from the server if
     * necessary. The updated state is returned which becomes the current
     * state for the next update.
     * @param map the map to be updated
     * @param currentState the current state of location data
     * @param ownLocation the optional own location
     * @param markerFactory the factory for creating markers
     * @param currentTime the current time
     * @return the updated state of location data
     */
    suspend fun updateMap(
        map: GoogleMap, currentState: MapMarkerState, ownLocation: MarkerData?,
        markerFactory: MarkerFactory, currentTime: Long
    ): MapMarkerState = withContext(Dispatchers.IO) {
        val trackService = trackServiceFactory(serverConfig)
        val filesOnServer = trackService.filesOnServer()
        val newLocationState = if (currentState.locations.stateChanged(filesOnServer)) {
            val knownData = createMarkerDataMap(currentState.locations, filesOnServer, trackService)
            val newState = createNewLocationState(filesOnServer, knownData)
            updateMarkers(map, newState, markerFactory, currentTime)
            newState
        } else currentState.locations

        val newOwnMarker = ownLocation?.let {
            showOwnLocation(map, newLocationState, it, currentState.ownMarker, markerFactory, currentTime)
        }
        MapMarkerState(newLocationState, newOwnMarker)
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
        centerMarker(map, state.recentMarker())
    }

    /**
     * Moves the camera of the map, so that the specified marker is in the
     * center. The zoom level is not changed. If the marker is *null*, this
     * function has not effect.
     * @param map the map
     * @param marker the marker to be centered
     */
    fun centerMarker(map: GoogleMap, marker: MarkerData?) {
        if (marker != null) {
            val currentZoom = map.cameraPosition.zoom
            val cameraPosition = CameraPosition.Builder()
                .target(marker.position)
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
     * @param markerFactory the factory to create markers
     * @param time the current time
     */
    private suspend fun updateMarkers(
        map: GoogleMap, state: LocationFileState, markerFactory: MarkerFactory,
        time: Long
    ) = withContext(Dispatchers.Main) {
        map.clear()
        val recentMarker = state.recentMarker()
        val markerData = state.files.mapNotNull { state.markerData[it] }
        markerData.withIndex()
            .forEach { item ->
                val options = markerFactory.createMarker(
                    item.value, time, recentMarker = item.value == recentMarker,
                    zIndex = item.index.toFloat()
                )
                map.addMarker(options)
            }
    }

    /**
     * Draws a marker representing the own location on the map. If there is a
     * last known location in the state, the distance between this and the own
     * location is calculated and added to the marker text. The marker for the
     * own location has a different color, but otherwise behaves like other
     * markers (also with regards to its alpha value).
     * @param map the map to be updated
     * @param state the current state of location data
     * @param ownLocation the own location
     * @param lastOwnMarker the last marker for the own position
     * @param markerFactory the factory to create markers
     * @param time the current time
     */
    private suspend fun showOwnLocation(
        map: GoogleMap, state: LocationFileState, ownLocation: MarkerData, lastOwnMarker: Marker?,
        markerFactory: MarkerFactory, time: Long
    ): Marker? = withContext(Dispatchers.Main) {
        lastOwnMarker?.remove()
        val recentLocation = state.recentMarker()
        val distanceString = generateDistanceString(recentLocation, ownLocation)
        val options = markerFactory.createMarker(
            ownLocation, time, recentMarker = false,
            zIndex = state.files.size.toFloat() + 1, text = distanceString, color = BitmapDescriptorFactory.HUE_GREEN
        )
        map.addMarker(options)
    }

    /**
     * Generates a string for the distance between the last known server
     * location and the own location. The server location may be undefined,
     * then the string is *null*. Otherwise, the distance is calculated and
     * formatted according to the distance template string.
     * @param recentLocation the recent location from the server
     * @param ownLocation the own location
     * @return a string with the distance between these locations
     */
    private fun generateDistanceString(
        recentLocation: MarkerData?,
        ownLocation: MarkerData
    ): String? = recentLocation?.let {
        val res = FloatArray(1)
        Location.distanceBetween(
            it.locationData.latitude, it.locationData.longitude,
            ownLocation.locationData.latitude, ownLocation.locationData.longitude, res
        )
        String.format(Locale.ROOT, distanceTemplate, round(res[0]).toInt())
    }

    companion object {
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
    }
}
