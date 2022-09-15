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
import android.location.Location
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
import com.github.oheger.locationteller.map.MarkerData
import com.github.oheger.locationteller.map.MarkerFactory
import com.github.oheger.locationteller.map.RangeTimeDeltaAlphaCalculator
import com.github.oheger.locationteller.server.LocationData
import com.github.oheger.locationteller.server.ServerConfig
import com.github.oheger.locationteller.server.TrackService

import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.maps.android.compose.CameraPositionState

import kotlin.math.round

/**
 * An enumeration class defining the actions the user can trigger on the receiver UI. Actions typically cause some kind
 * of update of the map view and the receiver state.
 */
enum class ReceiverAction {
    /** Triggers an immediate update of the receiver state from the server. */
    UPDATE,

    /** Moves the recent position to the center of the map. */
    CENTER_RECENT_POSITION,

    /**
     * Changes position and zoom level of the map view, so that the whole tracked area can be displayed.
     */
    ZOOM_TRACKED_AREA,

    /** Queries the position of this device, so that it can be displayed on the map. */
    UPDATE_OWN_POSITION
}

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

    /**
     * A list with [MarkerOptions] corresponding to the positions in the managed [LocationFileState]. Whenever a new
     * [LocationFileState] is set, corresponding markers are created using [markerFactory]. The UI can then directly
     * use these markers to render the map view.
     */
    val markers: List<MarkerOptions>

    /** Stores the [MarkerOptions] representing the location of this device if it is known. */
    val ownLocation: MarkerOptions?

    /** The number of seconds until the next update from the server. */
    val secondsToNextUpdate: Int

    /** A formatted string for the time until the next update from the server. */
    val secondsToNextUpdateString: String

    /**
     * Set the current [ReceiverConfig] to [newConfig]. This causes updates on some objects managed by this instance.
     */
    fun updateReceiverConfig(newConfig: ReceiverConfig)

    /**
     * Generate a formatted text with the time (or age) of the recent location retrieved from the server. Return
     * *null* if there are no locations available.
     */
    fun recentLocationTime(): String?

    /**
     * Return a flag whether currently an update of the locations from the server is in progress. This information can
     * be used to display an indicator in the UI.
     */
    fun isUpdating(): Boolean = secondsToNextUpdate == 0

    /**
     * Handle the given [action]. This function is called as a reaction on a user action on the receiver UI. The
     * action causes an update on the receiver state.
     */
    fun onAction(action: ReceiverAction)

    /**
     * Create a list of [MarkerOptions] for the positions stored in this [LocationFileState] using the [MarkerFactory]
     * managed by this object. This function can be used by concrete implementations to provide the markers
     * property.
     */
    fun LocationFileState.createMarkers(): List<MarkerOptions> {
        val recentMarker = recentMarker()
        val markerData = files.mapNotNull { markerData[it] }

        return markerData.withIndex()
            .map { (index, data) ->
                markerFactory.createMarker(
                    data,
                    recentMarker = data == recentMarker,
                    zIndex = index.toFloat()
                )
            }
    }
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
     * Stores a list with [MarkerOptions] for the positions contained in [currentLocationFileState]. This property is
     * updated automatically when a new [LocationFileState] is set or the [MarkerFactory] changes.
     */
    private val currentMarkers: MutableState<List<MarkerOptions>>

    /** Stores [MarkerOptions] for the marker representing the own location. */
    private val currentOwnLocation: MutableState<MarkerOptions?>

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

    /** Stores a [MarkerData] object to represent the own location if it is known. */
    private var ownLocationMarker: MarkerData? = null

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
        currentMarkers = mutableStateOf(emptyList())
        currentOwnLocation = mutableStateOf(null)

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

    override val markers: List<MarkerOptions>
        get() = currentMarkers.value

    override val ownLocation: MarkerOptions?
        get() = currentOwnLocation.value

    override val secondsToNextUpdate: Int
        get() = currentSecondsToNextUpdate.value

    override val secondsToNextUpdateString: String
        get() = currentSecondsToNextUpdateString.value

    override fun updateReceiverConfig(newConfig: ReceiverConfig) {
        ConfigManager.getInstance().updateReceiverConfig(getApplication(), newConfig)
    }

    override fun recentLocationTime(): String? {
        return locationFileState.recentMarker()?.locationData?.time?.currentTime?.let { time ->
            markerFactory.deltaFormatter.formatTimeDelta(markerFactory.timeService.currentTime().currentTime - time)
        }
    }

    override fun onAction(action: ReceiverAction) {
        Log.i(TAG, "onAction($action)")

        when (action) {
            ReceiverAction.UPDATE -> mapStateUpdater?.update()
            ReceiverAction.CENTER_RECENT_POSITION -> receiverCameraState.centerRecentMarker(locationFileState)
            ReceiverAction.ZOOM_TRACKED_AREA -> receiverCameraState.zoomToAllMarkers(locationFileState)
            ReceiverAction.UPDATE_OWN_POSITION -> mapStateUpdater?.queryLocation(getApplication())
        }
    }

    override fun onCleared() {
        Log.i(TAG, "Clearing ReceiverViewModel.")
        val configManager = ConfigManager.getInstance()
        configManager.removeReceiverConfigChangeListener(this::receiverConfigChanged)
        configManager.removeServerConfigChangeListener(this::serverConfigChanged)

        mapStateUpdater?.close()
    }

    /**
     * The notification function called by [MapStateUpdater] when new data has been retrieved from the server.
     */
    internal fun locationFileStateChanged(newState: LocationFileState) {
        updateCamera(newState)
        currentLocationFileState.value = newState
        currentMarkers.value = newState.createMarkers()
        updateOwnLocationMarkerOptions()
    }

    /**
     * The callback function called by [MapStateUpdater] when the own location has been retrieved. It computes the
     * [MarkerData] for the given [location] and eventually updates the [currentOwnLocation] state.
     */
    internal fun updateOwnLocation(location: Location?) {
        ownLocationMarker = location?.let { loc ->
            MarkerData(
                LocationData(loc.latitude, loc.longitude, markerFactory.timeService.currentTime()),
                LatLng(loc.latitude, loc.longitude)
            ).also {
                receiverCameraState.centerMarker(it)
            }
        }

        updateOwnLocationMarkerOptions()
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
            this::onCountDown,
            this::updateOwnLocation
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
        currentMarkers.value = locationFileState.createMarkers()
    }

    /**
     * The notification function called by the [ConfigManager] when the [TrackServerConfig] was updated.
     */
    private fun serverConfigChanged(config: TrackServerConfig) {
        currentServerConfig = config
        mapStateLoader = null
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

    /**
     * Generate the [MarkerOptions] for the own location based on [ownLocationMarker]. This function is called for
     * every change of the own location or the location state, to make sure that the [MarkerOptions] are always
     * up-to-date.
     */
    private fun updateOwnLocationMarkerOptions() {
        currentOwnLocation.value = ownLocationMarker?.let { markerData ->
            markerFactory.createMarker(
                markerData,
                recentMarker = false,
                zIndex = (locationFileState.files.size + 1).toFloat(),
                text = generateDistanceString(markerData),
                color = BitmapDescriptorFactory.HUE_GREEN
            )
        }
    }

    /**
     * Generate a string describing the distance between the given [ownLocation] and the recent location retrieved
     * from the server. Return *null* if no location data is available.
     */
    private fun generateDistanceString(ownLocation: MarkerData): String? =
        locationFileState.recentMarker()?.let {
            val res = FloatArray(1)
            Location.distanceBetween(
                it.locationData.latitude,
                it.locationData.longitude,
                ownLocation.locationData.latitude,
                ownLocation.locationData.longitude,
                res
            )

            getApplication<Application>().getString(R.string.map_distance_own, round(res[0]).toInt())
        }
}
