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

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.oheger.locationteller.R

import com.github.oheger.locationteller.map.LocationFileState
import com.github.oheger.locationteller.map.MarkerFactory
import com.github.oheger.locationteller.ui.state.ReceiverViewModel
import com.github.oheger.locationteller.ui.state.ReceiverViewModelImpl

import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MarkerInfoWindow
import com.google.maps.android.compose.MarkerState

internal const val TAG_REC_MAP_VIEW = "rec_map_view"
internal const val TAG_REC_UPDATE_INDICATOR = "rec_update_indicator"
internal const val TAG_REC_UPDATE_STATUS_TEXT = "rec_update_status_text"
internal const val TAG_REC_LOCATION_STATUS_TEXT = "rec_location_status_text"

/**
 * Generate the whole receiver UI. This is the entry point into this UI.
 */
@Composable
fun ReceiverUi(modifier: Modifier = Modifier, model: ReceiverViewModelImpl = viewModel()) {
    ReceiverView(model = model, modifier = modifier)
}

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
            StatusLine(
                updateInProgress = model.isUpdating(),
                countDown = model.secondsToNextUpdateString,
                numberOfLocations = model.locationFileState.files.size,
                recentLocationTime = model.recentLocationTime(),
                modifier
            )
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

/**
 * Generate the whole status line, consisting of information about an ongoing or scheduled update (defined by
 * [updateInProgress] and [countDown]), and about the locations retrieved from the server (defined by
 * [numberOfLocations] and [recentLocationTime]).
 */
@Composable
internal fun StatusLine(
    updateInProgress: Boolean,
    countDown: String,
    numberOfLocations: Int,
    recentLocationTime: String?,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(5.dp)
    ) {
        Box(modifier = modifier) {
            UpdateStatus(updateInProgress = updateInProgress, countDown = countDown, modifier = modifier)
        }
        Spacer(modifier = modifier.weight(1.0f))
        Box(modifier = modifier) {
            LocationStatus(
                numberOfLocations = numberOfLocations,
                recentLocationTime = recentLocationTime,
                modifier = modifier
            )
        }
    }
}

/**
 * Generate the part of the status line that displays the update status. If [updateInProgress] is *true*, a progress
 * indicator is displayed. Otherwise, show the time to the next update based on [countDown].
 */
@Composable
internal fun UpdateStatus(updateInProgress: Boolean, countDown: String, modifier: Modifier = Modifier) {
    val statusText = if (updateInProgress) stringResource(id = R.string.map_status_updating)
    else stringResource(id = R.string.map_status_update_scheduled, countDown)

    Row(modifier = modifier) {
        if (updateInProgress) {
            val infiniteTransition = rememberInfiniteTransition()
            val progressAnimationValue by infiniteTransition.animateFloat(
                initialValue = 0.0f,
                targetValue = 1.0f,
                animationSpec = infiniteRepeatable(animation = tween(900))
            )
            CircularProgressIndicator(
                progress = progressAnimationValue,
                modifier = modifier
                    .size(16.dp)
                    .testTag(TAG_REC_UPDATE_INDICATOR)
            )
        }

        Text(
            text = statusText, modifier = modifier.testTag(TAG_REC_UPDATE_STATUS_TEXT)
        )
    }
}

/**
 * Generate the part of the status line that displays information about the number and age of the locations obtained
 * from the server based on the passed in [numberOfLocations] and [recentLocationTime]. If the latter is *null*,
 * assume that no locations are available and generate a corresponding message.
 */
@Composable
internal fun LocationStatus(numberOfLocations: Int, recentLocationTime: String?, modifier: Modifier = Modifier) {
    val statusText = recentLocationTime?.let { time ->
        stringResource(id = R.string.map_status, numberOfLocations, time)
    } ?: stringResource(id = R.string.map_status_empty)

    Text(
        text = statusText,
        modifier = modifier.testTag(TAG_REC_LOCATION_STATUS_TEXT)
    )
}

// Note: This preview cannot be displayed in Android Studio.
@Preview(showBackground = true)
@Composable
fun ReceiverPreview() {
    val model = PreviewReceiverViewModel()

    ReceiverView(model = model)
}
