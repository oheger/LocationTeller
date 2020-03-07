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

import com.github.oheger.locationteller.map.LocationTestHelper.createLocationData
import com.github.oheger.locationteller.map.LocationTestHelper.createMarkerData
import com.github.oheger.locationteller.server.TimeData
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import io.kotlintest.matchers.floats.shouldBeGreaterThan
import io.kotlintest.matchers.floats.shouldBeGreaterThanOrEqual
import io.kotlintest.matchers.floats.shouldBeLessThan
import io.kotlintest.matchers.floats.shouldBeLessThanOrEqual
import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.verify

/**
 * Test class for _MarkerFactory_.
 */
class MarkerFactorySpec : StringSpec() {
    init {
        "MarkerFactory should set the correct position" {
            val data = createMarkerData(5)

            val options = createOptionsForMarker(data)
            options.position shouldBe data.position
        }

        "MarkerFactory should set a title using the time formatter" {
            val delta = 987123L
            val data = createMarkerDataWithTime(currentTime - delta)
            val factory = createFactory()
            val options = callFactoryForMarker(data, factory)

            options.title shouldBe title
            verify { factory.deltaFormatter.formatTimeDelta(delta) }
        }

        "MarkerFactory should set default values for optional properties" {
            val data = createMarkerDataWithTime(currentTime - 10000)
            val options = createOptionsForMarker(data)

            options.icon shouldBe null
            options.zIndex shouldBe 0f
            options.snippet shouldBe null
        }

        "MarkerFactory should set the zIndex when creating a marker" {
            val zIndex = 11f
            val data = createMarkerData(1)
            val factory = createFactory()

            val options = factory.createMarker(data, currentTime, recentMarker = false, zIndex = zIndex)
            options.zIndex shouldBe zIndex
        }

        "MarkerFactory should set an additional text when creating a marker" {
            val text = "This is additional information"
            val data = createMarkerData(2)
            val factory = createFactory()

            val options = factory.createMarker(data,currentTime - 111, recentMarker = false, text = text)
            options.snippet shouldBe text
        }

        "MarkerFactory should evaluate the color of the marker" {
            val color = BitmapDescriptorFactory.HUE_GREEN
            val descriptor = mockk<BitmapDescriptor>()
            mockkStatic(BitmapDescriptorFactory::class)
            every { BitmapDescriptorFactory.defaultMarker(color) } returns descriptor
            val data = createMarkerData(3)
            val factory = createFactory()

            val options = factory.createMarker(data, currentTime - 22, recentMarker = false, color = color)
            options.icon shouldBe descriptor
        }

        "MarkerFactory should set the alpha of the most recent marker to 1" {
            val marker = createMarkerDataWithTime(currentTime - 5 * 24 * 60 * 60 * 1000)
            val options = createOptionsForMarker(marker, recentMarker = true)

            options.alpha shouldBe 1f
        }

        "MarkerFactory should set the alpha for markers in the minute range" {
            val marker1 = createMarkerDataWithTime(currentTime - 3 * 60 * 1000)
            val marker2 = createMarkerDataWithTime(currentTime - 59 * 60 * 1000)
            val factory = createFactory()

            val options1 = factory.createMarker(marker2, currentTime, recentMarker = false)
            val options2 = factory.createMarker(marker1, currentTime, recentMarker = false)
            options2.alpha shouldBeLessThan 1f
            options2.alpha shouldBeGreaterThan 0.9f
            options2.alpha shouldBeGreaterThan options1.alpha
            options1.alpha shouldBeGreaterThanOrEqual MarkerFactory.AlphaMinutesMin
        }

        "MarkerFactory should set the alpha for markers in the hours range" {
            val deltaHour = 60 * 60 * 1000
            val marker1 = createMarkerDataWithTime(currentTime - 23 * deltaHour - 60)
            val marker2 = createMarkerDataWithTime(currentTime - 1 * deltaHour)
            val factory = createFactory()

            val options2 = factory.createMarker(marker2, currentTime, recentMarker = false)
            val options1 = factory.createMarker(marker1, currentTime, recentMarker = false)
            options2.alpha shouldBeLessThanOrEqual MarkerFactory.AlphaHoursMax
            options2.alpha shouldBeGreaterThan MarkerFactory.AlphaHoursMax - 0.02f
            options2.alpha shouldBeGreaterThan options1.alpha
            options1.alpha shouldBeGreaterThan MarkerFactory.AlphaHoursMin
        }

        "MarkerFactory should set the alpha for markers in the days range" {
            val deltaDay = (24 * 60 * 60 + 1) * 1000L
            val marker1 = createMarkerDataWithTime(currentTime - deltaDay)
            val marker2 = createMarkerDataWithTime(currentTime - 50 * deltaDay)
            val factory = createFactory()

            val options2 = factory.createMarker(marker1, currentTime, recentMarker = false)
            val options1 = factory.createMarker(marker2, currentTime, recentMarker = false)
            options1.alpha shouldBe MarkerFactory.AlphaDays
            options2.alpha shouldBe MarkerFactory.AlphaDays
        }
    }

    companion object {
        /** The current time passed to the factory.*/
        private const val currentTime = 20190723220948L

        /** The title to be returned in marker options. */
        private const val title = "formattedTimeDelta"

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
         * Invokes the factory to create marker options for the given marker
         * data. Creates a state that contains only this data. Then creates a
         * test factory and invokes it.
         * @param data the test _MarkerData_
         * @param recentMarker flag whether this is the most recent marker
         * @return the options created for this data
         */
        private fun createOptionsForMarker(data: MarkerData, recentMarker: Boolean = false): MarkerOptions =
            callFactoryForMarker(data, createFactory(), recentMarker)

        /**
         * Invokes the given factory on a specific marker data object and
         * returns the result.
         * @param data the _MarkerData_
         * @param factory the test factory
         * @return the result produced by the factory
         */
        private fun callFactoryForMarker(
            data: MarkerData,
            factory: MarkerFactory,
            recentMarker: Boolean = false
        ): MarkerOptions {
            return factory.createMarker(data, currentTime, recentMarker)
        }

        /**
         * Creates a marker factory with a mock context.
         * @return the marker factory
         */
        private fun createFactory(): MarkerFactory {
            val formatter = mockk<TimeDeltaFormatter>()
            every { formatter.formatTimeDelta(any()) } returns title
            return MarkerFactory(formatter)
        }
    }
}
