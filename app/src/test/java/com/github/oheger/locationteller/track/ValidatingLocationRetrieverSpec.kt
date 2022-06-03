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
import com.github.oheger.locationteller.MockDispatcher
import com.github.oheger.locationteller.ResetDispatcherListener
import com.github.oheger.locationteller.server.TimeData
import com.github.oheger.locationteller.server.TimeService
import io.kotest.core.listeners.TestListener
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.util.concurrent.TimeUnit

/**
 * Test class for [ValidatingLocationRetriever].
 */
@ExperimentalCoroutinesApi
class ValidatingLocationRetrieverSpec : StringSpec({
    "ValidatingLocationRetriever should delegate to the wrapped retriever" {
        val initLoc = mockk<Location>()
        val loc1 = locationWithDistance(initLoc, MINUTE_DIST)
        val loc2 = locationWithDistance(loc1, MINUTE_DIST)
        val locations = listOf(initLoc, loc1, loc2)
        val retriever = createValidatingRetriever(locations)

        locations.forEach { loc ->
            retriever.fetchLocation() shouldBe loc
        }
    }

    "ValidatingLocationRetriever should accept the maximum speed increase" {
        val initLoc = mockk<Location>()
        val loc1 = locationWithDistance(initLoc, MINUTE_DIST)
        val loc2 = locationWithDistance(loc1, (INCREASE_FACTOR - .001f) * MINUTE_DIST)
        val locations = listOf(initLoc, loc1, loc2)
        val retriever = createValidatingRetriever(locations)

        locations.forEach { loc ->
            retriever.fetchLocation() shouldBe loc
        }
    }

    "ValidatingLocationRetriever should pass errors from the wrapped retriever to the caller" {
        val initLoc = mockk<Location>()
        val loc1 = locationWithDistance(initLoc, MINUTE_DIST)
        val loc2 = locationWithDistance(loc1, MINUTE_DIST)
        val locations = listOf(initLoc, loc1, null, loc2)
        val retriever = createValidatingRetriever(locations)

        locations.forEach { loc ->
            retriever.fetchLocation() shouldBe loc
        }
    }

    "ValidatingLocationRetriever should detect an invalid location" {
        val initLoc = mockk<Location>()
        val loc1 = locationWithDistance(initLoc, MINUTE_DIST)
        val loc2 = locationWithDistance(loc1, INCREASE_FACTOR * MINUTE_DIST + 1)
        val locations = listOf(initLoc, loc1, loc2)
        val retriever = createValidatingRetriever(locations)

        skipLocations(retriever, 2)
        retriever.fetchLocation().shouldBeNull()
    }

    "ValidatingLocationRetriever should adapt itself to a higher velocity" {
        val initLoc = mockk<Location>()
        val loc1 = locationWithDistance(initLoc, MINUTE_DIST)
        val loc2 = locationWithDistance(loc1, INCREASE_FACTOR * MINUTE_DIST + 1)
        val loc3 = locationWithDistance(loc1, (2 * INCREASE_FACTOR + 1) * MINUTE_DIST)
        val locations = listOf(initLoc, loc1, loc2, loc3)
        val retriever = createValidatingRetriever(locations)

        skipLocations(retriever, 3)
        retriever.fetchLocation() shouldBe loc3
    }

    "ValidatingLocationRetriever should ignore velocity increments for low velocities" {
        val initLoc = mockk<Location>()
        val loc1 = locationWithDistance(initLoc, 10f)
        val loc2 = locationWithDistance(loc1, INCREASE_FACTOR * MINUTE_DIST + 1)
        val locations = listOf(initLoc, loc1, loc2)
        val retriever = createValidatingRetriever(locations)

        locations.forEach { loc ->
            retriever.fetchLocation() shouldBe loc
        }
    }

    "ValidatingLocationRetriever should deal with a 0 delta T" {
        val timeService = mockk<TimeService>()
        every { timeService.currentTime() } returnsMany listOf(
            TimeData(0), TimeData(TICK), TimeData(TICK), TimeData(3 * TICK)
        )
        val initLoc = mockk<Location>()
        val loc1 = locationWithDistance(initLoc, MINUTE_DIST)
        val loc2 = locationWithDistance(loc1, MINUTE_DIST)
        val loc3 = locationWithDistance(loc2, MINUTE_DIST)
        val locations = listOf(initLoc, loc1, loc2, loc3)
        val retriever = createValidatingRetriever(locations, mockTimeService = timeService)

        locations.forEach { loc ->
            retriever.fetchLocation() shouldBe loc
        }
    }
}) {
    override fun listeners(): List<TestListener> = listOf(ResetDispatcherListener)
}

/** Average walking speed in m/s ~ 4 km/h. */
const val WALKING_SPEED = 1.1

/** The distance one can walk in one minute. */
const val MINUTE_DIST = (WALKING_SPEED * 60).toFloat()

/** The speed increment threshold factor. */
const val INCREASE_FACTOR = 2.0f

/** Constant for a clock tick; corresponds to one minute. */
val TICK = TimeUnit.MINUTES.toMillis(1)

/**
 * Creates a [ValidatingLocationRetriever] to be used by tests that has been
 * configured with mock objects and default settings. The wrapped retriever is
 * prepared to return the specified locations for its single invocations.
 * @param wrappedLocations the locations to be returned by the wrapped
 * retriever
 * @param mockTimeService optional mock for the timer service
 */
@ExperimentalCoroutinesApi
private fun createValidatingRetriever(
    wrappedLocations: List<Location?>,
    mockTimeService: TimeService? = null
): ValidatingLocationRetriever {
    val wrappedRetriever = mockk<LocationRetriever>()
    coEvery { wrappedRetriever.fetchLocation() } returnsMany wrappedLocations
    val timeService = mockTimeService ?: tickTimeService()
    MockDispatcher.installAsMain()
    return ValidatingLocationRetriever(wrappedRetriever, timeService, INCREASE_FACTOR.toDouble(), WALKING_SPEED)
}

/**
 * Creates a new mock [Location] that is configured to return a specific
 * distance to a reference location.
 * @param refLocation the reference location
 * @param distance the distance to be returned
 * @return the new mock location
 */
private fun locationWithDistance(refLocation: Location, distance: Float): Location {
    val location = mockk<Location>()
    every { location.distanceTo(refLocation) } returns distance
    return location
}

/**
 * Returns a mock [TimeService] implementation, which increments the time in
 * regular intervals (by one [TICK]), everytime it is invoked. When the time
 * flows in a regular fashion, distance calculations become easier.
 * @return the tick time service
 */
private fun tickTimeService(): TimeService {
    var ticks = 0L
    return object : TimeService {
        override fun currentTime(): TimeData {
            val currentTime = TimeData(ticks)
            ticks += TICK
            return currentTime
        }
    }
}

/**
 * Requests the given number of locations from a retriever, ignoring the
 * concrete results.
 * @param retriever the test [LocationRetriever]
 * @param skipCount the number of results to skip
 */
private suspend fun skipLocations(retriever: LocationRetriever, skipCount: Int) {
    for (i in 1..skipCount) {
        retriever.fetchLocation()
    }
}
