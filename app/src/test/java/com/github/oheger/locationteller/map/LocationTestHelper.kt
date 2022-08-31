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
import com.github.oheger.locationteller.server.TimeData

import com.google.android.gms.maps.model.LatLng

/**
 * A test helper object that can generate test data related to location files,
 * positions, and markers.
 */
object LocationTestHelper {
    /** A prefix for generated test file names.*/
    private const val FILE_PREFIX = "file"

    /**
     * Generate the name of a test file based on the given [index].
     */
    fun createFile(index: Int): String = "$FILE_PREFIX$index"

    /**
     * Generate a list with test files based on the given [range].
     */
    fun createFiles(range: IntRange): List<String> = range.map { createFile(it) }

    /**
     * Create a test location data object based on the given [index].
     */
    fun createLocationData(index: Int): LocationData =
        LocationData(
            latitude = 41.0 + index / 10.0,
            longitude = 9.0 - index / 10.0,
            time = TimeData(20190716213000L + index)
        )

    /**
     * Generate a map from file names to location data in the given [range] of indices.
     */
    fun createLocationDataMap(range: IntRange): MutableMap<String, LocationData> =
        range.associate { idx -> createFile(idx) to createLocationData(idx) }.toMutableMap()

    /**
     * Create a test marker data based on the given [index].
     */
    fun createMarkerData(index: Int): MarkerData {
        val locData = createLocationData(index)
        val pos = LatLng(locData.latitude, locData.longitude)
        return MarkerData(locData, pos)
    }

    /**
     * Generate a map from file names to marker data in the given [range] of indices.
     */
    fun createMarkerDataMap(range: IntRange): MutableMap<String, MarkerData> =
        range.associate { idx -> createFile(idx) to createMarkerData(idx) }.toMutableMap()

    /**
     * Create a [LocationFileState] object that contains data about the test files in the given [range].
     */
    fun createState(range: IntRange): LocationFileState =
        LocationFileState(createFiles(range), createMarkerDataMap(range))
}
