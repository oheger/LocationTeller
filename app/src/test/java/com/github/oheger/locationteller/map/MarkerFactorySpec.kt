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

import android.content.Context
import android.content.res.Resources
import com.github.oheger.locationteller.R
import com.github.oheger.locationteller.map.LocationTestHelper.createFile
import com.github.oheger.locationteller.map.LocationTestHelper.createLocationData
import com.github.oheger.locationteller.map.LocationTestHelper.createMarkerData
import com.github.oheger.locationteller.map.LocationTestHelper.createState
import com.github.oheger.locationteller.server.TimeData
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import io.kotlintest.shouldBe
import io.kotlintest.shouldThrow
import io.kotlintest.specs.StringSpec
import io.mockk.every
import io.mockk.mockk

/**
 * Test class for _MarkerFactory_.
 */
class MarkerFactorySpec : StringSpec() {
    init {
        "MarkerFactory should set the correct position" {
            val state = createState(1..8)
            val index = 5
            val key = createFile(index)
            val expLocation = createMarkerData(index)
            val factory = createFactory()

            val options = factory.createMarker(state, key, 0)
            options.position shouldBe expLocation.position
        }

        "MarkerFactory should throw if an invalid key is passed" {
            val state = createState(1..2)
            val factory = createFactory()

            shouldThrow<IllegalArgumentException> {
                factory.createMarker(state, createFile(4), 0)
            }
        }

        "MarkerFactory should set a title using the seconds time unit" {
            val data = createMarkerDataWithTime(currentTime - 59 * 1000L)
            val options = createOptionsForMarker(data)

            options.title shouldBe "59 $unitSec"
        }

        "MarkerFactory should set a title using the minutes time unit for a delta > 1 minute" {
            val data = createMarkerDataWithTime(currentTime - 60 * 1000L)
            val options = createOptionsForMarker(data)

            options.title shouldBe "1 $unitMin"
        }

        "MarkerFactory should set a title using the minutes time unit for a delta < 1 hour" {
            val data = createMarkerDataWithTime(currentTime - 60 * 60 * 1000L + 1)
            val options = createOptionsForMarker(data)

            options.title shouldBe "59 $unitMin"
        }

        "MarkerFactory should set a title using the hours time unit for a delta >= 1 hour" {
            val delta = createMarkerDataWithTime(currentTime - 60 * 60 * 1000L)
            val options = createOptionsForMarker(delta)

            options.title shouldBe "1 $unitHour"
        }

        "MarkerFactory should set a title using the hours time unit for a delta < 1 day" {
            val delta = createMarkerDataWithTime(currentTime - 24 * 60 * 60 * 1000L + 1)
            val options = createOptionsForMarker(delta)

            options.title shouldBe "23 $unitHour"
        }

        "MarkerFactory should set a title using the days time unit for a delta >= 1 day" {
            val delta = createMarkerDataWithTime(currentTime - 24 * 60 * 60 * 1000L)
            val options = createOptionsForMarker(delta)

            options.title shouldBe "1 $unitDay"
        }

        "MarkerFactory should set a title using the days time unit for larger deltas" {
            val delta = createMarkerDataWithTime(currentTime - 5 * 24 * 60 * 60 * 1000L + 1)
            val options = createOptionsForMarker(delta)

            options.title shouldBe "4 $unitDay"
        }
    }

    companion object {
        /** Time unit for seconds.*/
        private const val unitSec = "s"

        /** Time unit for minutes.*/
        private const val unitMin = "m"

        /** Time unit for hours.*/
        private const val unitHour = "h"

        /** Time unit for days.*/
        private const val unitDay = "d"

        /** The current time passed to the factory.*/
        private const val currentTime = 20190723220948L

        /**
         * Creates a _MarkerData_ object with the given timestamp.
         * @param time the time of the marker
         * @return the _MarkerData_ with this time
         */
        private fun createMarkerDataWithTime(time: Long): MarkerData {
            val locData = createLocationData(1).copy(time = TimeData(time))
            return MarkerData(locData, LatLng(47.0, 8.0))
        }

        /**
         * Creates a state object that contains only the single marker data.
         * @param data the _MarkerData_
         * @return the state
         */
        private fun createStateWithData(data: MarkerData): LocationFileState {
            val key = data.locationData.time.timeString
            return LocationFileState(listOf(key), mapOf(key to data))
        }

        /**
         * Invokes the factory to create marker options for the given marker
         * data. Creates a state that contains only this data. Then creates a
         * test factory and invokes it.
         * @param data the test _MarkerData_
         * @return the options created for this data
         */
        private fun createOptionsForMarker(data: MarkerData): MarkerOptions {
            val state = createStateWithData(data)
            return createFactory().createMarker(state, state.files.first(), currentTime)
        }

        /**
         * Creates an Android context that is prepared to return resources for
         * the time units used by the factory.
         */
        private fun createContext(): Context {
            val context = mockk<Context>()
            val resources = mockk<Resources>()
            every { context.resources } returns resources
            every { resources.getString(R.string.time_secs) } returns unitSec
            every { resources.getString(R.string.time_minutes) } returns unitMin
            every { resources.getString(R.string.time_hours) } returns unitHour
            every { resources.getString(R.string.time_days) } returns unitDay
            return context
        }

        /**
         * Creates a marker factory with a mock context.
         * @return the marker factory
         */
        private fun createFactory(): MarkerFactory =
            MarkerFactory.create(createContext())
    }
}
