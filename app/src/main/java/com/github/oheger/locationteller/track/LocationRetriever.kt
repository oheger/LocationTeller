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
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.withContext
import kotlin.coroutines.Continuation
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
    /**
     * Sends the last known location to the actor for updating the server. The
     * result is the duration in seconds when the next location update should
     * be scheduled.
     * @param prefHandler the _PreferencesHandler_
     * @return the time period to the next location update
     */
    suspend fun retrieveAndUpdateLocation(prefHandler: PreferencesHandler): Int {
        Log.i(tag, "Triggering location update.")
        val lastLocation = fetchLocation()
        val locUpdate = locationUpdateFor(lastLocation, prefHandler)
        locationUpdateActor.send(locUpdate)
        return locUpdate.nextTrackDelay.await()
    }

    /**
     * Fetches the last known location from the location client assigned to
     * this object.
     * @return the last known location
     */
    private suspend fun fetchLocation(): Location? = withContext(Dispatchers.Main) {
        suspendCoroutine<Location?> { cont ->
            val callback = createLocationCallback(cont)
            locationClient.requestLocationUpdates(locationRequest, callback, null)
            Log.d(tag, "Requested location update.")
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
     * @param prefHandler the _PreferencesHandler_
     * @return the corresponding _LocationUpdate_ object
     */
    private fun locationUpdateFor(location: Location?, prefHandler: PreferencesHandler): LocationUpdate {
        val locData = location?.toLocationData() ?: unknownLocation
        return LocationUpdate(locData, location, CompletableDeferred(), prefHandler)
    }

    /**
     * Creates a callback to be invoked when a new location is available. This
     * location is returned as result of the _fetchLocation()_ co-routine.
     * @param cont the continuation object
     * @return the callback to receive a location
     */
    private fun createLocationCallback(cont: Continuation<Location?>): LocationCallback =
        object : LocationCallback() {
            override fun onLocationResult(result: LocationResult?) {
                Log.d(tag, "Got location result $result.")
                locationClient.removeLocationUpdates(this)
                cont.resumeWith(Result.success(result?.lastLocation))
            }
        }

    companion object {
        /** Tag for logging.*/
        private const val tag = "LocationRetriever"

        /** The interval to request updates from the location provider.*/
        private const val updateInterval = 5000L

        /** The request for a location update.*/
        private val locationRequest = LocationRequest.create().apply {
            interval = updateInterval
            fastestInterval = updateInterval
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }
    }
}
