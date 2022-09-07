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

import com.github.oheger.locationteller.duration.TimeDeltaFormatter
import com.github.oheger.locationteller.map.LocationTestHelper.createLocationData
import com.github.oheger.locationteller.map.LocationTestHelper.createMarkerData
import com.github.oheger.locationteller.server.CurrentTimeService
import com.github.oheger.locationteller.server.TimeData
import com.github.oheger.locationteller.server.TimeService

import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.verify

/**
 * Test class for [MarkerFactory].
 */
class MarkerFactorySpec : WordSpec() {
    init {
        "the constructor" should {
            "create a default time service" {
                val factory = MarkerFactory(mockk(), DisabledFadeOutAlphaCalculator)

                factory.timeService shouldBe CurrentTimeService
            }
        }

        "createMarker" should {
            "set the correct position" {
                val data = createMarkerData(5)

                val options = createOptionsForMarker(data)
                options.position shouldBe data.position
            }

            "set a title using the time formatter" {
                val delta = 987123L
                val data = createMarkerDataWithTime(CURRENT_TIME - delta)
                val factory = createFactory()
                val options = callFactoryForMarker(data, factory)

                options.title shouldBe TITLE
                verify { factory.deltaFormatter.formatTimeDeltaOrTime(delta, data.locationData.time) }
            }

            "set default values for optional properties" {
                val data = createMarkerDataWithTime(CURRENT_TIME - 10000)
                val options = createOptionsForMarker(data)

                options.icon shouldBe null
                options.zIndex shouldBe 0f
                options.snippet shouldBe null
            }

            "set the zIndex when creating a marker" {
                val zIndex = 11f
                val data = createMarkerData(1)
                val factory = createFactory()

                val options = factory.createMarker(data, CURRENT_TIME, recentMarker = false, zIndex = zIndex)
                options.zIndex shouldBe zIndex
            }

            "set an additional text when creating a marker" {
                val text = "This is additional information"
                val data = createMarkerData(2)
                val factory = createFactory()

                val options = factory.createMarker(data, CURRENT_TIME - 111, recentMarker = false, text = text)
                options.snippet shouldBe text
            }

            "evaluate the color of the marker" {
                val color = BitmapDescriptorFactory.HUE_GREEN
                val descriptor = mockk<BitmapDescriptor>()
                mockkStatic(BitmapDescriptorFactory::class)
                every { BitmapDescriptorFactory.defaultMarker(color) } returns descriptor
                val data = createMarkerData(3)
                val factory = createFactory()

                val options = factory.createMarker(data, CURRENT_TIME - 22, recentMarker = false, color = color)
                options.icon shouldBe descriptor
            }

            "set the alpha of the most recent marker to 1" {
                val marker = createMarkerDataWithTime(CURRENT_TIME - 5 * 24 * 60 * 60 * 1000)
                val options = createOptionsForMarker(marker, recentMarker = true)

                options.alpha shouldBe 1f
            }

            "call the alpha calculator to determine the alpha value" {
                val delta = 3 * 60 * 1000L
                val marker = createMarkerDataWithTime(CURRENT_TIME - delta)
                val factory = createFactory()

                val options = factory.createMarker(marker, CURRENT_TIME, recentMarker = false)
                options.alpha shouldBe ALPHA
                verify {
                    factory.alphaCalculator.calculateAlpha(delta)
                }
            }
        }
    }

    companion object {
        /** The current time passed to the factory.*/
        private const val CURRENT_TIME = 20190723220948L

        /** The title to be returned in marker options. */
        private const val TITLE = "formattedTimeDelta"

        /** A test alpha value returned by the mock alpha calculator. */
        private const val ALPHA = 0.75f

        /**
         * Create a [MarkerData] object and associate it with the given [time].
         */
        private fun createMarkerDataWithTime(time: Long): MarkerData {
            val locData = createLocationData(1).copy(time = TimeData(time))
            return MarkerData(locData, LatLng(47.0, 8.0))
        }

        /**
         * Invoke the factory to create [MarkerOptions] for the given [data]. [Optionally][recentMarker], assume that
         * this is the recent marker. This function creates a state that contains only this [data]. Then it creates a
         * test [MarkerFactory] and invokes it.
         */
        private fun createOptionsForMarker(data: MarkerData, recentMarker: Boolean = false): MarkerOptions =
            callFactoryForMarker(data, createFactory(), recentMarker)

        /**
         * Invoke the given [factory] on a specific marker [data] object and return the result.
         * [Optionally][recentMarker], assume that this is the recent marker.
         */
        private fun callFactoryForMarker(
            data: MarkerData,
            factory: MarkerFactory,
            recentMarker: Boolean = false
        ): MarkerOptions = factory.createMarker(data, recentMarker)

        /**
         * Create a [MarkerFactory] with mock dependencies.
         */
        private fun createFactory(): MarkerFactory {
            val formatter = mockk<TimeDeltaFormatter>()
            val calculator = mockk<TimeDeltaAlphaCalculator>()
            val timeService = mockk<TimeService>()
            every { formatter.formatTimeDeltaOrTime(any(), any()) } returns TITLE
            every { calculator.calculateAlpha(any()) } returns ALPHA
            every { timeService.currentTime() } returns TimeData(CURRENT_TIME)

            return MarkerFactory(formatter, calculator, timeService)
        }
    }
}
