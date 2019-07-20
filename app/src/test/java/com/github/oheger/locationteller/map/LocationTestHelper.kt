/*
 * Copyright 2019 The Developers.
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
import com.github.oheger.locationteller.server.TimeData
import com.google.android.gms.maps.model.LatLng

/**
 * A test helper object that can generate test data related to location files,
 * positions, and markers.
 */
object LocationTestHelper {
    /** A prefix for generated test file names.*/
    private const val filePrefix = "file"

    /**
     * Generates the name of a test file.
     * @param index the index of the test file
     * @return the name of this test file
     */
    fun createFile(index: Int): String = "$filePrefix$index"

    /**
     * Generates a list with test files based on the given range.
     * @param range defines the indices of the test files
     * @return a list with the names of all test files
     */
    fun createFiles(range: IntRange): List<String> =
        range.map { createFile(it) }

    /**
     * Creates a test location data object with the given index.
     * @param index the index of the location data
     * @return the location data with this index
     */
    fun createLocationData(index: Int): LocationData =
        LocationData(
            41.0 + index / 10.0, 9.0 - index / 10.0,
            TimeData(20190716213000L + index)
        )

    /**
     * Generates a map from files names to location data in the given range of
     * indices.
     * @param range defines the indices for the test data
     * @return a map with the test data
     */
    fun createLocationDataMap(range: IntRange): MutableMap<String, LocationData> {
        val result = mutableMapOf<String, LocationData>()
        for (idx in range) {
            val fileName = createFile(idx)
            val locData = createLocationData(idx)
            result[fileName] = locData
        }
        return result
    }

    /**
     * Creates a test marker data with the given index.
     * @param index the index of this marker
     * @return the marker data with this index
     */
    fun createMarkerData(index: Int): MarkerData {
        val locData = createLocationData(index)
        val pos = LatLng(locData.latitude, locData.longitude)
        return MarkerData(locData, pos)
    }

    /**
     * Generates a map from file names to marker data in the given range of
     * indices.
     * @param range defines the indices for the test data
     * @return a map with the test data
     */
    fun createMarkerDataMap(range: IntRange): MutableMap<String, MarkerData> {
        val result = mutableMapOf<String, MarkerData>()
        for (idx in range) {
            val fileName = createFile(idx)
            val marker = createMarkerData(idx)
            result[fileName] = marker
        }
        return result
    }

    /**
     * Creates a _LocationFileState_ object that contains data about the
     * test files in the given range.
     * @param range defines the indices of the test files in the state
     * @return the state object containing these test files
     */
    fun createState(range: IntRange): LocationFileState =
        LocationFileState(createFiles(range), createMarkerDataMap(range))
}