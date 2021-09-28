/*
 * Copyright 2019-2021 The Developers.
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
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.ticker
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.Continuation
import kotlin.coroutines.suspendCoroutine

/**
 * The default implementation of the service to retrieve the current GPS
 * location.
 *
 * The class requests an update of the GPS location from the corresponding
 * system service and waits for a configurable timeout for a response. This is
 * of course done without blocking the main thread. The location that was
 * retrieved (which can be *null*) is* then returned to the caller.

 * @param locationClient the client to obtain the last known location
 * @param timeout the timeout for the GPS request (in milliseconds)
 */
class LocationRetrieverImpl(
    val locationClient: FusedLocationProviderClient,
    val timeout: Long
) : LocationRetriever {
    /**
     * Fetches the current location from the location client assigned to this
     * object. If no answer is received withing the configured timeout, result
     * is *null*.
     * @return the current location or *null*
     */
    @ObsoleteCoroutinesApi
    override suspend fun fetchLocation(): Location? = withContext(Dispatchers.Main) {
        val timeout = timeout
        val tickerChannel = ticker(timeout, timeout)
        suspendCoroutine<Location?> { cont ->
            val callback = LocationCallbackImpl(locationClient, cont, tickerChannel)
            launch {
                tickerChannel.receive()
                callback.cancelLocationUpdate()
            }
            try {
                locationClient.requestLocationUpdates(locationRequest, callback, null)
                Log.d(tag, "Requested location update.")
            } catch (e: SecurityException) {
                Log.e(tag, "Missing right to request location.", e)
            }
        }
    }

    /**
     * An implementation of _LocationCallback_ that continues the current
     * co-routine when a location update is retrieved. It is also possible to
     * cancel waiting for an update, e.g. when a timeout occurs.
     *
     * @param locationClient the location client
     * @param cont the object to continue the co-routine
     * @param tickerChannel the channel for timer events
     */
    private class LocationCallbackImpl(
        val locationClient: FusedLocationProviderClient,
        val cont: Continuation<Location?>,
        val tickerChannel: ReceiveChannel<Unit>
    ) : LocationCallback() {
        override fun onLocationResult(result: LocationResult?) {
            Log.d(tag, "Got location result $result.")
            removeLocationUpdateRegistration()
            cont.resumeWith(Result.success(result?.lastLocation))
        }

        /**
         * Cancels the location update. This method is called when the timeout
         * for the GPS signal is reached.
         */
        fun cancelLocationUpdate() {
            Log.i(tag, "Canceling update for location.")
            removeLocationUpdateRegistration()
            cont.resumeWith(Result.success(null))
        }

        /**
         * Removes the registration for location updates. This method must be
         * called to stop the GPS client.
         */
        private fun removeLocationUpdateRegistration() {
            tickerChannel.cancel()
            locationClient.removeLocationUpdates(this)
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
