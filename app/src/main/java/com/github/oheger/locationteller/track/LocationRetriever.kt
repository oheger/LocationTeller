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
import android.util.Log
import com.github.oheger.locationteller.server.LocationData
import com.github.oheger.locationteller.server.TimeService
import com.google.android.gms.location.FusedLocationProviderClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.SendChannel
import kotlin.coroutines.suspendCoroutine

/**
 * A helper class for retrieving an update of the current location and passing
 * this update to the server via a location updater actor.
 *
 * This class is invoked from the service responsible for tracking the
 * location. It asks the given location client for the last known location.
 * This information is then passed to the given channel.
 *
 * @param locationClient the client to obtain the last known location
 * @param locationUpdateActor the actor to pass the location to
 * @param timeService the time service
 */
class LocationRetriever(
    val locationClient: FusedLocationProviderClient,
    val locationUpdateActor: SendChannel<LocationUpdate>,
    val timeService: TimeService
) {
    /** Tag for logging.*/
    private val tag = "LocationRetriever"

    /**
     * Sends the last known location to the actor for updating the server. The
     * result is the duration in seconds when the next location update should
     * be scheduled.
     * @return the time period to the next location update
     */
    suspend fun retrieveAndUpdateLocation(): Int {
        Log.i(tag, "Triggering location update.")
        val lastLocation = fetchLocation()
        val locUpdate = locationUpdateFor(lastLocation)
        locationUpdateActor.send(locUpdate)
        return locUpdate.nextTrackDelay.await()
    }

    /**
     * Fetches the last known location from the location client assigned to
     * this object.
     * @return the last known location
     */
    private suspend fun fetchLocation(): Location? = suspendCoroutine { cont ->
        locationClient.lastLocation.addOnCompleteListener { task ->
            val location = if (task.isSuccessful) task.result
            else {
                Log.e(tag, "Error when retrieving location!", task.exception)
                null
            }
            cont.resumeWith(Result.success(location))
        }
    }

    /**
     * Converts a _Location_ object to a _LocationData_.
     * @return the _LocationData_
     */
    private fun Location.toLocationData() =
        LocationData(latitude, longitude, timeService.currentTime())

    /**
     * Returns a _LocationUpdate_ object to report the given location. The
     * location can be *null*; in this case, the special unknown location
     * instance is used.
     * @param location the location
     * @return the corresponding _LocationUpdate_ object
     */
    private fun locationUpdateFor(location: Location?): LocationUpdate {
        val locData = location?.toLocationData() ?: unknownLocation
        return LocationUpdate(locData, CompletableDeferred())
    }
}
