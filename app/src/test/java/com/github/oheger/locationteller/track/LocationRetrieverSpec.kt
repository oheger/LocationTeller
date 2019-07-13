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
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import io.kotlintest.TestCase
import io.kotlintest.TestResult
import io.kotlintest.extensions.TestListener
import io.kotlintest.matchers.collections.shouldHaveSize
import io.kotlintest.matchers.doubles.shouldBeExactly
import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import io.mockk.*
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.CoroutineContext

/**
 * Test class for [LocationRetriever].
 */
@ExperimentalCoroutinesApi
class LocationRetrieverSpec : StringSpec() {
    /** Constant for the next update time to be returned by the mock actor.*/
    private val nextUpdate = 42

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
            locUpdate.locationData shouldBe unknownLocation
            locUpdate.nextTrackDelay.complete(nextUpdate)
        }
        return actor
    }

    /**
     * Installs a mock dispatcher for the main thread.
     * @return the mock dispatcher
     */
    @ExperimentalCoroutinesApi
    private fun initDispatcher(): MockDispatcher {
        val dispatcher = MockDispatcher()
        Dispatchers.setMain(dispatcher)
        return dispatcher
    }

    init {
        "LocationRetriever should pass a location data to the actor" {
            val latitude = 123.456
            val longitude = 654.789
            val currentTime = TimeData(20190629180617L)
            val actor = mockk<SendChannel<LocationUpdate>>()
            val timeService = mockk<TimeService>()
            val location = mockk<Location>()
            val locResult = mockk<LocationResult>()
            val prefHandler = mockk<PreferencesHandler>()
            val locClient = mockk<FusedLocationProviderClient>()
            val refCallback = AtomicReference<LocationCallback>()
            every { location.latitude } returns latitude
            every { location.longitude } returns longitude
            every { locResult.lastLocation } returns location
            every { locClient.requestLocationUpdates(any(), any(), null) } answers {
                val request = arg<LocationRequest>(0)
                request.interval shouldBe 5000L
                request.fastestInterval shouldBe request.interval
                request.priority shouldBe LocationRequest.PRIORITY_HIGH_ACCURACY
                val callback = arg<LocationCallback>(1)
                callback.onLocationResult(locResult)
                refCallback.set(callback)
                null
            }
            every { locClient.removeLocationUpdates(any<LocationCallback>()) } returns null
            every { timeService.currentTime() } returns currentTime
            coEvery { actor.send(any()) } answers {
                val locUpdate = arg<LocationUpdate>(0)
                locUpdate.locationData.latitude shouldBeExactly latitude
                locUpdate.locationData.longitude shouldBeExactly longitude
                locUpdate.locationData.time shouldBe currentTime
                locUpdate.prefHandler shouldBe prefHandler
                locUpdate.orgLocation shouldBe location
                locUpdate.nextTrackDelay.complete(nextUpdate)
            }
            val dispatcher = initDispatcher()
            val retriever = LocationRetriever(locClient, actor, timeService)

            retriever.retrieveAndUpdateLocation(prefHandler) shouldBe nextUpdate
            dispatcher.tasks shouldHaveSize 1
            verify { locClient.removeLocationUpdates(refCallback.get()) }
        }

        "LocationRetriever should handle a failure when retrieving the location" {
            val actor = createMockActorExpectingError()
            val locClient = mockk<FusedLocationProviderClient>()
            every { locClient.requestLocationUpdates(any(), any(), null) } answers {
                val callback = arg<LocationCallback>(1)
                callback.onLocationResult(null)
                null
            }
            every { locClient.removeLocationUpdates(any<LocationCallback>()) } returns null
            initDispatcher()
            val retriever = LocationRetriever(locClient, actor, mockk())

            retriever.retrieveAndUpdateLocation(mockk()) shouldBe nextUpdate
            coVerify { actor.send(any()) }
        }
    }

    /**
     * A mock dispatcher implementation. This is used to check whether some
     * actions are correctly executed on the main thread.
     */
    class MockDispatcher : CoroutineDispatcher() {
        /** Stores the tasks that have been dispatched.*/
        val tasks = mutableListOf<Runnable>()

        /**
         * This implementation records the task to be executed and executes it
         * directly.
         */
        override fun dispatch(context: CoroutineContext, block: Runnable) {
            tasks += block
            block.run()
        }
    }

    object ResetDispatcherListener : TestListener {
        @ExperimentalCoroutinesApi
        override fun afterTest(testCase: TestCase, result: TestResult) {
            Dispatchers.resetMain()
        }
    }
}
