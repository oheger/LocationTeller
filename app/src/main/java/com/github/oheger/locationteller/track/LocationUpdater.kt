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

import com.github.oheger.locationteller.server.LocationData
import com.github.oheger.locationteller.server.TimeData
import com.github.oheger.locationteller.server.TrackService
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.actor
import kotlin.math.min

/**
 * Constant for an unknown location data object. This is used as an error
 * indicator, for instance in cases when no current location could be
 * retrieved.
 */
val unknownLocation = LocationData(0.0, 0.0, TimeData(0))

/**
 * Data class for the messages processed by location updater actor.
 *
 * The message contains a new location and a _CompletableDeferred_ that is used
 * to communicate the next update time to the caller.
 */
data class LocationUpdate(
    val locationData: LocationData,
    val nextTrackDelay: CompletableDeferred<Int>
)

/**
 * A function providing an actor that guards adding new location data via a
 * _TrackService_ object. Location updates can arrive from multiple threads;
 * however, _TrackService_ is not thread-safe. Therefore, this actor is
 * introduced. Messages of type [LocationUpdate] can be sent to it, and they
 * will be processed in sequence.
 *
 * The actor function also keeps track on the last known location. Whether it
 * has changed or not impacts the interval when the next location update has to
 * be requested: the update interval is increased based on the configuration
 * settings until the maximum is reached or a location change is detected.
 *
 * @param trackService the _TrackService_ to be called
 * @param trackConfig the configuration for tracking location data
 * @return the channel to send messages to the actor
 */
@ObsoleteCoroutinesApi
fun locationUpdaterActor(trackService: TrackService, trackConfig: TrackConfig, crScope: CoroutineScope):
        SendChannel<LocationUpdate> {
    return crScope.actor {
        var lastLocation = LocationData(0.0, 0.0, TimeData(0))

        var updateInterval = trackConfig.minTrackInterval

        fun locationChanged(locationUpdate: LocationUpdate): Boolean =
            locationUpdate.locationData.latitude != lastLocation.latitude ||
                    locationUpdate.locationData.longitude != lastLocation.longitude

        for (locUpdate in channel) {
            if (locationChanged(locUpdate)) {
                if (locUpdate.locationData != unknownLocation) {
                    trackService.addLocation(locUpdate.locationData)
                }
                //TODO implement error handling
                lastLocation = locUpdate.locationData
                updateInterval = trackConfig.minTrackInterval
            } else {
                updateInterval = min(
                    updateInterval + trackConfig.intervalIncrementOnIdle,
                    trackConfig.maxTrackInterval
                )
            }
            locUpdate.nextTrackDelay.complete(updateInterval)
        }
    }
}
