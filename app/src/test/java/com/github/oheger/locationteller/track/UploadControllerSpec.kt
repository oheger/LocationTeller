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
package com.github.oheger.locationteller.track

import android.location.Location
import com.github.oheger.locationteller.config.TrackConfig
import com.github.oheger.locationteller.server.LocationData
import com.github.oheger.locationteller.server.TimeData
import com.github.oheger.locationteller.server.TimeService
import com.github.oheger.locationteller.server.TrackService
import com.github.oheger.locationteller.track.OfflineLocationStorage.Companion.MultiUploadProgress
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
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
            every { loc2.distanceTo(locUpdate1.orgLocation!!) } returns defaultConfig.locationUpdateThreshold - 0.1f
            val locUpdate2 = locationUpdate(
                locUpdate1.locationData.copy(time = TimeData(1)),
                orgLocation = loc2
            )
            val helper = ControllerTestHelper()

            helper.prepareTrackService()
                .runUpload(locUpdate1)
                .runUpload(locUpdate2)
            coVerify(exactly = 1) {
                helper.trackService.addLocation(any())
            }
            helper.verifyPrefHandler {
                recordCheck(locUpdate2.updateTime(), initialCheckCount + 2)
            }.doWithTrackStorage { handler ->
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

            helper.prepareTrackService()
                .runUpload(locUpdate1)
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

            helper.prepareTrackService()
                .runUpload(locUpdate1)
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

            helper.prepareTrackService()
                .runUpload(locUpdate1)
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

            helper.prepareTrackService()
                .runUpload(locUpdate1)
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

            helper.prepareTrackService()
                .runUpload(locUpdate1)
                .runUpload(locUpdate2)
                .checkUpload(locUpdate3, defaultConfig.minTrackInterval)
        }

        "UploadController should record an error if an update fails" {
            val locUpdate = locationUpdate(locationData(1))
            val helper = ControllerTestHelper()

            helper.prepareTrackService(successUpload = false)
                .expectStorageOfFailedUpload(locUpdate.locationData)
                .runUpload(locUpdate)
                .verifyPrefHandler {
                    recordError(locUpdate.updateTime(), initialErrorCount + 1)
                }
                .verifyStorageOfFailedUpload(locUpdate.locationData)
        }

        "UploadController should increment the error counter" {
            val locUpdate1 = locationUpdate(locationData(1))
            val locUpdate2 = locationUpdate(locationData(2))
            val helper = ControllerTestHelper()

            helper.prepareTrackService(successUpload = false)
                .expectStorageOfFailedUpload(locUpdate1.locationData)
                .expectStorageOfFailedUpload(locUpdate2.locationData)
                .runUpload(locUpdate1)
                .runUpload(locUpdate2)
                .verifyPrefHandler {
                    recordError(locUpdate1.updateTime(), initialErrorCount + 1)
                    recordError(locUpdate2.updateTime(), initialErrorCount + 2)
                }
        }

        "UploadController should treat an unknown location data as error" {
            val locUpdate = locationUpdate(locationData(1), orgLocation = null)
            val helper = ControllerTestHelper()

            helper.prepareTrackService()
                .runUpload(locationUpdate(2))
                .checkUpload(locUpdate, defaultConfig.retryOnErrorTime)
                .verifyPrefHandler {
                    recordCheck(locUpdate.updateTime(), initialCheckCount + 2)
                    recordError(locUpdate.updateTime(), initialErrorCount + 1)
                }
                .doWithTrackStorage { handler ->
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
            every { loc.distanceTo(locUpdate1.orgLocation!!) } returns 1f
            val locUpdate3 = locationUpdate(locUpdate1.locationData, orgLocation = loc)
            val helper = ControllerTestHelper()

            helper.prepareTrackService()
                .runUpload(locUpdate1)
                .runUpload(locUpdate2)
                .runUpload(locUpdate3)
            verify {
                loc.distanceTo(locUpdate1.orgLocation!!)
            }
        }

        "UploadController should respect the retry interval if there are errors" {
            val locUpdate1 = locationUpdate(1)
            val locUpdate2 = locationUpdate(2)
            val locUpdate3 = locationUpdate(3)
            val helper = ControllerTestHelper()

            helper.prepareTrackService(successUpload = false)
                .expectStorageOfFailedUpload(locUpdate1.locationData)
                .expectStorageOfFailedUpload(locUpdate2.locationData)
                .expectStorageOfFailedUpload(locUpdate3.locationData)
                .checkUpload(locUpdate1, defaultConfig.retryOnErrorTime)
                .checkUpload(locUpdate2, 2 * defaultConfig.retryOnErrorTime)
                .checkUpload(locUpdate3, 4 * defaultConfig.retryOnErrorTime)
        }

        "UploadController should increase the retry time only up to the maximum track interval" {
            val locUpdate1 = locationUpdate(1)
            val locUpdate2 = locationUpdate(2)
            val config = defaultConfig.copy(retryOnErrorTime = defaultConfig.maxTrackInterval - 1)
            val helper = ControllerTestHelper(config)

            helper.prepareTrackService(successUpload = false)
                .expectStorageOfFailedUpload(locUpdate1.locationData)
                .expectStorageOfFailedUpload(locUpdate2.locationData)
                .checkUpload(locUpdate1, config.retryOnErrorTime)
                .checkUpload(locUpdate2, defaultConfig.maxTrackInterval)
        }

        "UploadController should reset the retry time after a successful invocation" {
            val locUpdate1 = locationUpdate(1)
            val locUpdate2 = locationUpdate(2)
            val locUpdate3 = locationUpdate(3)
            val helper = ControllerTestHelper()
            coEvery { helper.trackService.removeOutdated(any()) } returns true
            coEvery { helper.trackService.addLocation(any()) } returnsMany listOf(false, true, false)

            helper.expectStorageOfFailedUpload(locUpdate1.locationData)
                .expectStorageOfFailedUpload(locUpdate3.locationData)
                .checkUpload(locUpdate1, defaultConfig.retryOnErrorTime)
                .checkUpload(locUpdate2, defaultConfig.minTrackInterval)
                .checkUpload(locUpdate3, defaultConfig.retryOnErrorTime)
        }

        "UploadController should handle multi-uploads" {
            val locUpdate = locationUpdate(0)
            val chunk1 = listOf(locationData(1), locationData(2), locationData(3))
            val chunk2 = listOf(locationData(4), locationData(5))
            val helper = ControllerTestHelper()
            every { helper.offlineStorage.canUploadDirectly(locUpdate.locationData) } returns false
            every {
                helper.offlineStorage.nextUploadChunk(
                    defaultConfig.multiUploadChunkSize
                )
            } returnsMany listOf(chunk1, chunk2)
            every {
                helper.offlineStorage.handleMultiUploadResult(
                    chunk1,
                    chunk1.size
                )
            } returns MultiUploadProgress.PROGRESS
            every {
                helper.offlineStorage.handleMultiUploadResult(
                    chunk2,
                    1
                )
            } returns MultiUploadProgress.DONE
            coEvery { helper.trackService.addLocations(chunk1) } returns chunk1.size
            coEvery { helper.trackService.addLocations(chunk2) } returns 1

            helper.checkUpload(locUpdate, defaultConfig.minTrackInterval)
                .verifyPrefHandler {
                    recordUpdate(locUpdate.updateTime(), initialUpdateCount + 1, 0, initialDistance)
                }
            coVerify {
                helper.trackService.addLocations(chunk1)
                helper.trackService.addLocations(chunk2)
            }
        }

        "UploadController should respect the sync time limit for multi-uploads" {
            val locUpdate = locationUpdate(0)
            val chunk1 = listOf(locationData(1), locationData(2))
            val chunk2 = listOf(locationData(3), locationData(4))
            val helper = ControllerTestHelper()
            every { helper.offlineStorage.canUploadDirectly(locUpdate.locationData) } returns false
            every {
                helper.offlineStorage.nextUploadChunk(
                    defaultConfig.multiUploadChunkSize
                )
            } returnsMany listOf(chunk1, chunk2)
            every { helper.offlineStorage.handleMultiUploadResult(any(), any()) } returns MultiUploadProgress.PROGRESS
            coEvery { helper.trackService.addLocations(any()) } returns 2

            helper.timeTicks(
                defaultConfig.maxOfflineStorageSyncTime / 2,
                defaultConfig.maxOfflineStorageSyncTime
            )
                .checkUpload(locUpdate, defaultConfig.minTrackInterval)
            coVerify {
                helper.trackService.addLocations(chunk1)
                helper.trackService.addLocations(chunk2)
            }
        }

        "UploadController should handle errors during a multi-upload operation" {
            val locUpdate = locationUpdate(0)
            val chunk1 = listOf(locationData(1), locationData(2))
            val chunk2 = listOf(locationData(3))
            val helper = ControllerTestHelper()
            every { helper.offlineStorage.canUploadDirectly(locUpdate.locationData) } returns false
            every {
                helper.offlineStorage.nextUploadChunk(
                    defaultConfig.multiUploadChunkSize
                )
            } returnsMany listOf(chunk1, chunk2)
            every {
                helper.offlineStorage.handleMultiUploadResult(
                    chunk1,
                    chunk1.size
                )
            } returns MultiUploadProgress.PROGRESS
            every {
                helper.offlineStorage.handleMultiUploadResult(
                    chunk2,
                    0
                )
            } returns MultiUploadProgress.ERROR
            coEvery { helper.trackService.addLocations(chunk1) } returns chunk1.size
            coEvery { helper.trackService.addLocations(chunk2) } returns 0

            helper.checkUpload(locUpdate, defaultConfig.retryOnErrorTime)
                .verifyPrefHandler {
                    recordUpdate(locUpdate.updateTime(), initialUpdateCount + 1, 0, initialDistance)
                    recordError(locUpdate.updateTime(), initialErrorCount + 1)
                }
            coVerify {
                helper.trackService.addLocations(chunk1)
                helper.trackService.addLocations(chunk2)
            }
        }
    }

    /**
     * A test helper class that manages a test controller and its dependencies.
     * @param trackConfig the tracking configuration to be used
     */
    private class ControllerTestHelper(trackConfig: TrackConfig = defaultConfig) {
        /** Mock for the track service. */
        val trackService = mockk<TrackService>()

        /** The mock for the [TrackStorage]. */
        val trackStorage = createTrackStorage()

        /** The mock for the offline storage. */
        val offlineStorage = createOfflineStorage()

        /** The mock for the time service. */
        val timeService = createTimeService()

        /** The test controller. */
        private val controller = createUploadController(trackConfig)

        /**
         * Execute the given [block] passing in the mock [TrackStorage]. This can be used for instance to do custom
         * mock initializations or verifications.
         */
        fun doWithTrackStorage(block: (TrackStorage) -> Unit): ControllerTestHelper {
            block(trackStorage)
            return this
        }

        /**
         * Perform verification on the managed [TrackStorage] mock by invoking [block] inside a `verify` construct.
         */
        fun verifyPrefHandler(block: TrackStorage.() -> Unit): ControllerTestHelper =
            doWithTrackStorage {
                verify {
                    it.block()
                }
            }

        /**
         * Prepare the mock for the [TrackService] to expect and answer operations to remove outdated data and upload
         * new data. [successUpload] controls whether the upload operation is successful.
         */
        fun prepareTrackService(successUpload: Boolean = true): ControllerTestHelper {
            coEvery { trackService.removeOutdated(any()) } returns true
            coEvery { trackService.addLocation(any()) } returns successUpload
            return this
        }

        /**
         * Initialize the mock [TimeService] to return a sequence of time ticks. The first tick is the reference
         * time; then the provided [delta values][deltas] are interpreted as seconds relative to this reference time.
         */
        fun timeTicks(vararg deltas: Int): ControllerTestHelper {
            val nextTimes = deltas.map { TimeData(referenceTime + it * 1000) }
            val times = listOf(TimeData(referenceTime), *nextTimes.toTypedArray())
            every { timeService.currentTime() } returnsMany times
            return this
        }

        /**
         * Prepare the mock for the [OfflineLocationStorage] to expect an invocation due to a failed upload of [data].
         */
        fun expectStorageOfFailedUpload(data: LocationData): ControllerTestHelper {
            every { offlineStorage.storeFailedUpload(data) } just runs
            return this
        }

        /**
         * Verify that the mock for the [OfflineLocationStorage] has been invoked to record a failed upload of [data].
         */
        fun verifyStorageOfFailedUpload(data: LocationData): ControllerTestHelper {
            verify { offlineStorage.storeFailedUpload(data) }
            return this
        }

        /**
         * Invoke the test [UploadController] with the passed in [locData] and [location] and return the delay for the
         * next location check.
         */
        suspend fun triggerUpload(locData: LocationData, location: Location?): Int =
            controller.handleUpload(locData, location)

        /**
         * Invoke the test [UploadController] with the given [locUpdate] and return the delay for the next location
         * check.
         */
        suspend fun triggerUpload(locUpdate: LocationUpdate): Int =
            triggerUpload(locUpdate.locationData, locUpdate.orgLocation)

        /**
         * Invoke the test [UploadController] with the given test [locUpdate] object and check whether the delay that
         * it returns equals [expectedDelay].
         */
        suspend fun checkUpload(locUpdate: LocationUpdate, expectedDelay: Int): ControllerTestHelper {
            triggerUpload(locUpdate) shouldBe expectedDelay
            return this
        }

        /**
         * Invoke the test [UploadController] with the given [locUpdate] object and ignore the result returned by the
         * [triggerUpload] function.
         */
        suspend fun runUpload(locUpdate: LocationUpdate): ControllerTestHelper {
            triggerUpload(locUpdate)
            return this
        }

        /**
         * Create a mock for the [OfflineLocationStorage] and prepare it for the most frequent interactions.
         */
        private fun createOfflineStorage(): OfflineLocationStorage {
            val storage = mockk<OfflineLocationStorage>()
            every { storage.canUploadDirectly(any()) } returns true
            return storage
        }

        /**
         * Create a mock for the [TimeService] and prepare it to always return the reference time. This means that the
         * simulated time does not flow.
         */
        private fun createTimeService(): TimeService {
            val timeService = mockk<TimeService>()
            every { timeService.currentTime() } returns TimeData(referenceTime)
            return timeService
        }

        /**
         * Create the test [UploadController].
         */
        private fun createUploadController(trackConfig: TrackConfig): UploadController =
            UploadController(trackStorage, trackService, trackConfig, offlineStorage, timeService)
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

        /** The time returned per default by the mock time service. */
        private const val referenceTime = 20200217182414L

        /** A test configuration with default values.*/
        private val defaultConfig = TrackConfig(
            minTrackInterval = 60,
            maxTrackInterval = 200,
            intervalIncrementOnIdle = 30,
            locationValidity = 3600,
            locationUpdateThreshold = 56,
            gpsTimeout = 10,
            retryOnErrorTime = 4,
            autoResetStats = false,
            offlineStorageSize = 32,
            maxOfflineStorageSyncTime = 60,
            multiUploadChunkSize = 4,
            maxSpeedIncrease = 2.0,
            walkingSpeed = 1.0
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
         * Create a mock for a [TrackStorage]. The mock is prepared to return some initial statistics values.
         */
        private fun createTrackStorage(): TrackStorage {
            val storage = mockk<TrackStorage>(relaxed = true)
            every { storage.checkCount() } returns initialCheckCount
            every { storage.updateCount() } returns initialUpdateCount
            every { storage.errorCount() } returns initialErrorCount
            every { storage.totalDistance() } returns initialDistance
            return storage
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