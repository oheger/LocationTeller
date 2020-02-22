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

import android.location.Location
import android.util.Log
import com.github.oheger.locationteller.server.LocationData
import com.github.oheger.locationteller.server.TimeService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.channels.SendChannel

/**
 * A helper class for retrieving an update of the current location and passing
 * this update to the server via a location updater actor.
 *
 * This class is invoked from the service responsible for tracking the
 * location. It asks the given [[LocationRetriever]] for the last known
 * location. This information is then passed to the given channel.
 *
 * @param locationRetriever the object to retrieve the GPS location
 * @param locationUpdateActor the actor to pass the location to
 * @param timeService the time service
 */
class LocationProcessor(
    val locationRetriever: LocationRetriever,
    val locationUpdateActor: SendChannel<LocationUpdate>,
    val timeService: TimeService
) {
    /**
     * Sends the last known location to the actor for updating the server. The
     * result is the duration in seconds when the next location update should
     * be scheduled.
     * @return the time period to the next location update
     */
    @ObsoleteCoroutinesApi
    suspend fun retrieveAndUpdateLocation(): Int {
        Log.i(tag, "Triggering location update.")
        val lastLocation = fetchLocation()
        val locUpdate = locationUpdateFor(lastLocation)
        locationUpdateActor.send(locUpdate)
        return locUpdate.nextTrackDelay.await()
    }

    /**
     * Fetches the last known location from the _LocationRetriever_ assigned to
     * this object.
     * @return the last known location
     */
    @ObsoleteCoroutinesApi
    private suspend fun fetchLocation(): Location? = locationRetriever.fetchLocation()

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
        val locData = location?.toLocationData() ?: unknownLocation()
        return LocationUpdate(locData, location, CompletableDeferred())
    }

    /**
     * Returns a _LocationData_ object representing an unknown location. This
     * object has no valid position data set, but the time is accurate. (During
     * a location update, an invalid location is detected by the original
     * location being *null*.)
     * @return an object representing an unknown _LocationData_
     */
    private fun unknownLocation(): LocationData =
        LocationData(0.0, 0.0, timeService.currentTime())

    companion object {
        /** Tag for logging.*/
        private const val tag = "LocationProcessor"
    }
}
