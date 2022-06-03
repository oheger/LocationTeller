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

import com.github.oheger.locationteller.server.LocationData
import java.util.*
import kotlin.collections.ArrayList

/**
 * A class allowing to store location data offline if no internet connection
 * for the upload is available.
 *
 * This class plays a central role in the handling of a broken internet
 * connection. The basic idea is as follows:
 *
 * If the upload of a location data file to the server fails, the data is
 * passed to an instance of this class. The class stores a configurable number
 * of records, discarding old ones when the capacity limit is reached. During a
 * location check, it is tested whether the storage contains any data. If it is
 * empty, it is assumed that the internet connection is working, and the new
 * location data is uploaded directly. If there is data in the store, however,
 * a chunk of data (starting from the most recent ones) is tried to be
 * uploaded. This is continued with further chunks until an error occurs or a
 * configurable time limit is reached. The data remaining will then be
 * processed during the next check.
 *
 * This class provides an API to manage the data that could not be uploaded
 * directly. It takes care that the newest records available take part in
 * upload operations. It also implements some filtering on the data it needs to
 * store: If an error during upload is detected, another check is scheduled
 * after a (typically shorter) retry interval. So more location data would be
 * recorded. Here the class makes sure that only a single data item (always the
 * newest one) is kept in the configured minimum tracking interval.
 *
 * Implementation note: This class is not thread-safe. It is expected to be
 * used inside an actor or a corresponding synchronized structure.
 *
 * @param capacity the capacity (the max number of items to store)
 * @param minTrackInterval the minimum track interval (in milliseconds)
 */
class OfflineLocationStorage(
    val capacity: Int,
    val minTrackInterval: Long
) {
    /** Stores the data items in descending order by their creation time. */
    private val storage = LinkedList<LocationData>()

    /**
     * Returns a flag whether the given location data object can be uploaded
     * directly to the server. This is *true* if the storage does not contain
     * any data, so no buffering is required. Otherwise, the data is processed,
     * and the storage is updated accordingly.
     * @param locationData the current _LocationData_
     * @return a flag whether a direct upload is possible
     */
    fun canUploadDirectly(locationData: LocationData): Boolean =
        if (storage.isEmpty()) true
        else {
            addToStorage(locationData)
            false
        }

    /**
     * Passes a _LocationData_ object to this storage that could not be
     * uploaded. It is added to this storage; this may cause some other data to
     * be removed.
     * @param locationData the data object that could not be uploaded
     */
    fun storeFailedUpload(locationData: LocationData) {
        addToStorage(locationData)
    }

    /**
     * Requests a chunk of data to be uploaded. This function is called if no
     * direct upload is possible (because uploads had failed in the past). It
     * returns an ordered list with the most recent _LocationData_ objects
     * available (the first item in the list is the newest one) that has at
     * most the given chunk size. The elements in the list are also removed
     * from the storage.
     * @param chunkSize a hint for the chunk size
     * @return the next chunk of data to be uploaded
     */
    fun nextUploadChunk(chunkSize: Int): List<LocationData> {
        val result = ArrayList<LocationData>(chunkSize)
        while (!storage.isEmpty() && result.size < chunkSize) {
            result += storage.removeFirst()
        }
        return result
    }

    /**
     * Handles the result of an upload operation with multiple elements. If
     * there were failures, the failed elements need to be added again to the
     * storage (at the front). The return value indicates whether further
     * uploads can be done . This is the case if the operation was successful,
     * and more data is available to be uploaded. The return value is actually
     * an enumeration constant that carries some more information about the
     * current status of the storage.
     * @param chunk the list with data that has been uploaded
     * @param successCount the number of successful uploads
     * @return a constant describing the status of the offline storage
     */
    fun handleMultiUploadResult(chunk: List<LocationData>, successCount: Int): MultiUploadProgress {
        if (successCount < chunk.size) {
            storage.addAll(0, chunk.drop(successCount))
            return MultiUploadProgress.ERROR
        }

        return if(storage.isEmpty()) MultiUploadProgress.DONE else MultiUploadProgress.PROGRESS
    }

    /**
     * Adds the given (newest) _LocationData_ object to the managed storage.
     * Also takes care that the maximum capacity is not exceeded by removing
     * the oldest element if necessary.
     * @param locationData the data to be added
     */
    private fun addToStorage(locationData: LocationData) {
        if (intervalTooShort(locationData)) {
            storage.removeFirst()
        }

        storage.add(0, locationData)
        if (storage.size > capacity) {
            storage.removeLast()
        }
    }

    /**
     * Checks the interval in which data elements come in and filters out
     * elements below the threshold. This function checks if the interval
     * between the newest element and the second last one that has been added
     * (if existing) is below the minimum track interval. If so, the element
     * in between can be removed.
     * @param locationData a new location data object to be added
     * @return *true* if an element can be filtered out; else *false*
     */
    private fun intervalTooShort(locationData: LocationData) =
        storage.size > 2 && locationData.time.currentTime - storage[1].time.currentTime < minTrackInterval

    companion object {
        /**
         * An enum class defining the possible proceedings after a multi-upload
         * has been performed.
         *
         * Depending on the result of the last upload operation and the
         * availability of more data to upload, the sync operation can be
         * continued or aborted. Whether there was an error or not is also an
         * important information as it impacts the delay until the next
         * location update is attempted.
         *
         * @param canContinue flag whether more data can be uploaded
         * @param isError flag whether an error occurred
         */
        enum class MultiUploadProgress(val canContinue: Boolean, val isError: Boolean) {
            /**
             * Constant defining the state that no more data is available for
             * upload. So the offline storage has been synced successfully.
             */
            DONE(canContinue = false, isError = false),

            /**
             * Constant defining the state that there is more data to be
             * uploaded. So the sync operation can go on, provided that the
             * sync time has not yet been exceeded.
             */
            PROGRESS(canContinue = true, isError = false),

            /**
             * Constant defining the state that an upload failed. Then no
             * further uploads should be tried in this cycle.
             */
            ERROR(canContinue = false, isError = true)
        }
    }
}
