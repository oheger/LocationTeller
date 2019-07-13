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

import android.location.Location
import com.github.oheger.locationteller.server.LocationData
import com.github.oheger.locationteller.server.TimeData
import com.github.oheger.locationteller.server.TrackService
import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import io.mockk.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.runBlocking

/**
 * Test class for the actor that executes location updates.
 */
@ObsoleteCoroutinesApi
class LocationUpdaterSpec : StringSpec() {
    /**
     * Creates a mock track service that is prepared to expect the default
     * invocations.
     * @return the mock track service
     */
    private fun createTrackService(): TrackService {
        val service = mockk<TrackService>()
        every { service.resetClient() } just runs
        return service
    }

    init {
        "LocationUpdaterActor should pass a location update to the track service" {
            val trackService = createTrackService()
            val locUpdate = locationUpdate(0)
            val expRefTime =
                TimeData(locUpdate.locationData.time.currentTime - 1000 * defaultConfig.locationValidity)
            coEvery { trackService.addLocation(locUpdate.locationData) } returns true
            coEvery { trackService.removeOutdated(expRefTime) } returns true

            runActorTest(trackService, defaultConfig) { actor ->
                actor.send(locUpdate)
                val nextUpdate = locUpdate.nextTrackDelay.await()
                nextUpdate shouldBe defaultConfig.minTrackInterval
                coVerifyOrder {
                    trackService.removeOutdated(expRefTime)
                    trackService.addLocation(locUpdate.locationData)
                }
                verify {
                    trackService.resetClient()
                    locUpdate.prefHandler.recordCheck(locUpdate.locationData.time.currentTime)
                    locUpdate.prefHandler.recordUpdate(locUpdate.locationData.time.currentTime)
                }
            }
        }

        "LocationUpdaterActor should not process an update if there is no change in the location" {
            val trackService = createTrackService()
            val locUpdate1 = locationUpdate(0)
            val loc2 = mockk<Location>()
            every { loc2.distanceTo(locUpdate1.orgLocation) } returns MinimumLocationDelta - 0.1f
            val locUpdate2 = locationUpdate(
                locUpdate1.locationData.copy(time = TimeData(1)),
                orgLocation = loc2
            )
            coEvery { trackService.removeOutdated(any()) } returns true
            coEvery { trackService.addLocation(any()) } returns true

            runActorTest(trackService, defaultConfig) { actor ->
                actor.send(locUpdate1)
                actor.send(locUpdate2)
                locUpdate2.nextTrackDelay.await()
                coVerify(exactly = 1) {
                    trackService.addLocation(any())
                    trackService.resetClient()
                }
                verify { locUpdate2.prefHandler.recordCheck(locUpdate2.locationData.time.currentTime) }
                verify(exactly = 0) {
                    locUpdate2.prefHandler.recordUpdate(any())
                    locUpdate2.prefHandler.recordError(any())
                }
            }
        }

        "LocationUpdaterActor should increase the update interval if the location is the same" {
            val trackService = createTrackService()
            val locUpdate1 = locationUpdate(0)
            val locUpdate2 = locationUpdate(locationData(1), orgLocation = createLocation(1f))
            val locUpdate3 = locationUpdate(locationData(2), orgLocation = createLocation(0f))
            coEvery { trackService.removeOutdated(any()) } returns true
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
            val trackService = createTrackService()
            val config = defaultConfig.copy(intervalIncrementOnIdle = 200)
            val locUpdate1 = locationUpdate(0)
            val locUpdate2 = locationUpdate(locationData(1), orgLocation = createLocation(0f))
            coEvery { trackService.removeOutdated(any()) } returns true
            coEvery { trackService.addLocation(any()) } returns true

            runActorTest(trackService, config) { actor ->
                actor.send(locUpdate1)
                actor.send(locUpdate2)
                val nextUpdate = locUpdate2.nextTrackDelay.await()
                nextUpdate shouldBe defaultConfig.maxTrackInterval
            }
        }

        "LocationUpdaterActor should reset the update interval when another change is detected" {
            val trackService = createTrackService()
            val locUpdate1 = locationUpdate(0)
            val locUpdate2 = locationUpdate(locationData(1), orgLocation = createLocation(0f))
            val locUpdate3 = locationUpdate(1)
            coEvery { trackService.removeOutdated(any()) } returns true
            coEvery { trackService.addLocation(any()) } returns true

            runActorTest(trackService, defaultConfig) { actor ->
                actor.send(locUpdate1)
                actor.send(locUpdate2)
                actor.send(locUpdate3)
                val nextUpdate = locUpdate3.nextTrackDelay.await()
                nextUpdate shouldBe defaultConfig.minTrackInterval
            }
        }

        "LocationUpdaterActor should record an error if an update fails" {
            val trackService = createTrackService()
            val prefHandler = mockk<PreferencesHandler>()
            val locUpdate = locationUpdate(locationData(1), prefHandler)
            coEvery { trackService.removeOutdated(any()) } returns true
            coEvery { trackService.addLocation(any()) } returns false
            every { prefHandler.recordError(locUpdate.locationData.time.currentTime) } just runs
            every { prefHandler.recordCheck(locUpdate.locationData.time.currentTime) } just runs

            runActorTest(trackService, defaultConfig) { actor ->
                actor.send(locUpdate)
            }
            verify {
                prefHandler.recordError(locUpdate.locationData.time.currentTime)
            }
        }

        "LocationUpdaterActor should treat an unknown location data as error" {
            val trackService = createTrackService()
            val prefHandler = mockk<PreferencesHandler>()
            val locUpdate = locationUpdate(unknownLocation, prefHandler, orgLocation = null)
            coEvery { trackService.removeOutdated(any()) } returns true
            coEvery { trackService.addLocation(any()) } returns true
            every { prefHandler.recordCheck(any()) } just runs
            every { prefHandler.recordError(locUpdate.locationData.time.currentTime) } just runs

            runActorTest(trackService, defaultConfig) { actor ->
                actor.send(locationUpdate(2))
                actor.send(locUpdate)
                locUpdate.nextTrackDelay.await() shouldBe defaultConfig.minTrackInterval
            }
            coVerify(exactly = 0) { trackService.addLocation(unknownLocation) }
            verify(exactly = 0) { prefHandler.recordUpdate(any()) }
            verify { locUpdate.prefHandler.recordCheck(locUpdate.locationData.time.currentTime) }
        }

        "LocationUpdaterActor should not store a null location as last location" {
            val trackService = createTrackService()
            val locUpdate1 = locationUpdate(0)
            val locUpdate2 = locationUpdate(locData = unknownLocation, orgLocation = null)
            val loc = mockk<Location>()
            every { loc.distanceTo(locUpdate1.orgLocation) } returns 1f
            val locUpdate3 = locationUpdate(locUpdate1.locationData, orgLocation = loc)
            coEvery { trackService.removeOutdated(any()) } returns true
            coEvery { trackService.addLocation(any()) } returns true

            runActorTest(trackService, defaultConfig) { actor ->
                actor.send(locUpdate1)
                actor.send(locUpdate2)
                actor.send(locUpdate3)
            }
            verify {
                loc.distanceTo(locUpdate1.orgLocation)
            }
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
        private fun locationUpdate(
            locData: LocationData,
            prefHandler: PreferencesHandler = createPrefHandler(),
            orgLocation: Location? = createLocation()
        ): LocationUpdate =
            LocationUpdate(locData, orgLocation, CompletableDeferred(), prefHandler)

        /**
         * Convenience function to create _LocationUpdate_ object for a test
         * _LocationData_ which is derived from the given index.
         * @param index the index
         * @return the update object for this test _LocationData_
         */
        private fun locationUpdate(index: Int): LocationUpdate =
            locationUpdate(locationData(index))

        /**
         * Creates a mock for a _PreferencesHandler_.
         * @return the mock handler
         */
        private fun createPrefHandler(): PreferencesHandler = mockk(relaxed = true)

        /**
         * Creates a mock for a _Location_ object. The mock is prepared to
         * calculate the distance to another location.
         * @param distance the distance to be returned
         * @return the mock location
         */
        private fun createLocation(distance: Float = MinimumLocationDelta): Location {
            val location = mockk<Location>()
            every { location.distanceTo(any()) } returns distance
            return location
        }

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
