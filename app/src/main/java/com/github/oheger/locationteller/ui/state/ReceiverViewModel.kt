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
package com.github.oheger.locationteller.ui.state

import android.app.Application
import android.util.Log

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel

import com.github.oheger.locationteller.R
import com.github.oheger.locationteller.config.ConfigManager
import com.github.oheger.locationteller.config.ReceiverConfig
import com.github.oheger.locationteller.config.TrackServerConfig
import com.github.oheger.locationteller.duration.DurationModel
import com.github.oheger.locationteller.duration.TimeDeltaFormatter
import com.github.oheger.locationteller.map.AlphaRange
import com.github.oheger.locationteller.map.DisabledFadeOutAlphaCalculator
import com.github.oheger.locationteller.map.LocationFileState
import com.github.oheger.locationteller.map.MapStateLoader
import com.github.oheger.locationteller.map.MapStateUpdater
import com.github.oheger.locationteller.map.MarkerFactory
import com.github.oheger.locationteller.map.RangeTimeDeltaAlphaCalculator
import com.github.oheger.locationteller.server.ServerConfig
import com.github.oheger.locationteller.server.TrackService

import com.google.maps.android.compose.CameraPositionState

/**
 * Definition of an interface that defines the contract of the view model used for the receiver part of the application
 * (the map view showing the recorded locations).
 */
interface ReceiverViewModel {
    /** The configuration with settings related to the receiver part. */
    val receiverConfig: ReceiverConfig

    /** The factory for the markers to be added to the map. */
    val markerFactory: MarkerFactory

    /** The current position state of the camera. */
    val cameraPositionState: CameraPositionState

    /** The object containing the position files loaded from the server. */
    val locationFileState: LocationFileState

    /** The number of seconds until the next update from the server. */
    val secondsToNextUpdate: Int

    /** A formatted string for the time until the next update from the server. */
    val secondsToNextUpdateString: String

    /**
     * Set the current [ReceiverConfig] to [newConfig]. This causes updates on some objects managed by this instance.
     */
    fun updateReceiverConfig(newConfig: ReceiverConfig)

    /**
     * Return a flag whether currently an update of the locations from the server is in progress. This information can
     * be used to display an indicator in the UI.
     */
    fun isUpdating(): Boolean = secondsToNextUpdate == 0
}

/**
 * The productive implementation of [ReceiverViewModel].
 */
class ReceiverViewModelImpl(application: Application) : AndroidViewModel(application), ReceiverViewModel {
    companion object {
        /** The alpha calculator for fast and strong fading. */
        val CALCULATOR_FAST_STRONG = RangeTimeDeltaAlphaCalculator(
            listOf(
                AlphaRange(1f, 0.55f, DurationModel.Component.HOUR.toMillis()),
                AlphaRange(0.5f, 0.2f, DurationModel.Component.DAY.toMillis())
            ), 0.1f
        )

        /** The alpha calculator for slow and strong fading. */
        val CALCULATOR_SLOW_STRONG = RangeTimeDeltaAlphaCalculator(
            listOf(
                AlphaRange(1f, 0.55f, DurationModel.Component.DAY.toMillis()),
                AlphaRange(0.5f, 0.2f, 7 * DurationModel.Component.DAY.toMillis())
            ), 0.1f
        )

        /** The alpha calculator for fast normal fading. */
        val CALCULATOR_FAST = RangeTimeDeltaAlphaCalculator(
            listOf(
                AlphaRange(1f, 0.75f, DurationModel.Component.HOUR.toMillis()),
                AlphaRange(0.7f, 0.5f, DurationModel.Component.DAY.toMillis())
            ), 0.4f
        )

        /** The alpha calculator for slow normal fading. */
        val CALCULATOR_SLOW = RangeTimeDeltaAlphaCalculator(
            listOf(
                AlphaRange(1f, 0.75f, DurationModel.Component.DAY.toMillis()),
                AlphaRange(0.7f, 0.5f, 7 * DurationModel.Component.DAY.toMillis())
            ), 0.4f
        )

        /** Tag for logging. */
        private const val TAG = "ReceiverViewModel"
    }

    /** Stores the formatter for time deltas. */
    private val timeDeltaFormatter = createTimeDeltaFormatter(application)

    /**
     * Holds the current [ReceiverConfig]. The configuration gets updated when the user changes settings.
     */
    private val currentReceiverConfig: MutableState<ReceiverConfig>

    /**
     * Stores the current [MarkerFactory]. Everytime the configuration changes, a new factory is created that is
     * configured accordingly.
     */
    private val currentMarkerFactory: MutableState<MarkerFactory>

    /**
     * Stores the object with location files obtained from the server. This property gets updated automatically from
     * the [MapStateUpdater].
     */
    private val currentLocationFileState: MutableState<LocationFileState>

    /**
     * Stores the count-down value when the next update from the server is scheduled. This property gets updated
     * automatically from the [MapStateUpdater].
     */
    private val currentSecondsToNextUpdate = mutableStateOf(0)

    /**
     * Stores a formatted string with the count-down value when the next update from the server is scheduled. This
     * property is updated together with [currentSecondsToNextUpdate].
     */
    private val currentSecondsToNextUpdateString = mutableStateOf("")

    /** The helper object managing the camera state. */
    private val receiverCameraState = ReceiverCameraState.create()

    /**
     * The object responsible for updating the map state. It depends on the current receiver configuration and is
     * recreated whenever this configuration changes.
     */
    private var mapStateUpdater: MapStateUpdater? = null

    /** Stores the current server configuration. */
    private var currentServerConfig = TrackServerConfig.EMPTY

    /**
     * The object responsible for loading the current positions from the server. It depends on the server configuration
     * and is reset whenever this configuration changes.
     */
    private var mapStateLoader: MapStateLoader? = null

    /** A flag to keep track whether the camera has been initialized. */
    private var cameraInitialized = false

    init {
        Log.i(TAG, "Creating new instance of ReceiverViewModel.")

        val configManager = ConfigManager.getInstance()
        currentReceiverConfig = mutableStateOf(ReceiverConfig.DEFAULT)
        configManager.addReceiverConfigChangeListener(this::receiverConfigChanged)
        configManager.addServerConfigChangeListener(this::serverConfigChanged)

        currentMarkerFactory = mutableStateOf(createMarkerFactory())
        currentLocationFileState = mutableStateOf(LocationFileState.EMPTY)

        receiverConfigChanged(configManager.receiverConfig(application))
        serverConfigChanged(configManager.serverConfig(application))
    }

    override val receiverConfig: ReceiverConfig
        get() = currentReceiverConfig.value

    override val markerFactory: MarkerFactory
        get() = currentMarkerFactory.value

    override val cameraPositionState: CameraPositionState
        get() = receiverCameraState.cameraPositionState

    override val locationFileState: LocationFileState
        get() = currentLocationFileState.value

    override val secondsToNextUpdate: Int
        get() = currentSecondsToNextUpdate.value

    override val secondsToNextUpdateString: String
        get() = currentSecondsToNextUpdateString.value

    override fun updateReceiverConfig(newConfig: ReceiverConfig) {
        ConfigManager.getInstance().updateReceiverConfig(getApplication(), newConfig)
    }

    override fun onCleared() {
        Log.i(TAG, "Clearing ReceiverViewModel.")
        val configManager = ConfigManager.getInstance()
        configManager.removeReceiverConfigChangeListener(this::receiverConfigChanged)
        configManager.removeServerConfigChangeListener(this::serverConfigChanged)

        mapStateUpdater?.close()
    }

    /**
     * Create a new [MarkerFactory] based on the current configuration.
     */
    private fun createMarkerFactory(): MarkerFactory {
        val alphaCalculator = with(currentReceiverConfig.value) {
            if (!fadeOutEnabled) DisabledFadeOutAlphaCalculator
            else if (strongFadeOut) {
                if (fastFadeOut) CALCULATOR_FAST_STRONG
                else CALCULATOR_SLOW_STRONG
            } else {
                if (fastFadeOut) CALCULATOR_FAST
                else CALCULATOR_SLOW
            }
        }

        return MarkerFactory(timeDeltaFormatter, alphaCalculator)
    }

    /**
     * Create the [TimeDeltaFormatter] that is used by the [MarkerFactory] managed by this model.
     */
    private fun createTimeDeltaFormatter(application: Application): TimeDeltaFormatter =
        TimeDeltaFormatter(
            unitSec = application.getString(R.string.time_secs),
            unitMin = application.getString(R.string.time_minutes),
            unitHour = application.getString(R.string.time_hours),
            unitDay = application.getString(R.string.time_days)
        )

    /**
     * Create a new [MapStateUpdater] instance and configure it with the given [updateInterval].
     */
    private fun createMapStateUpdater(updateInterval: Int): MapStateUpdater =
        MapStateUpdater.create(
            updateInterval,
            this::getOrCreateMapStateLoader,
            this::locationFileStateChanged,
            this::onCountDown
        )

    /**
     * Create a [MapStateLoader] instance based on the current server configuration.
     */
    private fun createMapStateLoader(): MapStateLoader {
        val serverConfig = ServerConfig(
            serverUri = currentServerConfig.serverUri,
            basePath = currentServerConfig.basePath,
            user = currentServerConfig.user,
            password = currentServerConfig.password
        )
        val trackService = TrackService.create(serverConfig)

        return MapStateLoader(trackService)
    }

    /**
     * Return the current [MapStateLoader] instance. If necessary, create a new instance now.
     */
    private fun getOrCreateMapStateLoader(): MapStateLoader =
        mapStateLoader ?: createMapStateLoader().also { mapStateLoader = it }

    /**
     * The notification function called by the [ConfigManager] when the [ReceiverConfig] was updated.
     */
    private fun receiverConfigChanged(config: ReceiverConfig) {
        if (mapStateUpdater == null || config.updateInterval != receiverConfig.updateInterval) {
            mapStateUpdater?.close()
            mapStateUpdater = createMapStateUpdater(config.updateInterval)
        }

        currentReceiverConfig.value = config
        currentMarkerFactory.value = createMarkerFactory()
    }

    /**
     * The notification function called by the [ConfigManager] when the [TrackServerConfig] was updated.
     */
    private fun serverConfigChanged(config: TrackServerConfig) {
        currentServerConfig = config
        mapStateLoader = null
    }

    /**
     * The notification function called by [MapStateUpdater] when new data has been retrieved from the server.
     */
    private fun locationFileStateChanged(newState: LocationFileState) {
        updateCamera(newState)
        currentLocationFileState.value = newState
    }

    /**
     * Update the camera state on arrival of [a new LocationFileState][newState].
     */
    private fun updateCamera(newState: LocationFileState) {
        if (!cameraInitialized) {
            cameraInitialized = true
            receiverCameraState.zoomToAllMarkers(newState)
        }

        if (receiverConfig.centerNewPosition && locationFileState.recentMarker() != newState.recentMarker()) {
            receiverCameraState.centerRecentMarker(newState)
        }
    }

    /**
     * The notification function called by [MapStateUpdater] on each tick of the count-down timer.
     */
    private fun onCountDown(value: Int) {
        currentSecondsToNextUpdate.value = value
        currentSecondsToNextUpdateString.value = timeDeltaFormatter.formatTimeDelta(value * 1000L)
    }
}
