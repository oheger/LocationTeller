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
import com.github.oheger.locationteller.config.PreferencesHandler
import com.github.oheger.locationteller.config.TrackConfig
import com.github.oheger.locationteller.server.LocationData
import com.github.oheger.locationteller.server.TimeData
import com.github.oheger.locationteller.server.TimeService
import com.github.oheger.locationteller.server.TrackService
import com.github.oheger.locationteller.track.OfflineLocationStorage.Companion.MultiUploadProgress
import kotlin.math.min


/**
 * A class implementing the logic to upload location data to the tracking
 * server.
 *
 * An instance of this class is responsible for the interaction with a
 * [[TrackService]] object. It is invoked with [[LocationUpdate]] objects when
 * new location data becomes available. It then has to upload the new data,
 * check whether outdated data can be removed, handle possible errors, and
 * update tracking statistics. Depending on the outcome of the operation, the
 * delay until the next location check is determined.
 *
 * By making use of an [[OfflineLocationStorage]] object, a temporary loss of
 * the internet connection can be handled; data is buffered, and is uploaded
 * when the connection is available again.
 *
 * This class keeps a state and is therefore not thread-safe. It is used inside
 * an actor to make sure that no concurrency issues occur.
 *
 * @param prefHandler the object that manages access to preferences
 * @param trackService the _TrackService_ instance
 * @param trackConfig the current track configuration
 * @param offlineStorage the object for storing data that could not be uploaded
 * temporarily
 * @param timeService a service to obtain the current time
 */
class UploadController(
    val prefHandler: PreferencesHandler,
    val trackService: TrackService,
    val trackConfig: TrackConfig,
    val offlineStorage: OfflineLocationStorage,
    val timeService: TimeService
) {
    /**
     * Stores the last location that was reported. This is used to determine
     * the distance for the newest location.
     */
    private var lastLocation: Location? = null

    /**
     * The current update interval. This is used to determine the delay to the
     * next location check. (The interval is increased if no change of the
     * location is detected.)
     */
    private var updateInterval = trackConfig.minTrackInterval

    /** The current retry case in case of an error. */
    private var retryTime = trackConfig.retryOnErrorTime

    /** The statistic of the number of checks. */
    private var checkCount = prefHandler.checkCount()

    /** The statistic of the number of updates. */
    private var updateCount = prefHandler.updateCount()

    /** The statistic of the number of errors. */
    private var errorCount = prefHandler.errorCount()

    /** Accumulates the distance values over all location updates. */
    private var totalDistance = prefHandler.totalDistance()

    /**
     * Handles an upload request for the data passed in and returns a delay
     * when the next location check is to be executed.
     * @param locationData the data to be uploaded
     * @param orgLocation the original location the data was derived from
     * @return the delay (in seconds) until the next location check
     */
    suspend fun handleUpload(locationData: LocationData, orgLocation: Location?): Int {
        checkCount += 1
        val updateTime = locationData.time.currentTime
        prefHandler.recordCheck(updateTime, checkCount)
        val distance = locationChanged(orgLocation)
        if (distance >= 0) {
            val success = processUpload(locationData, orgLocation, distance)

            if (orgLocation != null) {
                lastLocation = orgLocation
            }
            calculateNextDelays(updateTime, success)
        } else {
            updateInterval = min(
                updateInterval + trackConfig.intervalIncrementOnIdle,
                trackConfig.maxTrackInterval
            )
        }
        return updateInterval
    }

    /**
     * Performs the upload of a new location data object and returns a flag
     * whether this was successful.
     * @param locationData the location data object
     * @param orgLocation the original location
     * @param distance the distance to the last location
     * @return *true* if the operation was successful, *false* otherwise
     */
    private suspend fun processUpload(
        locationData: LocationData,
        orgLocation: Location?,
        distance: Int
    ): Boolean = if (orgLocation != null) {
        updateCount += 1
        totalDistance += distance
        prefHandler.recordUpdate(locationData.time.currentTime, updateCount, distance, totalDistance)
        if (offlineStorage.canUploadDirectly(locationData)) {
            processSingleUpload(locationData)
        } else {
            processMultiUpload()
        }
    } else false

    /**
     * Uploads a single _LocationData_ object to the server. Returns a flag
     * whether this action was successful.
     * @param locationData the data to be uploaded
     * @return a flag whether the upload was successful
     */
    private suspend fun processSingleUpload(locationData: LocationData): Boolean {
        val outdatedRefTime = TimeData(
            locationData.time.currentTime - trackConfig.locationValidity * 1000
        )
        trackService.removeOutdated(outdatedRefTime)
        return if (trackService.addLocation(locationData)) {
            true
        } else {
            offlineStorage.storeFailedUpload(locationData)
            false
        }
    }

    /**
     * Executes a multi-upload operation. This function tries to upload data
     * that has been buffered locally, as long as more is available, uploads
     * are successful, and the configured maximum sync time is not exceeded.
     * @return a flag whether all uploads have been successful
     */
    private suspend fun processMultiUpload(): Boolean {
        val maxSyncTime = timeService.currentTime().currentTime + trackConfig.maxOfflineStorageSyncTime * 1000
        var uploadProgress: MultiUploadProgress
        do {
            val uploadChunk = offlineStorage.nextUploadChunk(trackConfig.multiUploadChunkSize)
            val uploadSuccess = trackService.addLocations(uploadChunk)
            uploadProgress = offlineStorage.handleMultiUploadResult(uploadChunk, uploadSuccess)
        } while (uploadProgress.canContinue && timeService.currentTime().currentTime < maxSyncTime)
        return !uploadProgress.isError
    }

    /**
     * Calculates the next update interval and the retry time based on the
     * passed in success flag. If an error was detected, the statistics are
     * updated accordingly.
     * @param updateTime the time of the update
     * @param success flag whether the upload was successful
     */
    private fun calculateNextDelays(updateTime: Long, success: Boolean) {
        if (!success) {
            errorCount += 1
            prefHandler.recordError(updateTime, errorCount)
            updateInterval = retryTime
            retryTime = min(retryTime * 2, trackConfig.maxTrackInterval)
        } else {
            updateInterval = trackConfig.minTrackInterval
            retryTime = trackConfig.retryOnErrorTime
        }
    }

    /**
     * Checks whether there is a change in location data. If so, returns
     * the distance to the last location; -1 means, there is no change.
     * @param currentLocation the current location
     * @return the distance to the previous location
     */
    private fun locationChanged(currentLocation: Location?): Int {
        if (lastLocation == null) {
            return 0
        }

        val distance = currentLocation?.distanceTo(lastLocation) ?: trackConfig.locationUpdateThreshold.toFloat()
        return if (distance >= trackConfig.locationUpdateThreshold) distance.toInt()
        else -1
    }
}