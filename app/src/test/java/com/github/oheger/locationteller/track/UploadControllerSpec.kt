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

/**
 * Test class for [[UploadController]].
 */
class UploadControllerSpec : StringSpec() {
    init {
        "UploadController should pass a location update to the track service" {
            val locUpdate = locationUpdate(0)
            val expRefTime =
                TimeData(locUpdate.locationData.time.currentTime - 1000 * defaultConfig.locationValidity)
            val helper = ControllerTestHelper()
            coEvery { helper.trackService.addLocation(locUpdate.locationData) } returns true
            coEvery { helper.trackService.removeOutdated(expRefTime) } returns true

            helper.checkUpload(locUpdate, defaultConfig.minTrackInterval)
                .verifyPrefHandler {
                    recordCheck(locUpdate.updateTime(), initialCheckCount + 1)
                    recordUpdate(locUpdate.updateTime(), initialUpdateCount + 1, 0, initialDistance)
                }
            coVerifyOrder {
                helper.trackService.removeOutdated(expRefTime)
                helper.trackService.addLocation(locUpdate.locationData)
            }
        }

        "UploadController should not process an update if there is no change in the location" {
            val locUpdate1 = locationUpdate(0)
            val loc2 = mockk<Location>()
            every { loc2.distanceTo(locUpdate1.orgLocation) } returns defaultConfig.locationUpdateThreshold - 0.1f
            val locUpdate2 = locationUpdate(
                locUpdate1.locationData.copy(time = TimeData(1)),
                orgLocation = loc2
            )
            val helper = ControllerTestHelper()
            coEvery { helper.trackService.removeOutdated(any()) } returns true
            coEvery { helper.trackService.addLocation(any()) } returns true

            helper.runUpload(locUpdate1)
                .runUpload(locUpdate2)
            coVerify(exactly = 1) {
                helper.trackService.addLocation(any())
            }
            helper.verifyPrefHandler {
                recordCheck(locUpdate2.updateTime(), initialCheckCount + 2)
            }.doWithPrefHandler { handler ->
                verify(exactly = 0) {
                    handler.recordUpdate(locUpdate2.locationData.time.currentTime, any(), any(), any())
                    handler.recordError(any(), any())
                }
            }
        }

        "UploadController should increase the update interval if the location is the same" {
            val locUpdate1 = locationUpdate(0)
            val locUpdate2 = locationUpdate(
                locationData(1),
                orgLocation = createLocation(1f)
            )
            val locUpdate3 = locationUpdate(
                locationData(2),
                orgLocation = createLocation(0f)
            )
            val helper = ControllerTestHelper()
            coEvery { helper.trackService.removeOutdated(any()) } returns true
            coEvery { helper.trackService.addLocation(any()) } returns true

            helper.runUpload(locUpdate1)
                .checkUpload(
                    locUpdate2,
                    defaultConfig.minTrackInterval + defaultConfig.intervalIncrementOnIdle
                )
                .checkUpload(
                    locUpdate3,
                    defaultConfig.minTrackInterval + 2 * defaultConfig.intervalIncrementOnIdle
                )
        }

        "UploadController should record the distance to the last location" {
            val distance = defaultConfig.locationUpdateThreshold + 27
            val locUpdate1 = locationUpdate(0)
            val locUpdate2 = locationUpdate(
                locationData(1),
                orgLocation = createLocation(1f)
            )
            val locUpdate3 = locationUpdate(
                locationData(2),
                orgLocation = createLocation(distance.toFloat())
            )
            val helper = ControllerTestHelper()
            coEvery { helper.trackService.removeOutdated(any()) } returns true
            coEvery { helper.trackService.addLocation(any()) } returns true

            helper.runUpload(locUpdate1)
                .runUpload(locUpdate2)
                .runUpload(locUpdate3)
                .verifyPrefHandler {
                    recordUpdate(
                        locUpdate3.updateTime(), initialUpdateCount + 2, distance,
                        initialDistance + distance
                    )
                }
        }

        "UploadController should increment the check and update count and the total distance" {
            val distance1 = defaultConfig.locationUpdateThreshold + 33
            val distance2 = defaultConfig.locationUpdateThreshold + 127
            val locUpdate1 = locationUpdate(0)
            val locUpdate2 = locationUpdate(
                locationData(1),
                orgLocation = createLocation(distance1.toFloat())
            )
            val locUpdate3 = locationUpdate(
                locationData(2),
                orgLocation = createLocation(distance2.toFloat())
            )
            val helper = ControllerTestHelper()
            coEvery { helper.trackService.removeOutdated(any()) } returns true
            coEvery { helper.trackService.addLocation(any()) } returns true

            helper.runUpload(locUpdate1)
                .runUpload(locUpdate2)
                .runUpload(locUpdate3)
                .verifyPrefHandler {
                    recordCheck(locUpdate1.updateTime(), initialCheckCount + 1)
                    recordCheck(locUpdate2.updateTime(), initialCheckCount + 2)
                    recordUpdate(
                        locUpdate2.updateTime(), initialUpdateCount + 2, distance1,
                        initialDistance + distance1
                    )
                    recordUpdate(
                        locUpdate3.updateTime(), initialUpdateCount + 3, distance2,
                        initialDistance + distance1 + distance2
                    )
                }
        }

        "UpdateController should respect the maximum update interval" {
            val config = defaultConfig.copy(intervalIncrementOnIdle = 200)
            val locUpdate1 = locationUpdate(0)
            val locUpdate2 = locationUpdate(
                locationData(1),
                orgLocation = createLocation(0f)
            )
            val helper = ControllerTestHelper(config)
            coEvery { helper.trackService.removeOutdated(any()) } returns true
            coEvery { helper.trackService.addLocation(any()) } returns true

            helper.runUpload(locUpdate1)
                .checkUpload(locUpdate2, defaultConfig.maxTrackInterval)
        }

        "UploadController should reset the update interval when another change is detected" {
            val locUpdate1 = locationUpdate(0)
            val locUpdate2 = locationUpdate(
                locationData(1),
                orgLocation = createLocation(0f)
            )
            val locUpdate3 = locationUpdate(1)
            val helper = ControllerTestHelper()
            coEvery { helper.trackService.removeOutdated(any()) } returns true
            coEvery { helper.trackService.addLocation(any()) } returns true

            helper.runUpload(locUpdate1)
                .runUpload(locUpdate2)
                .checkUpload(locUpdate3, defaultConfig.minTrackInterval)
        }

        "UploadController should record an error if an update fails" {
            val locUpdate = locationUpdate(locationData(1))
            val helper = ControllerTestHelper()
            coEvery { helper.trackService.removeOutdated(any()) } returns true
            coEvery { helper.trackService.addLocation(any()) } returns false

            helper.runUpload(locUpdate)
                .verifyPrefHandler {
                    recordError(locUpdate.updateTime(), initialErrorCount + 1)
                }
        }

        "UploadController should increment the error counter" {
            val locUpdate1 = locationUpdate(locationData(1))
            val locUpdate2 = locationUpdate(locationData(2))
            val helper = ControllerTestHelper()
            coEvery { helper.trackService.removeOutdated(any()) } returns true
            coEvery { helper.trackService.addLocation(any()) } returns false

            helper.runUpload(locUpdate1)
                .runUpload(locUpdate2)
                .verifyPrefHandler {
                    recordError(locUpdate1.updateTime(), initialErrorCount + 1)
                    recordError(locUpdate2.updateTime(), initialErrorCount + 2)
                }
        }

        "UploadController should treat an unknown location data as error" {
            val locUpdate = locationUpdate(locationData(1), orgLocation = null)
            val helper = ControllerTestHelper()
            coEvery { helper.trackService.removeOutdated(any()) } returns true
            coEvery { helper.trackService.addLocation(any()) } returns true

            helper.runUpload(locationUpdate(2))
                .checkUpload(locUpdate, defaultConfig.retryOnErrorTime)
                .verifyPrefHandler {
                    recordCheck(locUpdate.updateTime(), initialCheckCount + 2)
                    recordError(locUpdate.updateTime(), initialErrorCount + 1)
                }
                .doWithPrefHandler { handler ->
                    verify(exactly = 0) {
                        handler.recordUpdate(locUpdate.updateTime(), any(), any(), any())
                    }
                }
            coVerify(exactly = 0) { helper.trackService.addLocation(locUpdate.locationData) }
        }

        "UploadController should not store a null location as last location" {
            val locUpdate1 = locationUpdate(0)
            val locUpdate2 =
                locationUpdate(locData = locationData(2), orgLocation = null)
            val loc = mockk<Location>()
            every { loc.distanceTo(locUpdate1.orgLocation) } returns 1f
            val locUpdate3 = locationUpdate(locUpdate1.locationData, orgLocation = loc)
            val helper = ControllerTestHelper()
            coEvery { helper.trackService.removeOutdated(any()) } returns true
            coEvery { helper.trackService.addLocation(any()) } returns true

            helper.runUpload(locUpdate1)
                .runUpload(locUpdate2)
                .runUpload(locUpdate3)
            verify {
                loc.distanceTo(locUpdate1.orgLocation)
            }
        }

        "UploadController should respect the retry interval if there are errors" {
            val locUpdate1 = locationUpdate(1)
            val locUpdate2 = locationUpdate(2)
            val locUpdate3 = locationUpdate(3)
            val helper = ControllerTestHelper()
            coEvery { helper.trackService.removeOutdated(any()) } returns true
            coEvery { helper.trackService.addLocation(any()) } returns false

            helper.checkUpload(locUpdate1, defaultConfig.retryOnErrorTime)
                .checkUpload(locUpdate2, 2 * defaultConfig.retryOnErrorTime)
                .checkUpload(locUpdate3, 4 * defaultConfig.retryOnErrorTime)
        }

        "UploadController should increase the retry time only up to the maximum track interval" {
            val locUpdate1 = locationUpdate(1)
            val locUpdate2 = locationUpdate(2)
            val config = defaultConfig.copy(retryOnErrorTime = defaultConfig.maxTrackInterval - 1)
            val helper = ControllerTestHelper(config)
            coEvery { helper.trackService.removeOutdated(any()) } returns true
            coEvery { helper.trackService.addLocation(any()) } returns false

            helper.checkUpload(locUpdate1, config.retryOnErrorTime)
                .checkUpload(locUpdate2, defaultConfig.maxTrackInterval)
        }

        "UploadController should reset the retry time after a successful invocation" {
            val locUpdate1 = locationUpdate(1)
            val locUpdate2 = locationUpdate(2)
            val locUpdate3 = locationUpdate(3)
            val helper = ControllerTestHelper()
            coEvery { helper.trackService.removeOutdated(any()) } returns true
            coEvery { helper.trackService.addLocation(any()) } returnsMany listOf(false, true, false)

            helper.checkUpload(locUpdate1, defaultConfig.retryOnErrorTime)
                .checkUpload(locUpdate2, defaultConfig.minTrackInterval)
                .checkUpload(locUpdate3, defaultConfig.retryOnErrorTime)
        }
    }

    /**
     * A test helper class that manages a test controller and its dependencies.
     * @param trackConfig the tracking configuration to be used
     */
    private class ControllerTestHelper(trackConfig: TrackConfig = defaultConfig) {
        /** Mock for the track service. */
        val trackService = mockk<TrackService>()

        /** The mock for the preferences handler. */
        val prefHandler = createPrefHandler()

        /** The test controller. */
        private val controller = createUploadController(trackConfig)

        fun doWithPrefHandler(block: (PreferencesHandler) -> Unit): ControllerTestHelper {
            block(prefHandler)
            return this
        }

        /**
         * Performs verification on the managed preferences handler mock.
         * @param block the verification function
         * @return this test helper
         */
        fun verifyPrefHandler(block: PreferencesHandler.() -> Unit): ControllerTestHelper =
            doWithPrefHandler {
                verify {
                    it.block()
                }
            }

        /**
         * Invokes the test upload controller with the passed in data and
         * returns the delay for the next location check.
         * @param locData the location data
         * @param location the original location
         * @return the delay returned from the test controller
         */
        suspend fun triggerUpload(locData: LocationData, location: Location?): Int =
            controller.handleUpload(locData, location)

        /**
         * Invokes the test upload controller with the given data object and
         * returns the delay for the next location check.
         * @param locUpdate the location update data object
         * @return the delay returned from the test controller
         */
        suspend fun triggerUpload(locUpdate: LocationUpdate): Int =
            triggerUpload(locUpdate.locationData, locUpdate.orgLocation)

        /**
         * Invokes the test upload controller with the given test data object
         * and checks the delay that it returns.
         * @param locUpdate the location update data object
         * @param expDelay the expected delay
         * @return this test helper
         */
        suspend fun checkUpload(locUpdate: LocationUpdate, expDelay: Int): ControllerTestHelper {
            triggerUpload(locUpdate) shouldBe expDelay
            return this
        }

        /**
         * Invokes the test upload controller with the given test data object
         * and ignores the result returned by the upload function.
         * @param locUpdate the location update data object
         * @return this test helper
         */
        suspend fun runUpload(locUpdate: LocationUpdate): ControllerTestHelper {
            triggerUpload(locUpdate)
            return this
        }

        /**
         * Creates the test upload controller.
         * @param trackConfig the tracking configuration
         * @return the test upload controller
         */
        private fun createUploadController(trackConfig: TrackConfig): UploadController =
            UploadController(prefHandler, trackService, trackConfig, OfflineLocationStorage(10, 0))
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
    }
}