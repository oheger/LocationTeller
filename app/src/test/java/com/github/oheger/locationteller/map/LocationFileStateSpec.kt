/*
 * Copyright 2019-2021 The Developers.
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

import com.github.oheger.locationteller.map.LocationTestHelper.createFiles
import com.github.oheger.locationteller.map.LocationTestHelper.createMarkerData
import com.github.oheger.locationteller.map.LocationTestHelper.createMarkerDataMap
import com.github.oheger.locationteller.map.LocationTestHelper.createState
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

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

        "LocationFileState should return the most recent marker data" {
            val state = createState(1..16)
            val exp = createMarkerData(16)

            state.recentMarker() shouldBe exp
        }

        "LocationFileState should return null for the recent marker if empty" {
            val state = LocationFileState(emptyList(), emptyMap())

            state.recentMarker() shouldBe null
        }
    }

}
