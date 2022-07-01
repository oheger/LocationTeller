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

import android.content.Context
import com.github.oheger.locationteller.config.PreferencesHandler
import com.github.oheger.locationteller.config.TrackConfig
import com.github.oheger.locationteller.server.CurrentTimeService
import com.github.oheger.locationteller.server.TrackService
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.SendChannel


/**
 * A factory class for creating the actor to update location data.
 *
 * This factory is used internally by [LocationTellerService]. By providing a
 * mock implementation, a mock actor can be injected for testing purposes.
 */
class UpdaterActorFactory {
    /**
     * Creates the actor for updating location data. Result may be *null* if
     * mandatory configuration options are not set.
     * @param preferencesHandler the preferences handler
     * @param trackConfig the track configuration
     * @param crScope the co-routine scope
     * @return the new actor
     */
    fun createActor(
        preferencesHandler: PreferencesHandler, trackConfig: TrackConfig,
        crScope: CoroutineScope
    ): SendChannel<LocationUpdate>? {
        val serverConfig = preferencesHandler.createServerConfig()
        return if (serverConfig != null) {
            val trackService = TrackService.create(serverConfig)
            val uploadController = UploadController(
                preferencesHandler, trackService, trackConfig,
                OfflineLocationStorage(trackConfig.offlineStorageSize, trackConfig.minTrackInterval * 1000L),
                CurrentTimeService
            )
            locationUpdaterActor(uploadController, crScope)
        } else null
    }
}

/**
 * A factory class for creating a [LocationRetriever].
 *
 * This class mainly abstracts over obtaining a fused location provider which
 * is needed by a _LocationRetriever_. Optionally, validation of the GPS
 * positions can be added to the retriever.
 */
class LocationRetrieverFactory {
    /**
     * Creates a new _LocationRetriever_ based on the parameters specified.
     * @param context the context
     * @param trackConfig the track configuration
     * @param validating flag whether the resulting _LocationRetriever_ should
     * validate its GPS positions
     * @return the _LocationRetriever_ instance
     */
    fun createRetriever(context: Context, trackConfig: TrackConfig, validating: Boolean): LocationRetriever {
        val retriever = LocationRetrieverImpl(
            LocationServices.getFusedLocationProviderClient(context),
            trackConfig.gpsTimeout * 1000L
        )
        return if (validating) {
            ValidatingLocationRetriever(
                retriever,
                ElapsedTimeService,
                trackConfig.maxSpeedIncrease,
                trackConfig.walkingSpeed
            )
        } else retriever
    }
}

/**
 * A factory class for creating a [LocationProcessor].
 *
 * This factory is used internally by [LocationTellerService]. By providing a
 * mock implementation, a mock actor can be injected for testing purposes.
 */
class LocationProcessorFactory {
    /**
     * Creates a new _LocationProcessor_ based on the given parameters.
     * @param retriever the _LocationRetriever_
     * @param updater the actor for publishing updates
     * @return the _LocationProcessor_ instance
     */
    fun createProcessor(retriever: LocationRetriever, updater: SendChannel<LocationUpdate>):
            LocationProcessor = LocationProcessor(retriever, updater, CurrentTimeService)
}
