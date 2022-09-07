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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview

import com.github.oheger.locationteller.map.LocationFileState
import com.github.oheger.locationteller.map.MarkerFactory
import com.github.oheger.locationteller.ui.state.ReceiverViewModel

import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MarkerInfoWindow
import com.google.maps.android.compose.MarkerState

internal const val TAG_REC_MAP_VIEW = "rec_map_view"

/**
 * Generate the view for the receiver part of the application based on the given [model].
 */
@Composable
fun ReceiverView(model: ReceiverViewModel, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Box(modifier = modifier.weight(1.0f)) {
            MapView(
                locationFileState = model.locationFileState,
                markerFactory = model.markerFactory,
                cameraState = model.cameraPositionState,
                modifier = modifier.testTag(TAG_REC_MAP_VIEW)
            )
        }
        Box(modifier = modifier) {
            Text(text = "The status line")
        }
    }
}

/**
 * Render the map view as part of the receiver UI. Use the given [markerFactory] to display markers on the map based on
 * the given [locationFileState]. Set the position and zoom level of the map as defined by the given [cameraState].
 */
@Composable
fun MapView(
    locationFileState: LocationFileState,
    markerFactory: MarkerFactory,
    cameraState: CameraPositionState,
    modifier: Modifier = Modifier
) {
    GoogleMap(
        modifier = modifier.fillMaxSize(),
        cameraPositionState = cameraState
    ) {
        val recentMarker = locationFileState.recentMarker()
        val markerData = locationFileState.files.mapNotNull { locationFileState.markerData[it] }

        markerData.withIndex()
            .forEach { (index, data) ->
                val options = markerFactory.createMarker(
                    data,
                    recentMarker = data == recentMarker,
                    zIndex = index.toFloat()
                )
                MarkerInfoWindow(
                    state = MarkerState(position = options.position),
                    alpha = options.alpha,
                    icon = options.icon,
                    zIndex = options.zIndex,
                    title = options.title,
                    snippet = options.snippet
                )
            }
    }
}

// Note: This preview cannot be displayed in Android Studio.
@Preview(showBackground = true)
@Composable
fun ReceiverPreview() {
    val model = PreviewReceiverViewModel()

    ReceiverView(model = model)
}
