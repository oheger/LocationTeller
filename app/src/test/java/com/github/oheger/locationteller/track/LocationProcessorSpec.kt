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
package com.github.oheger.locationteller.track

import android.location.Location
import com.github.oheger.locationteller.ResetDispatcherListener
import com.github.oheger.locationteller.server.TimeData
import com.github.oheger.locationteller.server.TimeService
import com.google.android.gms.location.LocationResult
import io.kotest.core.listeners.TestListener
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.shouldBeExactly
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.channels.SendChannel

/**
 * Test class for [LocationProcessor].
 */
@ObsoleteCoroutinesApi
@ExperimentalCoroutinesApi
class LocationProcessorSpec : StringSpec() {
    override fun listeners(): List<TestListener> = listOf(ResetDispatcherListener)

    /**
     * Creates a mock for an updater actor that is prepared to expect a message
     * indicating an error.
     * @return the mock actor
     */
    private fun createMockActorExpectingError(): SendChannel<LocationUpdate> {
        val actor = mockk<SendChannel<LocationUpdate>>()
        coEvery { actor.send(any()) } answers {
            val locUpdate = arg<LocationUpdate>(0)
            locUpdate.orgLocation shouldBe null
            locUpdate.updateTime() shouldBe errorTime.currentTime
            locUpdate.nextTrackDelay.complete(nextUpdate)
        }
        return actor
    }

    init {
        "LocationProcessor should pass a location data to the actor" {
            val latitude = 123.456
            val longitude = 654.789
            val currentTime = TimeData(20190629180617L)
            val actor = mockk<SendChannel<LocationUpdate>>()
            val timeService = mockk<TimeService>()
            val location = mockk<Location>()
            val locResult = mockk<LocationResult>()
            val locRetriever = mockk<LocationRetriever>()
            every { location.latitude } returns latitude
            every { location.longitude } returns longitude
            every { locResult.lastLocation } returns location
            coEvery { locRetriever.fetchLocation() } returns location
            every { timeService.currentTime() } returns currentTime
            coEvery { actor.send(any()) } answers {
                val locUpdate = arg<LocationUpdate>(0)
                locUpdate.locationData.latitude shouldBeExactly latitude
                locUpdate.locationData.longitude shouldBeExactly longitude
                locUpdate.locationData.time shouldBe currentTime
                locUpdate.orgLocation shouldBe location
                locUpdate.nextTrackDelay.complete(nextUpdate)
            }
            val processor = LocationProcessor(locRetriever, actor, timeService)

            processor.retrieveAndUpdateLocation() shouldBe nextUpdate
        }

        "LocationProcessor should handle a failure when retrieving the location" {
            val actor = createMockActorExpectingError()
            val locRetriever = mockk<LocationRetriever>()
            coEvery { locRetriever.fetchLocation() } returns null
            val processor = LocationProcessor(locRetriever, actor, errorTimeService())

            processor.retrieveAndUpdateLocation() shouldBe nextUpdate
            coVerify { actor.send(any()) }
        }
    }

    companion object {
        /** Constant for the next update time to be returned by the mock actor.*/
        private const val nextUpdate = 42

        /** Time constant that is set for invalid location updates. */
        private val errorTime = TimeData(20200205215808L)

        /**
         * Returns a mock time service that is configured to return the special
         * error time constant.
         * @return the mock time service
         */
        private fun errorTimeService(): TimeService {
            val timeService = mockk<TimeService>()
            every { timeService.currentTime() } returns errorTime
            return timeService
        }
    }
}
