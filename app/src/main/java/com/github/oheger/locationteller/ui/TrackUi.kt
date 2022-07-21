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

import android.Manifest

import androidx.compose.foundation.layout.*
import androidx.compose.material.Button
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

import com.github.oheger.locationteller.R
import com.github.oheger.locationteller.ui.state.TrackStatsState
import com.github.oheger.locationteller.ui.state.TrackViewModel
import com.github.oheger.locationteller.ui.state.TrackViewModelImpl

import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.rememberPermissionState

internal const val TAG_TRACK_START = "tag_track_start"
internal const val TAG_TRACK_END = "tag_track_end"
internal const val TAG_TRACK_TIME = "tag_track_time"
internal const val TAG_TRACK_DIST = "tag_track_dist"
internal const val TAG_TRACK_LAST_DIST = "tag_track_last_dist"
internal const val TAG_TRACK_SPEED = "tag_track_speed"
internal const val TAG_TRACK_CHECKS = "tag_track_checks"
internal const val TAG_TRACK_LAST_CHECK = "tag_track_last_check"
internal const val TAG_TRACK_UPDATES = "tag_track_updates"
internal const val TAG_TRACK_LAST_UPDATE = "tag_track_last_update"
internal const val TAG_TRACK_ERRORS = "tag_track_errors"
internal const val TAG_TRACK_LAST_ERROR = "tag_track_last_error"
internal const val TAG_TRACK_ENABLED_SWITCH = "tag_track_enabled_switch"
internal const val TAG_TRACK_PERM_BUTTON = "tag_track_perm_button"

/**
 * Generate the test tag of for the label element associated with the value defined by [tag].
 */
internal fun labelTag(tag: String): String = "${tag}_label"

/**
 * Generate the whole tracking UI. This is the entry point into this UI.
 */
@Composable
fun TrackUi(model: TrackViewModelImpl = viewModel(), modifier: Modifier = Modifier) {
    val locationPermissionState = rememberPermissionState(permission = Manifest.permission.ACCESS_FINE_LOCATION)

    TrackView(model = model, locationPermissionState = locationPermissionState, modifier = modifier)
}

/**
 * Generate the tracking UI based on the provided [model] and [locationPermissionState].
 */
@Composable
fun TrackView(model: TrackViewModel, locationPermissionState: PermissionState, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        TrackEnabledSwitch(
            enabled = model.trackingEnabled,
            onStateChange = { state -> model.updateTrackingState(state) },
            locationPermissionState = locationPermissionState,
            modifier = modifier
        )
        TrackStats(stats = model.trackStatistics, modifier = modifier)
    }
}

/**
 * Display the switch to enable or disable tracking using the current [enabled] state and the [onStateChange]
 * function to report changes.
 */
@Composable
fun TrackEnabledSwitch(
    enabled: Boolean,
    onStateChange: (Boolean) -> Unit,
    locationPermissionState: PermissionState,
    modifier: Modifier = Modifier
) {
    when (locationPermissionState.status) {
        PermissionStatus.Granted ->
            Row(
                modifier = modifier
                    .padding(all = 2.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Switch(
                    checked = enabled,
                    onCheckedChange = onStateChange,
                    modifier = modifier
                        .testTag(TAG_TRACK_ENABLED_SWITCH)
                )
                Text(text = stringResource(id = R.string.lab_track_enabled), modifier = modifier)
            }

        is PermissionStatus.Denied -> {
            Column {
                Text(text = stringResource(id = R.string.perm_location_rationale))
                Button(
                    onClick = { locationPermissionState.launchPermissionRequest() },
                    modifier = modifier.testTag(TAG_TRACK_PERM_BUTTON)
                ) {
                    Text(text = "Request permission")
                }
            }
        }
    }
}

/**
 * Generate the tracking statistics screen based on [stats]. Output a [StatsLine] for each statistics data item.
 */
@Composable
fun TrackStats(stats: TrackStatsState, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        StatsLine(
            labelRes = R.string.stats_tracking_started,
            value = stats.startTime,
            tag = TAG_TRACK_START,
            modifier = modifier
        )
        StatsLine(
            labelRes = R.string.stats_tracking_stopped,
            value = stats.endTime,
            tag = TAG_TRACK_END,
            modifier = modifier
        )
        StatsLine(
            labelRes = R.string.stats_tracking_time,
            value = stats.elapsedTime,
            tag = TAG_TRACK_TIME,
            modifier = modifier
        )
        StatsLine(
            labelRes = R.string.stats_tracking_total_distance,
            value = stats.totalDistance,
            tag = TAG_TRACK_DIST,
            modifier = modifier
        )
        StatsLine(
            labelRes = R.string.stats_tracking_speed,
            value = stats.averageSpeed,
            tag = TAG_TRACK_SPEED,
            modifier = modifier
        )
        StatsLine(
            labelRes = R.string.stats_tracking_last_distance,
            value = stats.lastDistance,
            tag = TAG_TRACK_LAST_DIST,
            modifier = modifier
        )
        StatsLine(
            labelRes = R.string.stats_tracking_check_count,
            value = stats.numberOfChecks,
            tag = TAG_TRACK_CHECKS,
            modifier = modifier
        )
        StatsLine(
            labelRes = R.string.stats_tracking_last_check,
            value = stats.lastCheckTime,
            tag = TAG_TRACK_LAST_CHECK,
            modifier = modifier
        )
        StatsLine(
            labelRes = R.string.stats_tracking_update_count,
            value = stats.numberOfUpdates,
            tag = TAG_TRACK_UPDATES,
            modifier = modifier
        )
        StatsLine(
            labelRes = R.string.stats_tracking_last_update,
            value = stats.lastUpdateTime,
            tag = TAG_TRACK_LAST_UPDATE,
            modifier = modifier
        )
        StatsLine(
            labelRes = R.string.stats_tracking_error_count,
            value = stats.numberOfErrors,
            tag = TAG_TRACK_ERRORS,
            modifier = modifier
        )
        StatsLine(
            labelRes = R.string.stats_tracking_last_error,
            value = stats.lastErrorTime,
            tag = TAG_TRACK_LAST_ERROR,
            modifier = modifier
        )
    }
}

/**
 * Generate one line of the tracking statistics screen, showing one statistics item. The item consists of a
 * [label][labelRes] and an optional [value].
 */
@Composable
fun StatsLine(labelRes: Int, value: String?, tag: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .padding(all = 2.dp)
            .fillMaxWidth()
    ) {
        Text(text = stringResource(id = labelRes), modifier = modifier.testTag(labelTag(tag)))
        if (value != null) {
            Spacer(modifier = modifier.width(width = 4.dp))
            Text(
                text = value,
                textAlign = TextAlign.Right,
                modifier = modifier
                    .fillMaxWidth()
                    .testTag(tag)
            )
        }
    }
}

/**
 * A dummy implementation of [TrackViewModel] that can be used in preview functions.
 */
data class PreviewTrackViewModel(
    override val trackStatistics: TrackStatsState,
    override var trackingEnabled: Boolean = false
) : TrackViewModel {
    override fun updateTrackingState(enabled: Boolean) {
        trackingEnabled = enabled
    }
}

@Preview(showBackground = true)
@Composable
fun TrackViewPreview(
    @PreviewParameter(PermissionStateProvider::class)
    permissionState: PermissionState
) {
    val state = TrackStatsState().apply {
        startTime = "2022-06-25 16:45:55"
        elapsedTime = "16:12"
        totalDistance = "2500"
        averageSpeed = "3.5"
        lastDistance = "142"
        numberOfChecks = "28"
        lastCheckTime = "17:01:31"
        numberOfUpdates = "20"
        lastUpdateTime = "17:01:31"
    }
    val model = PreviewTrackViewModel(state)

    TrackView(model = model, locationPermissionState = permissionState)
}

/**
 * A [PreviewParameterProvider] implementation that provides all possible permission states for the location
 * permission. So the effect of the permission state on the UI can be seen.
 */
class PermissionStateProvider : PreviewParameterProvider<PermissionState> {
    override val values: Sequence<PermissionState> = sequenceOf(
        PermissionStatus.Granted,
        PermissionStatus.Denied(shouldShowRationale = false),
        PermissionStatus.Denied(shouldShowRationale = true)
    ).map(this::createLocationPermissionState)

    /**
     * Create a [PermissionState] stub object that reports the given [status].
     */
    private fun createLocationPermissionState(status: PermissionStatus): PermissionState =
        object : PermissionState {
            override val permission = Manifest.permission.ACCESS_FINE_LOCATION

            override val status = status

            override fun launchPermissionRequest() {}
        }
}
