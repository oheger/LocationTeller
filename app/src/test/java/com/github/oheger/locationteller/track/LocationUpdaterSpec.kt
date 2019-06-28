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
package com.github.oheger.locationteller.track

import com.github.oheger.locationteller.server.LocationData
import com.github.oheger.locationteller.server.TimeData
import com.github.oheger.locationteller.server.TrackService
import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.SendChannel

/**
 * Test class for the actor that executes location updates.
 */
@ObsoleteCoroutinesApi
class LocationUpdaterSpec : StringSpec() {
    init {
        "LocationUpdaterActor should pass a location update to the track service" {
            val trackService = mockk<TrackService>()
            val locUpdate = locationUpdate(0)
            coEvery { trackService.addLocation(locUpdate.locationData) } returns true

            runActorTest(trackService, defaultConfig) { actor ->
                actor.send(locUpdate)
                val nextUpdate = locUpdate.nextTrackDelay.await()
                nextUpdate shouldBe defaultConfig.minTrackInterval
                coVerify { trackService.addLocation(locUpdate.locationData) }
            }
        }

        "LocationUpdaterActor should not process an update if there is no change in the location" {
            val trackService = mockk<TrackService>()
            val locUpdate1 = locationUpdate(0)
            val locUpdate2 = locationUpdate(locUpdate1.locationData.copy(time = TimeData(1)))
            coEvery { trackService.addLocation(any()) } returns true

            runActorTest(trackService, defaultConfig) { actor ->
                actor.send(locUpdate1)
                actor.send(locUpdate2)
                locUpdate2.nextTrackDelay.await()
                coVerify(exactly = 1) {
                    trackService.addLocation(any())
                }
            }
        }

        "LocationUpdaterActor should increase the update interval if the location is the same" {
            val trackService = mockk<TrackService>()
            val locUpdate1 = locationUpdate(0)
            val locUpdate2 = locationUpdate(locUpdate1.locationData)
            val locUpdate3 = locationUpdate(locUpdate1.locationData)
            coEvery { trackService.addLocation(any()) } returns true

            runActorTest(trackService, defaultConfig) { actor ->
                actor.send(locUpdate1)
                actor.send(locUpdate2)
                val nextUpdate1 = locUpdate2.nextTrackDelay.await()
                nextUpdate1 shouldBe defaultConfig.minTrackInterval + defaultConfig.intervalIncrementOnIdle
                actor.send(locUpdate3)
                val nextUpdate2 = locUpdate3.nextTrackDelay.await()
                nextUpdate2 shouldBe defaultConfig.minTrackInterval + 2 * defaultConfig.intervalIncrementOnIdle
            }
        }

        "LocationUpdaterActor should respect the maximum update interval" {
            val trackService = mockk<TrackService>()
            val config = defaultConfig.copy(intervalIncrementOnIdle = 200)
            val locUpdate1 = locationUpdate(0)
            val locUpdate2 = locationUpdate(locUpdate1.locationData)
            coEvery { trackService.addLocation(any()) } returns true

            runActorTest(trackService, config) { actor ->
                actor.send(locUpdate1)
                actor.send(locUpdate2)
                val nextUpdate = locUpdate2.nextTrackDelay.await()
                nextUpdate shouldBe defaultConfig.maxTrackInterval
            }
        }

        "LocationUpdaterActor should reset the update interval when another change is detected" {
            val trackService = mockk<TrackService>()
            val locUpdate1 = locationUpdate(0)
            val locUpdate2 = locationUpdate(locUpdate1.locationData)
            val locUpdate3 = locationUpdate(1)
            coEvery { trackService.addLocation(any()) } returns true

            runActorTest(trackService, defaultConfig) { actor ->
                actor.send(locUpdate1)
                actor.send(locUpdate2)
                actor.send(locUpdate3)
                val nextUpdate = locUpdate3.nextTrackDelay.await()
                nextUpdate shouldBe defaultConfig.minTrackInterval
            }
        }

        "LocationUpdaterActor should treat an unknown location data as error" {
            val trackService = mockk<TrackService>()
            val locUpdate = locationUpdate(unknownLocation)
            coEvery { trackService.addLocation(any()) } returns true

            runActorTest(trackService, defaultConfig) { actor ->
                actor.send(locationUpdate(2))
                actor.send(locUpdate)
                locUpdate.nextTrackDelay.await() shouldBe defaultConfig.minTrackInterval
            }
            coVerify(exactly = 0) { trackService.addLocation(unknownLocation) }
        }
    }

    companion object {
        /** A test configuration with default values.*/
        private val defaultConfig = TrackConfig(
            minTrackInterval = 60, maxTrackInterval = 200,
            intervalIncrementOnIdle = 30, locationValidity = 3600
        )

        /**
         * Creates a test _LocationData_ object based on the given index.
         * @param index the index
         * @return the test location data derived from this index
         */
        private fun locationData(index: Int): LocationData =
            LocationData(100.0 + index, 100.0 - index, TimeData(System.currentTimeMillis()))

        /**
         * Convenience function to create a _LocationUpdate_ object for the
         * given _LocationData_.
         * @param locData the _LocationData_
         * @return the update object for this data
         */
        private fun locationUpdate(locData: LocationData): LocationUpdate =
            LocationUpdate(locData, CompletableDeferred())

        /**
         * Convenience function to create _LocationUpdate_ object for a test
         * _LocationData_ which is derived from the given index.
         * @param index the index
         * @return the update object for this test _LocationData_
         */
        private fun locationUpdate(index: Int): LocationUpdate =
            locationUpdate(locationData(index))

        /**
         * Executes a test on an actor. The actor is obtained, and the given
         * test function is invoked with it. Finally, the actor is closed.
         * @param trackService the track service
         * @param trackConfig the track config
         * @param block the test function
         */
        private fun runActorTest(
            trackService: TrackService, trackConfig: TrackConfig,
            block: suspend (ch: SendChannel<LocationUpdate>) -> Unit
        ) = runBlocking {
            val actor = locationUpdaterActor(trackService, trackConfig, this)
            block(actor)
            actor.close()
        }
    }
}
