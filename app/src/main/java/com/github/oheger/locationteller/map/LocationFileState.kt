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

import com.github.oheger.locationteller.server.LocationData
import com.google.android.gms.maps.model.LatLng

/**
 * A data class storing information about a marker on a map.
 *
 * @param locationData the _LocationData_ object associated with the marker
 * @param position the position of the marker as _LatLng_ object
 */
data class MarkerData(val locationData: LocationData, val position: LatLng)

/**
 * A class storing information about the location files that are currently
 * available on the server.
 *
 * This class is used to display a map with the locations recorded and to
 * update this map when new data arrives.
 *
 * @param files the current list of location files on the server
 * @param markerData a map assigning a _MarkerData_ object to a file path
 */
data class LocationFileState(
    val files: List<String>,
    val markerData: Map<String, MarkerData>
) {
    /**
     * Checks this state against the given list of location files and returns a
     * flag whether the list has changed. If this function returns *true*, the
     * map view needs to be updated.
     * @param newFiles a list with the new location files on the server
     */
    fun stateChanged(newFiles: List<String>): Boolean = newFiles != files

    /**
     * Returns the most recent _MarkerData_ object. This is the marker that
     * corresponds to the latest location file uploaded to the server. If the
     * state is empty, result is *null*.
     * @return the most recent _MarkerData_ or *null*
     */
    fun recentMarker(): MarkerData? =
        if (markerData.isNotEmpty()) markerData[files.last()]
        else null

    /**
     * Returns a list that contains only the newFiles from the passed in list that
     * are not contained in this state. For these newFiles no location information
     * is available and has to be retrieved first from the server.
     * @param newFiles the files to filter
     * @return a list with the newFiles that are new compared with this state
     */
    fun filterNewFiles(newFiles: List<String>): List<String> =
        newFiles.filterNot(markerData::containsKey)

    /**
     * Returns a mutable map that contains all the marker data for the files
     * specified that are contained in this state object. This function can be
     * used when an updated state has been retrieved from the server; then the
     * data for files already known can be reused.
     * @param newFiles the list with new files
     * @return a map with all known marker data
     */
    fun getKnownMarkers(newFiles: List<String>): MutableMap<String, MarkerData> {
        val result = mutableMapOf<String, MarkerData>()
        newFiles.filter(markerData::containsKey)
            .forEach { file -> result[file] = markerData.getValue(file) }
        return result
    }
}
