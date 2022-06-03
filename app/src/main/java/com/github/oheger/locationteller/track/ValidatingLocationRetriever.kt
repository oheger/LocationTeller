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
import com.github.oheger.locationteller.server.TimeService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A specialized [LocationRetriever] implementation that decorates another
 * retriever and performs a validation of the GPS location obtained from this
 * wrapped retriever.
 *
 * The GPS locations retrieved via the standard system service can be
 * inaccurate from time to time; a new position is then suddenly way off from
 * the last position that was reported. This implementation tries to detect
 * such problems using an algorithm as follows:
 *
 * An instance keeps track on the current velocity. For each new position
 * received, it calculates the (average) velocity from the last position to the
 * new one. If this velocity is increased by a factor larger than a
 * configurable threshold, the position is considered suspicious, and the
 * result *null* is returned, indicating an error. This causes another location
 * request after a short interval; so the velocity can be checked again. If it
 * is actually higher - maybe the user switched from walking to driving by
 * car -, the retriever adapts itself to the new speed.
 *
 * That way, single inaccurate locations can be detected; but multiple wrong
 * position updates in series are still a problem.
 */
class ValidatingLocationRetriever(
    /** The wrapped retriever that is asked for GPS locations. */
    val wrappedRetriever: LocationRetriever,

    /**
     * A service for obtaining time information. This is needed to calculate
     * the velocity between two location updates.
     */
    val timeService: TimeService,

    /**
     * The threshold factor for a speed increase. If the velocity increases by
     * a factor larger than this one, the current GPS position is considered
     * inaccurate.
     */
    val maxSpeedIncrease: Double,

    /**
     * The normal walking speed (in m/s). An illegal velocity is flagged only
     * if it is higher than this one. This is to prevent that small changes in
     * the speed already trigger this algorithm; e.g. if the user stands for a
     * while and then moves on.
     */
    val walkingSpeed: Double
) : LocationRetriever {
    /** Stores the last location received from the wrapped retriever. */
    private var lastLocation: Location? = null

    /** The timestamp of the last location update. */
    private var lastTime = 0L

    /** The last velocity that has been calculated. */
    private var lastVelocity = 0.0

    /**
     * Obtains the current GPS location from the wrapped retriever and
     * validates the result. Valid results are returned; otherwise result is
     * *null*, triggering error handling.
     */
    override suspend fun fetchLocation(): Location? = withContext(Dispatchers.Main) {
        val location = wrappedRetriever.fetchLocation()
        if (location == null) null
        else {

            val now = timeService.currentTime().currentTime
            if (lastLocation == null) {
                lastLocation = location
                lastTime = now
                location
            } else {

                val distance = location.distanceTo(lastLocation)
                val time = now - lastTime
                val velocity = if (time > 0) distance * 1000.0 / time
                else lastVelocity
                val currentVelocity = lastVelocity
                lastVelocity = velocity
                if (currentVelocity >= walkingSpeed && velocity > currentVelocity * maxSpeedIncrease) null
                else {

                    lastLocation = location
                    lastTime = now
                    location
                }
            }
        }
    }
}
