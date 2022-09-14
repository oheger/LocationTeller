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
package com.github.oheger.locationteller.ui

import com.github.oheger.locationteller.config.ReceiverConfig
import com.github.oheger.locationteller.config.TrackConfig
import com.github.oheger.locationteller.config.TrackServerConfig
import com.github.oheger.locationteller.duration.TimeDeltaFormatter
import com.github.oheger.locationteller.map.DisabledFadeOutAlphaCalculator
import com.github.oheger.locationteller.map.LocationFileState
import com.github.oheger.locationteller.map.MarkerData
import com.github.oheger.locationteller.map.MarkerFactory
import com.github.oheger.locationteller.server.LocationData
import com.github.oheger.locationteller.server.TimeData
import com.github.oheger.locationteller.ui.state.ReceiverAction
import com.github.oheger.locationteller.ui.state.ReceiverViewModel
import com.github.oheger.locationteller.ui.state.TrackStatsState
import com.github.oheger.locationteller.ui.state.TrackViewModel

import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.maps.android.compose.CameraPositionState

/**
 * A dummy implementation of [TrackViewModel] that can be used in preview functions.
 */
internal data class PreviewTrackViewModel(
    override val trackStatistics: TrackStatsState = TrackStatsState(),
    override var trackingEnabled: Boolean = false,
    override val trackConfig: TrackConfig = TrackConfig.DEFAULT,
    override val serverConfig: TrackServerConfig = TEST_SERVER_CONFIG
) : TrackViewModel {
    override fun updateTrackingState(enabled: Boolean) {
        trackingEnabled = enabled
    }

    override fun updateTrackConfig(config: TrackConfig) {}
    override fun updateServerConfig(config: TrackServerConfig) {}
}

/**
 * A dummy implementation of [ReceiverViewModel] that can be used in preview functions.
 */
internal data class PreviewReceiverViewModel(
    override val receiverConfig: ReceiverConfig = ReceiverConfig.DEFAULT,
    override val markerFactory: MarkerFactory = MARKER_FACTORY,
    override val cameraPositionState: CameraPositionState = CAMERA_STATE,
    override val locationFileState: LocationFileState = createLocationFileState(),
    override val secondsToNextUpdate: Int = 59,
    override val secondsToNextUpdateString: String = "59 s"

) : ReceiverViewModel {
    override val markers: List<MarkerOptions>
        get() = locationFileState.createMarkers()

    override fun updateReceiverConfig(newConfig: ReceiverConfig) {}

    override fun recentLocationTime(): String = "42 s"
    override fun onAction(action: ReceiverAction) {}
}

/** A test server configuration used by the model for the preview. */
private val TEST_SERVER_CONFIG = TrackServerConfig(
    serverUri = "https://track.example.org",
    basePath = "/my/tracks",
    user = "test-user",
    password = "test.password"
)

/** A test MarkerFactory. */
private val MARKER_FACTORY = MarkerFactory(
    deltaFormatter = TimeDeltaFormatter("s", "m", "h", "d"),
    alphaCalculator = DisabledFadeOutAlphaCalculator
)

/** Test camera state. */
private val CAMERA_STATE = CameraPositionState(
    position = CameraPosition(LatLng(47.9, 8.75), 15f, 1f, 1f)
)

/**
 * Create a [LocationFileState] object with some test locations.
 */
private fun createLocationFileState(): LocationFileState {
    val locations = listOf(
        LocationData(latitude = 47.125, longitude = 8.5, time = TimeData(1662543954000L)),
        LocationData(latitude = 47.5, longitude = 8.1, time = TimeData(1662536816000L)),
        LocationData(latitude = 47.62, longitude = 8.65, time = TimeData(1662536820000L)),
        LocationData(latitude = 47.985, longitude = 8.2, time = TimeData(1662537230000L)),
        LocationData(latitude = 47.9, longitude = 8.75, time = TimeData(1662537520000L))
    )
    val markerData = locations.map { locData ->
        val position = LatLng(locData.latitude, locData.longitude)
        MarkerData(locData, position)
    }
    val markerMap = markerData.associateBy { data ->
        data.locationData.stringRepresentation()
    }

    return LocationFileState(markerMap.keys.toList(), markerMap)
}
