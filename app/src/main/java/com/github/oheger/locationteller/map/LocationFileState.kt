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

import com.github.oheger.locationteller.server.LocationData

import com.google.android.gms.maps.model.LatLng

/**
 * A data class storing information about a marker on a map.
 */
data class MarkerData(
    /** The [LocationData] object associated with the marker. */
    val locationData: LocationData,

  /** The position of the marker as [LatLng] object. */
    val position: LatLng
)

/**
 * A class storing information about the location files that are currently available on the server.
 *
 * This class is used to display a map with the locations recorded and to update this map when new data arrives.
 */
data class LocationFileState(
    /** The current list of location files on the server. */
    val files: List<String>,

    /** A map assigning a [MarkerData] object to a file path. */
    val markerData: Map<String, MarkerData>
) {
    companion object {
        /**
         * Constant for an empty state. This constant can be used as initial state when loading data from the server.
         */
        val EMPTY = LocationFileState(emptyList(), emptyMap())
    }

    /**
     * Check this state against the given list of [new location files][newFiles] and return a flag whether the list
     * has changed. If this function returns *true*, the map view needs to be updated.
     */
    fun stateChanged(newFiles: List<String>): Boolean = newFiles != files

    /**
     * Return the most recent [MarkerData] object. This is the marker that corresponds to the latest location file
     * uploaded to the server. If the state is empty, result is *null*.
     */
    fun recentMarker(): MarkerData? = files.lastOrNull()?.let(markerData::get)

    /**
     * Return a list that contains only the files from the passed in list that are not contained in this state.
     * For these [newFiles] no location information is available and has to be retrieved first from the server.
     */
    fun filterNewFiles(newFiles: List<String>): List<String> =
        newFiles.filterNot(markerData::containsKey)

    /**
     * Return a mutable map that contains all the marker data for the specified [newFiles] that are contained in this
     * state object. This function can be used when an updated state has been retrieved from the server; then the
     * data for files already known can be reused.
     */
    fun getKnownMarkers(newFiles: List<String>): MutableMap<String, MarkerData> {
        val result = mutableMapOf<String, MarkerData>()
        newFiles.filter(markerData::containsKey)
            .forEach { file -> result[file] = markerData.getValue(file) }
        return result
    }
}
