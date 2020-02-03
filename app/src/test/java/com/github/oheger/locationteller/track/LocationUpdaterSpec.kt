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
    private fun createTrackService(): TrackService = mockk()

    init {
        "LocationUpdaterActor should pass a location update to the track service" {
            val trackService = createTrackService()
            val prefHandler = createPrefHandler()
            val locUpdate = locationUpdate(0)
            val expRefTime =
                TimeData(locUpdate.locationData.time.currentTime - 1000 * defaultConfig.locationValidity)
            coEvery { trackService.addLocation(locUpdate.locationData) } returns true
            coEvery { trackService.removeOutdated(expRefTime) } returns true

            runActorTest(prefHandler, trackService, defaultConfig) { actor ->
                actor.send(locUpdate)
                val nextUpdate = locUpdate.nextTrackDelay.await()
                nextUpdate shouldBe defaultConfig.minTrackInterval
                coVerifyOrder {
                    trackService.removeOutdated(expRefTime)
                    trackService.addLocation(locUpdate.locationData)
                }
                verify {
                    prefHandler.recordCheck(locUpdate.locationData.time.currentTime, initialCheckCount + 1)
                    prefHandler.recordUpdate(locUpdate.locationData.time.currentTime, initialUpdateCount + 1,
                        0, initialDistance)
                }
            }
        }

        "LocationUpdaterActor should not process an update if there is no change in the location" {
            val trackService = createTrackService()
            val prefHandler = createPrefHandler()
            val locUpdate1 = locationUpdate(0)
            val loc2 = mockk<Location>()
            every { loc2.distanceTo(locUpdate1.orgLocation) } returns defaultConfig.locationUpdateThreshold - 0.1f
            val locUpdate2 = locationUpdate(
                locUpdate1.locationData.copy(time = TimeData(1)),
                orgLocation = loc2
            )
            coEvery { trackService.removeOutdated(any()) } returns true
            coEvery { trackService.addLocation(any()) } returns true

            runActorTest(prefHandler, trackService, defaultConfig) { actor ->
                actor.send(locUpdate1)
                actor.send(locUpdate2)
                locUpdate2.nextTrackDelay.await()
                coVerify(exactly = 1) {
                    trackService.addLocation(any())
                }
                verify { prefHandler.recordCheck(locUpdate2.locationData.time.currentTime,
                    initialCheckCount + 2) }
                verify(exactly = 0) {
                    prefHandler.recordUpdate(locUpdate2.locationData.time.currentTime, any(), any(), any())
                    prefHandler.recordError(any(), any())
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

            runActorTest(createPrefHandler(), trackService, defaultConfig) { actor ->
                actor.send(locUpdate1)
                actor.send(locUpdate2)
                val nextUpdate1 = locUpdate2.nextTrackDelay.await()
                nextUpdate1 shouldBe defaultConfig.minTrackInterval + defaultConfig.intervalIncrementOnIdle
                actor.send(locUpdate3)
                val nextUpdate2 = locUpdate3.nextTrackDelay.await()
                nextUpdate2 shouldBe defaultConfig.minTrackInterval + 2 * defaultConfig.intervalIncrementOnIdle
            }
        }

        "LocationUpdaterActor should record the distance to the last location" {
            val distance = defaultConfig.locationUpdateThreshold + 27
            val prefHandler = createPrefHandler()
            val trackService = createTrackService()
            val locUpdate1 = locationUpdate(0)
            val locUpdate2 = locationUpdate(locationData(1), orgLocation = createLocation(1f))
            val locUpdate3 = locationUpdate(locationData(2), orgLocation = createLocation(distance.toFloat()))
            coEvery { trackService.removeOutdated(any()) } returns true
            coEvery { trackService.addLocation(any()) } returns true

            runActorTest(prefHandler, trackService, defaultConfig) { actor ->
                actor.send(locUpdate1)
                actor.send(locUpdate2)
                actor.send(locUpdate3)
                verify {
                    prefHandler.recordUpdate(locUpdate3.locationData.time.currentTime, initialUpdateCount + 2,
                        distance, initialDistance + distance)
                }
            }
        }

        "LocationUpdateActor should increment the check and update count and the total distance" {
            val distance1 = defaultConfig.locationUpdateThreshold + 33
            val distance2 = defaultConfig.locationUpdateThreshold + 127
            val trackService = createTrackService()
            val prefHandler = createPrefHandler()
            val locUpdate1 = locationUpdate(0)
            val locUpdate2 = locationUpdate(locationData(1), orgLocation = createLocation(distance1.toFloat()))
            val locUpdate3 = locationUpdate(locationData(2), orgLocation = createLocation(distance2.toFloat()))
            coEvery { trackService.removeOutdated(any()) } returns true
            coEvery { trackService.addLocation(any()) } returns true

            runActorTest(prefHandler, trackService, defaultConfig) { actor ->
                actor.send(locUpdate1)
                actor.send(locUpdate2)
                actor.send(locUpdate3)
                verify {
                    prefHandler.recordCheck(locUpdate1.locationData.time.currentTime, initialCheckCount + 1)
                    prefHandler.recordCheck(locUpdate2.locationData.time.currentTime, initialCheckCount + 2)
                    prefHandler.recordUpdate(locUpdate2.locationData.time.currentTime, initialUpdateCount + 2,
                        distance1, initialDistance + distance1)
                    prefHandler.recordUpdate(locUpdate3.locationData.time.currentTime, initialUpdateCount + 3,
                        distance2, initialDistance + distance1 + distance2)
                }
            }
        }

        "LocationUpdaterActor should respect the maximum update interval" {
            val trackService = createTrackService()
            val config = defaultConfig.copy(intervalIncrementOnIdle = 200)
            val locUpdate1 = locationUpdate(0)
            val locUpdate2 = locationUpdate(locationData(1), orgLocation = createLocation(0f))
            coEvery { trackService.removeOutdated(any()) } returns true
            coEvery { trackService.addLocation(any()) } returns true

            runActorTest(createPrefHandler(), trackService, config) { actor ->
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

            runActorTest(createPrefHandler(), trackService, defaultConfig) { actor ->
                actor.send(locUpdate1)
                actor.send(locUpdate2)
                actor.send(locUpdate3)
                val nextUpdate = locUpdate3.nextTrackDelay.await()
                nextUpdate shouldBe defaultConfig.minTrackInterval
            }
        }

        "LocationUpdaterActor should record an error if an update fails" {
            val trackService = createTrackService()
            val prefHandler = createPrefHandler()
            val locUpdate = locationUpdate(locationData(1))
            coEvery { trackService.removeOutdated(any()) } returns true
            coEvery { trackService.addLocation(any()) } returns false
            every { prefHandler.recordError(locUpdate.locationData.time.currentTime,
                initialErrorCount + 1) } just runs
            every { prefHandler.recordCheck(locUpdate.locationData.time.currentTime,
                initialCheckCount + 1) } just runs

            runActorTest(prefHandler, trackService, defaultConfig) { actor ->
                actor.send(locUpdate)
            }
            verify {
                prefHandler.recordError(locUpdate.locationData.time.currentTime, initialErrorCount + 1)
            }
        }

        "LocationUpdaterActor should increment the error counter" {
            val trackService = createTrackService()
            val prefHandler = createPrefHandler()
            val locUpdate1 = locationUpdate(locationData(1))
            val locUpdate2 = locationUpdate(locationData(2))
            coEvery { trackService.removeOutdated(any()) } returns true
            coEvery { trackService.addLocation(any()) } returns false

            runActorTest(prefHandler, trackService, defaultConfig) { actor ->
                actor.send(locUpdate1)
                actor.send(locUpdate2)
            }
            verify {
                prefHandler.recordError(locUpdate1.locationData.time.currentTime, initialErrorCount + 1)
                prefHandler.recordError(locUpdate2.locationData.time.currentTime, initialErrorCount + 2)
            }
        }

        "LocationUpdaterActor should treat an unknown location data as error" {
            val trackService = createTrackService()
            val prefHandler = createPrefHandler()
            val locUpdate = locationUpdate(unknownLocation, orgLocation = null)
            coEvery { trackService.removeOutdated(any()) } returns true
            coEvery { trackService.addLocation(any()) } returns true
            every { prefHandler.recordCheck(any(), any()) } just runs
            every { prefHandler.recordError(locUpdate.locationData.time.currentTime,
                initialErrorCount + 1) } just runs

            runActorTest(prefHandler, trackService, defaultConfig) { actor ->
                actor.send(locationUpdate(2))
                actor.send(locUpdate)
                locUpdate.nextTrackDelay.await() shouldBe defaultConfig.retryOnErrorTime
            }
            coVerify(exactly = 0) { trackService.addLocation(locUpdate.locationData) }
            verify(exactly = 0) { prefHandler.recordUpdate(locUpdate.locationData.time.currentTime,
                any(), any(), any()) }
            verify { prefHandler.recordCheck(locUpdate.locationData.time.currentTime,
                initialCheckCount + 2) }
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

            runActorTest(createPrefHandler(), trackService, defaultConfig) { actor ->
                actor.send(locUpdate1)
                actor.send(locUpdate2)
                actor.send(locUpdate3)
            }
            verify {
                loc.distanceTo(locUpdate1.orgLocation)
            }
        }

        "LocationUpdaterActor should respect the retry interval if there are errors" {
            val trackService = createTrackService()
            val locUpdate1 = locationUpdate(1)
            val locUpdate2 = locationUpdate(2)
            val locUpdate3 = locationUpdate(3)
            coEvery { trackService.removeOutdated(any()) } returns true
            coEvery { trackService.addLocation(any()) } returns false

            runActorTest(createPrefHandler(), trackService, defaultConfig) { actor ->
                actor.send(locUpdate1)
                locUpdate1.nextTrackDelay.await() shouldBe defaultConfig.retryOnErrorTime
                actor.send(locUpdate2)
                locUpdate2.nextTrackDelay.await() shouldBe 2 * defaultConfig.retryOnErrorTime
                actor.send(locUpdate3)
                locUpdate3.nextTrackDelay.await() shouldBe 4 * defaultConfig.retryOnErrorTime
            }
        }

        "LocationUpdaterActor should increase the retry time only up to the maximum track interval" {
            val trackService = createTrackService()
            val locUpdate1 = locationUpdate(1)
            val locUpdate2 = locationUpdate(2)
            coEvery { trackService.removeOutdated(any()) } returns true
            coEvery { trackService.addLocation(any()) } returns false
            val config = defaultConfig.copy(retryOnErrorTime = defaultConfig.maxTrackInterval - 1)

            runActorTest(createPrefHandler(), trackService, config) { actor ->
                actor.send(locUpdate1)
                locUpdate1.nextTrackDelay.await() shouldBe config.retryOnErrorTime
                actor.send(locUpdate2)
                locUpdate2.nextTrackDelay.await() shouldBe defaultConfig.maxTrackInterval
            }
        }

        "LocationUpdaterActor should reset the retry time after a successful invocation" {
            val trackService = createTrackService()
            val locUpdate1 = locationUpdate(1)
            val locUpdate2 = locationUpdate(2)
            val locUpdate3 = locationUpdate(3)
            coEvery { trackService.removeOutdated(any()) } returns true
            coEvery { trackService.addLocation(any()) } returnsMany listOf(false, true, false)

            runActorTest(createPrefHandler(), trackService, defaultConfig) { actor ->
                actor.send(locUpdate1)
                locUpdate1.nextTrackDelay.await() shouldBe defaultConfig.retryOnErrorTime
                actor.send(locUpdate2)
                locUpdate2.nextTrackDelay.await() shouldBe defaultConfig.minTrackInterval
                actor.send(locUpdate3)
                locUpdate3.nextTrackDelay.await() shouldBe defaultConfig.retryOnErrorTime
            }
        }
    }

    companion object {
        /** The checks already performed since tracking start. */
        private const val initialCheckCount = 111

        /** The updates already performed since tracking start. */
        private const val initialUpdateCount = 42

        /** The number of errors since tracking start. */
        private const val initialErrorCount = 8

        /** The total distance recorded since tracking start. */
        private const val initialDistance = 4711L

        /** A test configuration with default values.*/
        private val defaultConfig = TrackConfig(
            minTrackInterval = 60, maxTrackInterval = 200,
            intervalIncrementOnIdle = 30, locationValidity = 3600,
            locationUpdateThreshold = 56, gpsTimeout = 10, retryOnErrorTime = 4,
            autoResetStats = false
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
         * @param orgLocation the original location object
         * @return the update object for this data
         */
        private fun locationUpdate(
            locData: LocationData,
            orgLocation: Location? = createLocation()
        ): LocationUpdate =
            LocationUpdate(locData, orgLocation, CompletableDeferred())

        /**
         * Convenience function to create _LocationUpdate_ object for a test
         * _LocationData_ which is derived from the given index.
         * @param index the index
         * @return the update object for this test _LocationData_
         */
        private fun locationUpdate(index: Int): LocationUpdate =
            locationUpdate(locationData(index))

        /**
         * Creates a mock for a _PreferencesHandler_. The mock is prepared to
         * return some initial statistics values.
         * @return the mock handler
         */
        private fun createPrefHandler(): PreferencesHandler {
            val handler = mockk<PreferencesHandler>(relaxed = true)
            every { handler.checkCount() } returns initialCheckCount
            every { handler.updateCount() } returns initialUpdateCount
            every { handler.errorCount() } returns initialErrorCount
            every { handler.totalDistance() } returns initialDistance
            return handler
        }

        /**
         * Creates a mock for a _Location_ object. The mock is prepared to
         * calculate the distance to another location.
         * @param distance the distance to be returned
         * @return the mock location
         */
        private fun createLocation(distance: Float = defaultConfig.locationUpdateThreshold.toFloat()): Location {
            val location = mockk<Location>()
            every { location.distanceTo(any()) } returns distance
            return location
        }

        /**
         * Executes a test on an actor. The actor is obtained, and the given
         * test function is invoked with it. Finally, the actor is closed.
         * @param prefHandler the preferences handler
         * @param trackService the track service
         * @param trackConfig the track config
         * @param block the test function
         */
        private fun runActorTest(
            prefHandler: PreferencesHandler, trackService: TrackService, trackConfig: TrackConfig,
            block: suspend (ch: SendChannel<LocationUpdate>) -> Unit
        ) = runBlocking {
            val actor = locationUpdaterActor(prefHandler, trackService, trackConfig, this)
            block(actor)
            actor.close()
        }
    }
}
