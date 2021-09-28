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
import com.github.oheger.locationteller.server.LocationData
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.actor

/**
 * Data class for the messages processed by location updater actor.
 *
 * The message contains a new location data object, the original _Location_
 * retrieved from the fused location provider, and a _CompletableDeferred_ that
 * is used to communicate the next update time to the caller.
 */
data class LocationUpdate(
    val locationData: LocationData,
    val orgLocation: Location?,
    val nextTrackDelay: CompletableDeferred<Int>
) {
    /**
     * Returns the time of this update.
     * @return the update time
     */
    fun updateTime(): Long = locationData.time.currentTime
}

/**
 * A function providing an actor that guards adding new location data via an
 * [[UploadController]] object. Location updates can arrive from multiple
 * threads; however, _UploadController_ and the underlying _TrackService_ are
 * not thread-safe. Therefore, this actor is introduced. Messages of type
 * [LocationUpdate] can be sent to it, and they will be processed in sequence
 * by directly forwarding them to the _UploadController_. The result returned
 * by the controller - the delay until the next location check - is passed to
 * the sender of the message via the _nextTrackDelay_ property of the
 * _LocationUpdate_ update object received.
 *
 * @param uploadController the object handling the upload
 * @param crScope the co-routine scope
 * @return the channel to send messages to the actor
 */
@ObsoleteCoroutinesApi
fun locationUpdaterActor(uploadController: UploadController, crScope: CoroutineScope): SendChannel<LocationUpdate> {
    return crScope.actor {
        for (locUpdate in channel) {
            val delay = uploadController.handleUpload(locUpdate.locationData, locUpdate.orgLocation)
            locUpdate.nextTrackDelay.complete(delay)
        }
    }
}
