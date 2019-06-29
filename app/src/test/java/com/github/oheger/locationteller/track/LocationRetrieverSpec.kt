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
import com.github.oheger.locationteller.server.TimeData
import com.github.oheger.locationteller.server.TimeService
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.tasks.OnCompleteListener
import com.google.android.gms.tasks.Task
import io.kotlintest.matchers.doubles.shouldBeExactly
import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import io.mockk.*
import kotlinx.coroutines.channels.SendChannel

/**
 * Test class for [LocationRetriever].
 */
class LocationRetrieverSpec : StringSpec() {
    /** Constant for the next update time to be returned by the mock actor.*/
    private val nextUpdate = 42

    /**
     * Creates a mock task that is prepared to handle the registration of a
     * completion listener.
     * @param success the success flag to be returned by the task
     * @return the mock task
     */
    private fun createPreparedTaskMock(success: Boolean = true): Task<Location> {
        val task = mockk<Task<Location>>()
        every { task.addOnCompleteListener(any()) } answers {
            arg<OnCompleteListener<Location>>(0).onComplete(task)
            task
        }
        every { task.isSuccessful } returns success
        return task
    }

    /**
     * Creates a mock for an updater actor that is prepared to expect a message
     * indicating an error.
     * @return the mock actor
     */
    private fun createMockActorExpectingError(): SendChannel<LocationUpdate> {
        val actor = mockk<SendChannel<LocationUpdate>>()
        coEvery { actor.send(any()) } answers {
            val locUpdate = arg<LocationUpdate>(0)
            locUpdate.locationData shouldBe unknownLocation
            locUpdate.nextTrackDelay.complete(nextUpdate)
        }
        return actor
    }

    /**
     * Creates a mock for a location client that is prepared to return the
     * given task when asked for the last location.
     * @param task the task to be returned by the mock
     * @return the mock location client
     */
    private fun createLocationClient(task: Task<Location>): FusedLocationProviderClient {
        val locClient = mockk<FusedLocationProviderClient>()
        every { locClient.lastLocation } returns task
        return locClient
    }

    init {
        "LocationRetriever should pass a location data to the actor" {
            val latitude = 123.456
            val longitude = 654.789
            val currentTime = TimeData(20190629180617L)
            val actor = mockk<SendChannel<LocationUpdate>>()
            val timeService = mockk<TimeService>()
            val location = mockk<Location>()
            val task = createPreparedTaskMock()
            val locClient = createLocationClient(task)
            every { location.latitude } returns latitude
            every { location.longitude } returns longitude
            every { locClient.lastLocation } returns task
            every { timeService.currentTime() } returns currentTime
            every { task.result } returns location
            coEvery { actor.send(any()) } answers {
                val locUpdate = arg<LocationUpdate>(0)
                locUpdate.locationData.latitude shouldBeExactly latitude
                locUpdate.locationData.longitude shouldBeExactly longitude
                locUpdate.locationData.time shouldBe currentTime
                locUpdate.nextTrackDelay.complete(nextUpdate)
            }
            val retriever = LocationRetriever(locClient, actor, timeService)

            retriever.retrieveAndUpdateLocation() shouldBe nextUpdate
        }

        "LocationRetriever should handle a failure when retrieving the location" {
            val actor = createMockActorExpectingError()
            val task = createPreparedTaskMock(success = false)
            val locClient = createLocationClient(task)
            every { task.exception } returns Exception("No location")
            val retriever = LocationRetriever(locClient, actor, mockk())

            retriever.retrieveAndUpdateLocation() shouldBe nextUpdate
        }
    }

}
