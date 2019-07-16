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
import io.kotlintest.matchers.collections.shouldContainExactly
import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec

/**
 * Test class for _LocationFileState_.
 */
class LocationFileStateSpec : StringSpec() {
    init {
        "LocationFileState should detect a changed state" {
            val files1 = createFiles(1..3)
            val files2 = createFiles(2..4)
            val state = LocationFileState(files1, emptyMap())

            state.stateChanged(files2) shouldBe true
        }

        "LocationFileState should detect that the state has not changed" {
            val files1 = createFiles(3..8)
            val files2 = createFiles(3..8)
            val state = LocationFileState(files1, emptyMap())

            state.stateChanged(files2) shouldBe false
        }

        "LocationFilesState should determine the files that are new" {
            val newFiles = createFiles(2..5)
            val state = createState(1..3)

            state.filterNewFiles(newFiles) shouldContainExactly createFiles(4..5)
        }

        "LocationFilesState should return a map with known marker data" {
            val state = createState(1..8)
            val newFiles = createFiles(4..12)

            val markerMap = state.getKnownMarkers(newFiles)
            markerMap shouldBe createMarkerDataMap(4..8)
        }

        "LocationFilesState should return an empty map with markers if there is no overlap" {
            val state = createState(1..8)
            val newFiles = createFiles(9..16)

            val markerMap = state.getKnownMarkers(newFiles)
            markerMap.isEmpty() shouldBe true
        }
    }

    companion object {
        /** A prefix for generated test file names.*/
        private const val filePrefix = "file"

        /**
         * Generates the name of a test file.
         * @param index the index of the test file
         * @return the name of this test file
         */
        private fun createFile(index: Int): String = "$filePrefix$index"

        /**
         * Generates a list with test files based on the given range.
         * @param range defines the indices of the test files
         * @return a list with the names of all test files
         */
        private fun createFiles(range: IntRange): List<String> =
            range.map { createFile(it) }

        /**
         * Creates a test marker data with the given index.
         * @param index the index of this marker
         * @return the marker data with this index
         */
        private fun createMarkerData(index: Int): MarkerData {
            val locData = LocationData(
                41.0 + index / 10.0, 9.0 - index / 10.0,
                TimeData(20190716213000L + index)
            )
            val pos = LatLng(locData.latitude, locData.longitude)
            return MarkerData(locData, pos)
        }

        /**
         * Generates a map from file names to marker data in the given range of
         * indices.
         * @param range defines the indices for the test data
         * @return a map with the test data
         */
        private fun createMarkerDataMap(range: IntRange): MutableMap<String, MarkerData> {
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
        private fun createState(range: IntRange): LocationFileState =
            LocationFileState(createFiles(range), createMarkerDataMap(range))
    }
}