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

import com.github.oheger.locationteller.server.LocationData
import com.github.oheger.locationteller.server.TimeData
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe

/**
 * Test class for [[OfflineLocationStorage]].
 */
class OfflineLocationStorageSpec : StringSpec() {
    init {
        "OfflineLocationStorage should allow a direct upload if the storage is empty" {
            val storage = createStorage()

            storage.canUploadDirectly(locData(1, 0)) shouldBe true
        }

        "OfflineLocationStorage should return an empty chunk of upload data if it contains no data" {
            val storage = createStorage()

            storage.nextUploadChunk(42) shouldHaveSize 0
        }

        "OfflineLocationStorage should forbid a direct upload if the store contains elements" {
            val storage = createStorage()

            storage.storeFailedUpload(locData(1, minTrackingInterval))
            storage.canUploadDirectly(locData(2, 2 * minTrackingInterval)) shouldBe false
        }

        "OfflineLocationStorage should return the next chunk of data to be uploaded" {
            val data1 = locData(1, minTrackingInterval)
            val data2 = locData(2, 2 * minTrackingInterval)
            val data3 = locData(3, 3 * minTrackingInterval)
            val storage = createStorageWithData(locData(0, 0), data1, data2, data3)

            val chunk = storage.nextUploadChunk(3)
            chunk shouldContainExactly listOf(data3, data2, data1)
        }

        "OfflineLocationStorage should remove the items returned as the next data chunk" {
            val data0 = locData(0, 0)
            val data1 = locData(1, minTrackingInterval)
            val data2 = locData(2, 2 * minTrackingInterval)
            val data3 = locData(3, 3 * minTrackingInterval)
            val storage = createStorageWithData(data0, data1, data2, data3)
            storage.nextUploadChunk(3)

            val chunk = storage.nextUploadChunk(3)
            chunk shouldContainExactly listOf(data0)
        }

        "OfflineLocationStorage should process a successful multi-upload operation if no data is pending" {
            val locData = listOf(
                locData(1, minTrackingInterval),
                locData(2, minTrackingInterval * 2),
                locData(3, minTrackingInterval * 3)
            )
            val storage = createStorage()

            storage.handleMultiUploadResult(locData,
                locData.size) shouldBe OfflineLocationStorage.Companion.MultiUploadProgress.DONE
            storage.nextUploadChunk(42) shouldHaveSize 0
        }

        "OfflineLocationStorage should process a successful multi-upload operation if more data is pending" {
            val locData = listOf(
                locData(1, minTrackingInterval),
                locData(2, minTrackingInterval * 2),
                locData(3, minTrackingInterval * 3)
            )
            val data0 = locData(0, 0)
            val storage = createStorageWithData(data0)

            storage.handleMultiUploadResult(locData,
                locData.size) shouldBe OfflineLocationStorage.Companion.MultiUploadProgress.PROGRESS
            storage.nextUploadChunk(42) shouldContainExactly listOf(data0)
        }

        "OfflineLocationStorage should process a partly failed multi-upload operation" {
            val locData = listOf(
                locData(3, minTrackingInterval * 3),
                locData(2, minTrackingInterval * 2),
                locData(1, minTrackingInterval)
            )
            val data0 = locData(0, 0)
            val storage = createStorageWithData(data0)

            storage.handleMultiUploadResult(locData,
                2) shouldBe OfflineLocationStorage.Companion.MultiUploadProgress.ERROR
            storage.nextUploadChunk(3) shouldContainExactly listOf(locData[2], data0)
        }

        "OfflineLocationStore should add the element of a direct upload check to the store if needed" {
            val data1 = locData(1, minTrackingInterval)
            val data2 = locData(2, minTrackingInterval * 2)
            val storage = createStorageWithData(data1)

            storage.canUploadDirectly(data2) shouldBe false
            storage.nextUploadChunk(3) shouldContainExactly listOf(data2, data1)
        }

        "OfflineLocationStore should ensure its maximum capacity" {
            val data = (1..(capacity + 1)).map { idx -> locData(idx, minTrackingInterval * idx) }
            val storage = createStorageWithData(*data.toTypedArray())

            val chunk = storage.nextUploadChunk(capacity + 2)
            chunk shouldHaveSize capacity
            chunk shouldNotContain data[0]
        }

        "OfflineLocationStore should filter out data arriving with a frequency too high" {
            val data0 = locData(0, 0)
            val data1 = locData(1, 10000)
            val data2 = locData(2, 40000)
            val data3 = locData(3, minTrackingInterval)
            val data4 = locData(4, minTrackingInterval + 45000)
            val data5 = locData(5, 2 * minTrackingInterval - 1)
            val data6 = locData(6, 3 * minTrackingInterval)
            val storage = createStorageWithData(data0, data1, data2, data3, data4, data5, data6)

            val content = storage.nextUploadChunk(capacity)
            content shouldContainExactly listOf(data6, data5, data3, data1, data0)
        }
    }

    companion object {
        /** The capacity of the test storage. */
        private const val capacity = 8

        /** The minimum tracking interval. */
        private const val minTrackingInterval = 60000L

        /** The reference latitude to generate GPS data. */
        private const val refLatitude = 7.12

        /** The reference longitude to generate GPS data. */
        private const val refLongitude = 12.89

        /**
         * Time value used as reference time when generating relative
         * _TimeData_ objects.
         */
        private val referenceTime = System.currentTimeMillis()

        /**
         * Creates a new test instance with default settings.
         * @return the new test instance
         */
        private fun createStorage(): OfflineLocationStorage =
            OfflineLocationStorage(capacity, minTrackingInterval)

        /**
         * Creates a new test instance with default settings and adds the
         * given test data items to it.
         * @param data the data items to be added
         * @return the test storage instance
         */
        private fun createStorageWithData(vararg data: LocationData): OfflineLocationStorage {
            val storage = createStorage()
            data.forEach(storage::storeFailedUpload)
            return storage
        }

        /**
         * Generates a test _LocationData_ object based on the passed in
         * parameters. From the index, unique GPS position data is generated.
         * The _deltaT_ parameter is interpreted as an offset in milliseconds
         * to a reference time.
         * @param index the index of the data object
         * @param deltaT the time offset when this data was created
         * @return the new _LocationData_ object
         */
        private fun locData(index: Int, deltaT: Long): LocationData {
            val posDelta = index.toDouble() / 100.0
            return LocationData(
                latitude = refLatitude + posDelta, longitude = refLongitude - posDelta,
                time = TimeData(referenceTime + deltaT)
            )
        }
    }
}