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

import com.github.oheger.locationteller.config.PreferencesHandler
import com.github.oheger.locationteller.config.TrackConfig
import com.github.oheger.locationteller.server.ServerConfig
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject

/**
 * A module defining some helper functionality for several tests in the
 * _track_ package.
 *
 * The object defines constants for configurations and provides some functions
 * for setting up mock objects.
 */
object TrackTestHelper {
    /** A default test server configuration.*/
    val defServerConfig = ServerConfig(
        serverUri = "https://track-server.tst",
        basePath = "/my-tracks", user = "scott", password = "tiger"
    )

    /** A default test track configuration.*/
    val defTrackConfig = TrackConfig(
        minTrackInterval = 42, maxTrackInterval = 727,
        locationValidity = 1000, intervalIncrementOnIdle = 50,
        locationUpdateThreshold = 22, gpsTimeout = 10, retryOnErrorTime = 4,
        autoResetStats = false, offlineStorageSize = 8, maxOfflineStorageSyncTime = 20,
        multiUploadChunkSize = 4, maxSpeedIncrease = 2.0, walkingSpeed = 1.1111
    )

    /**
     * Prepare the creation of a [TrackConfig] from the given [mockHandler]. This means that the factory function
     * from [TrackConfig]'s companion object has to be mocked to return [trackConf].
     */
    fun prepareTrackConfigFromPreferences(mockHandler: PreferencesHandler, trackConf: TrackConfig = defTrackConfig) {
        mockkObject(TrackConfig)
        every { TrackConfig.fromPreferences(mockHandler) } returns trackConf
    }

    /**
     * Installs a mock preferences manager that returns shared preferences
     * initialized with the test configurations.
     * @param svrConf the server config to initialize preferences
     * @param trackConf the track config to initialize preferences
     * @param trackingEnabled flag whether tracking should be enabled
     * @return the mock for the preferences handler
     */
    fun preparePreferences(
        svrConf: ServerConfig? = defServerConfig,
        trackConf: TrackConfig = defTrackConfig,
        trackingEnabled: Boolean = true
    ): PreferencesHandler {
        val handler = mockk<PreferencesHandler>()
        every { handler.createServerConfig() } returns svrConf
        every { handler.isTrackingEnabled() } returns trackingEnabled
        every { handler.checkCount() } returns 42
        every { handler.updateCount() } returns 11
        every { handler.errorCount() } returns 1
        every { handler.totalDistance() } returns 100

        prepareTrackConfigFromPreferences(handler, trackConf)

        return handler
    }
}
