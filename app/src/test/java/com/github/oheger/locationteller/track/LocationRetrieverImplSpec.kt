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
import android.os.Looper
import com.github.oheger.locationteller.MockDispatcher
import com.github.oheger.locationteller.ResetDispatcherListener
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import io.kotest.core.listeners.TestListener
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.verify
import java.util.concurrent.atomic.AtomicReference

/**
 * Test class for [[LocationRetrieverImpl]].
 */
class LocationRetrieverImplSpec : StringSpec() {
    override fun listeners(): List<TestListener> = listOf(ResetDispatcherListener)

    /**
     * Installs a mock dispatcher for the main thread.
     * @return the mock dispatcher
     */
    private fun initDispatcher(): MockDispatcher = MockDispatcher.installAsMain()

    init {
        "LocationRetriever should fetch a location successfully" {
            val location = mockk<Location>()
            val locResult = mockk<LocationResult>()
            val locClient = mockk<FusedLocationProviderClient>()
            val refCallback = AtomicReference<LocationCallback>()
            val looper = mockLooper()
            every { locResult.lastLocation } returns location
            every { locClient.requestLocationUpdates(any(), any(), looper) } answers {
                val request = arg<LocationRequest>(0)
                request.interval shouldBe 5000L
                request.fastestInterval shouldBe request.interval
                request.priority shouldBe Priority.PRIORITY_HIGH_ACCURACY
                val callback = arg<LocationCallback>(1)
                callback.onLocationResult(locResult)
                refCallback.set(callback)
                mockk()
            }
            every { locClient.removeLocationUpdates(any<LocationCallback>()) } returns mockk()
            val dispatcher = initDispatcher()
            val retriever = LocationRetrieverImpl(locClient, gpsTimeout)

            retriever.fetchLocation() shouldBe location
            dispatcher.tasks.isEmpty() shouldBe false
            verify { locClient.removeLocationUpdates(refCallback.get()) }
        }

        "LocationProcessor should handle a timeout when retrieving the location" {
            val locClient = mockk<FusedLocationProviderClient>()
            mockLooper()
            every { locClient.requestLocationUpdates(any(), any(), any<Looper>()) } returns mockk()
            every { locClient.removeLocationUpdates(any<LocationCallback>()) } returns mockk()
            initDispatcher()
            val retriever = LocationRetrieverImpl(locClient, gpsTimeout)

            retriever.fetchLocation() shouldBe null
            verify { locClient.removeLocationUpdates(any() as LocationCallback) }
        }
    }

    companion object {
        /** Constant for the GPS timeout.*/
        private const val gpsTimeout = 1000L
    }
}

/**
 * Return a mock [Looper] and mock the _myLooper()_ function to return this mock.
 */
private fun mockLooper(): Looper {
    val mockLooper = mockk<Looper>()
    mockkStatic(Looper::class)
    every { Looper.myLooper() } returns mockLooper
    return mockLooper
}
