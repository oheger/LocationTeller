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
import com.github.oheger.locationteller.config.ConfigManager
import com.github.oheger.locationteller.config.PreferencesHandler
import com.github.oheger.locationteller.config.ReceiverConfig
import com.github.oheger.locationteller.config.TrackConfig
import com.github.oheger.locationteller.config.TrackServerConfig
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
    val DEFAULT_SERVER_CONFIG = TrackServerConfig(
        serverUri = "https://track-server.tst",
        basePath = "/my-tracks",
        user = "scott",
        password = "tiger"
    )

    /** A server configuration with undefined properties. */
    val UNDEFINED_SERVER_CONFIG = DEFAULT_SERVER_CONFIG.copy(serverUri = "")

    /** A default test track configuration.*/
    val DEFAULT_TRACK_CONFIG = TrackConfig(
        minTrackInterval = 42,
        maxTrackInterval = 727,
        locationValidity = 1_103_027, // 12d, 18h, 23min, 47sec
        intervalIncrementOnIdle = 50,
        locationUpdateThreshold = 22,
        gpsTimeout = 60,
        retryOnErrorTime = 40,
        autoResetStats = false,
        offlineStorageSize = 8,
        maxOfflineStorageSyncTime = 20,
        multiUploadChunkSize = 4,
        maxSpeedIncrease = 2.0,
        walkingSpeed = 1.25
    )

    /**
     * Prepare the creation of a [TrackConfig] from the given [mockHandler]. This means that the factory function
     * from [TrackConfig]'s companion object has to be mocked to return [trackConf].
     */
    fun prepareTrackConfigFromPreferences(mockHandler: PreferencesHandler, trackConf: TrackConfig = DEFAULT_TRACK_CONFIG) {
        mockkObject(TrackConfig)
        every { TrackConfig.fromPreferences(mockHandler) } returns trackConf
    }

    /**
     * Prepare the creation of a [TrackServerConfig] from the given [mockHandler]. So the factory function from
     * [TrackServerConfig]'s companion object is mocked to return this [serverConfig].
     */
    fun prepareTrackServerConfigFromPreferences(
        mockHandler: PreferencesHandler,
        serverConfig: TrackServerConfig = DEFAULT_SERVER_CONFIG
    ) {
        mockkObject(TrackServerConfig)
        every { TrackServerConfig.fromPreferences(mockHandler) } returns serverConfig
    }

    /**
     * Create a [ConfigManager] mock and prepare it to return the given [trackConfig], [serverConfig], and
     * [receiverConfig].
     */
    fun prepareConfigManager(
        context: Context,
        trackConfig: TrackConfig = DEFAULT_TRACK_CONFIG,
        serverConfig: TrackServerConfig = DEFAULT_SERVER_CONFIG,
        receiverConfig: ReceiverConfig = ReceiverConfig.DEFAULT
    ): ConfigManager {
        val configManager = mockk<ConfigManager>()
        mockkObject(ConfigManager)

        every { ConfigManager.getInstance() } returns configManager
        every { configManager.trackConfig(context) } returns trackConfig
        every { configManager.serverConfig(context) } returns serverConfig
        every { configManager.receiverConfig(context) } returns receiverConfig
        return configManager
    }

    /**
     * Create a [TrackStorage] configured with a [PreferencesHandler] mock obtained via [preparePreferences] passing
     * in [svrConf], [trackConf], and [trackingEnabled].
     */
    fun prepareTrackStorage(
        svrConf: TrackServerConfig = DEFAULT_SERVER_CONFIG,
        trackConf: TrackConfig = DEFAULT_TRACK_CONFIG,
        trackingEnabled: Boolean = true
    ): TrackStorage = mockk<TrackStorage>().apply {
        val handler = preparePreferences(svrConf, trackConf)
        every { isTrackingEnabled() } returns trackingEnabled
        every { checkCount() } returns 42
        every { updateCount() } returns 11
        every { errorCount() } returns 1
        every { totalDistance() } returns 100
        every { preferencesHandler } returns handler
    }

    /**
     * Transform this [TrackServerConfig] to a [ServerConfig].
     */
    fun TrackServerConfig.asServerConfig(): ServerConfig = ServerConfig(serverUri, basePath, user, password)

    /**
     * Create a [PreferencesHandler] mock that is prepared to expect invocations to construct the given
     * [server configuration][svrConf] and [track configuration][trackConf].
     */
    private fun preparePreferences(
        svrConf: TrackServerConfig = DEFAULT_SERVER_CONFIG,
        trackConf: TrackConfig = DEFAULT_TRACK_CONFIG
    ): PreferencesHandler {
        val handler = mockk<PreferencesHandler>()

        prepareTrackConfigFromPreferences(handler, trackConf)
        prepareTrackServerConfigFromPreferences(handler, svrConf)

        return handler
    }
}
