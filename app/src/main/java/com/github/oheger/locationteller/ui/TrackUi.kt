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
import android.content.res.Configuration

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

import com.github.oheger.locationteller.R
import com.github.oheger.locationteller.ui.state.TrackStatsState
import com.github.oheger.locationteller.ui.state.TrackViewModel
import com.github.oheger.locationteller.ui.state.TrackViewModelImpl
import com.github.oheger.locationteller.ui.theme.LocationTellerTheme

import com.google.accompanist.permissions.PermissionState
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
internal const val TAG_TRACK_RESET_STATS = "tag_track_reset_stats"
internal const val TAG_TRACK_PERM_MESSAGE = "tag_track_perm_message"
internal const val TAG_TRACK_PERM_DETAILS = "tag_track_perm_details"

/**
 * Generate the test tag of for the label element associated with the value defined by [tag].
 */
internal fun labelTag(tag: String): String = "${tag}_label"

/**
 * Generate the whole tracking UI. This is the entry point into this UI.
 */
@Composable
fun TrackUi(
    openDrawer: () -> Unit,
    updateTrackState: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    model: TrackViewModelImpl = viewModel()
) {
    val locationPermissionState = rememberPermissionState(permission = Manifest.permission.ACCESS_FINE_LOCATION)

    TrackView(
        model = model,
        locationPermissionState = locationPermissionState,
        openDrawer = openDrawer,
        updateTrackState = updateTrackState,
        modifier = modifier
    )
}

/**
 * Generate the tracking UI based on the provided [model] and [locationPermissionState]. Call the [openDrawer]
 * function if the menu icon is clicked, and [updateTrackState] if there is a change in the tracking state.
 */
@Composable
fun TrackView(
    model: TrackViewModel,
    locationPermissionState: PermissionState,
    openDrawer: () -> Unit,
    updateTrackState: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val trackStateChanged: (Boolean) -> Unit = { state ->
        model.updateTrackingState(state)
        updateTrackState(state)
    }

    Column(modifier = modifier) {
        TopBar(title = stringResource(id = R.string.trackView), onMenuClicked = openDrawer)
        TrackEnabledSwitch(
            enabled = model.trackingEnabled,
            onStateChange = trackStateChanged,
            locationPermissionState = locationPermissionState,
            modifier = modifier
        )
        TrackStats(stats = model.trackStatistics, model.trackingEnabled, modifier = modifier)
        ResetStatsButton(onClick = model::resetStatistics, modifier = modifier)
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
    val permissionFlags = locationPermissionState.toFlags()

    if (permissionFlags.hasPermission) {
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
    } else {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(all = 10.dp)
        ) {
            Text(
                text = stringResource(id = R.string.perm_location_title),
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = modifier
                    .testTag(TAG_TRACK_PERM_MESSAGE)
                    .padding(bottom = 10.dp)
            )
            if (permissionFlags.shouldShowRationale) {
                Text(
                    text = stringResource(id = R.string.perm_location_rationale),
                    fontSize = 12.sp,
                    modifier = modifier
                        .testTag(TAG_TRACK_PERM_DETAILS)
                        .padding(bottom = 10.dp)
                )
            }
            RequestPermissionButton(locationPermissionState, modifier.align(Alignment.CenterHorizontally))
        }
    }
}

/**
 * Generate the tracking statistics screen based on [stats] and the [tracking enabled state][trackingEnabled]. Output
 * a [StatsLine] for each statistics data item.
 */
@Composable
fun TrackStats(stats: TrackStatsState, trackingEnabled: Boolean, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(all = 10.dp)) {
        StatsLine(
            labelRes = R.string.stats_tracking_started,
            value = stats.startTime,
            showUndefined = trackingEnabled,
            tag = TAG_TRACK_START,
            modifier = modifier
        )
        StatsLine(
            labelRes = R.string.stats_tracking_stopped,
            value = stats.endTime,
            showUndefined = trackingEnabled,
            tag = TAG_TRACK_END,
            modifier = modifier
        )
        StatsLine(
            labelRes = R.string.stats_tracking_time,
            value = stats.elapsedTime,
            showUndefined = trackingEnabled,
            tag = TAG_TRACK_TIME,
            modifier = modifier
        )
        StatsLine(
            labelRes = R.string.stats_tracking_total_distance,
            value = stats.totalDistance,
            showUndefined = trackingEnabled,
            tag = TAG_TRACK_DIST,
            modifier = modifier
        )
        StatsLine(
            labelRes = R.string.stats_tracking_speed,
            value = stats.averageSpeed,
            showUndefined = trackingEnabled,
            tag = TAG_TRACK_SPEED,
            modifier = modifier
        )
        StatsLine(
            labelRes = R.string.stats_tracking_last_distance,
            value = stats.lastDistance,
            showUndefined = trackingEnabled,
            tag = TAG_TRACK_LAST_DIST,
            modifier = modifier
        )
        StatsLine(
            labelRes = R.string.stats_tracking_check_count,
            value = stats.numberOfChecks,
            showUndefined = trackingEnabled,
            tag = TAG_TRACK_CHECKS,
            modifier = modifier
        )
        StatsLine(
            labelRes = R.string.stats_tracking_last_check,
            value = stats.lastCheckTime,
            showUndefined = trackingEnabled,
            tag = TAG_TRACK_LAST_CHECK,
            modifier = modifier
        )
        StatsLine(
            labelRes = R.string.stats_tracking_update_count,
            value = stats.numberOfUpdates,
            showUndefined = trackingEnabled,
            tag = TAG_TRACK_UPDATES,
            modifier = modifier
        )
        StatsLine(
            labelRes = R.string.stats_tracking_last_update,
            value = stats.lastUpdateTime,
            showUndefined = trackingEnabled,
            tag = TAG_TRACK_LAST_UPDATE,
            modifier = modifier
        )
        StatsLine(
            labelRes = R.string.stats_tracking_error_count,
            value = stats.numberOfErrors,
            showUndefined = false,
            color = MaterialTheme.colors.error,
            tag = TAG_TRACK_ERRORS,
            modifier = modifier
        )
        StatsLine(
            labelRes = R.string.stats_tracking_last_error,
            value = stats.lastErrorTime,
            showUndefined = false,
            color = MaterialTheme.colors.error,
            tag = TAG_TRACK_LAST_ERROR,
            modifier = modifier
        )
    }
}

/**
 * Generate one line of the tracking statistics screen, showing one statistics item. The item consists of a
 * [label][labelRes] and an optional [value]. If [value] is undefined, show the line only if [showUndefined] is *true*.
 * Use [color] as text color.
 */
@Composable
fun StatsLine(
    labelRes: Int,
    value: String?,
    showUndefined: Boolean,
    tag: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colors.onBackground
) {
    if (value != null || showUndefined) {
        Row(
            modifier = modifier
                .fillMaxWidth()
        ) {
            Text(text = stringResource(id = labelRes), color = color, modifier = modifier.testTag(labelTag(tag)))
            if (value != null) {
                Spacer(modifier = modifier.width(width = 4.dp))
                Text(
                    text = value,
                    textAlign = TextAlign.Right,
                    color = color,
                    modifier = modifier
                        .fillMaxWidth()
                        .testTag(tag)
                )
            }
        }
    }
}

/**
 * Generate a button that allows the user to reset tracking statistics. Report button clicks via the given [onClick]
 * function.
 */
@Composable
private fun ResetStatsButton(onClick: () -> Unit, modifier: Modifier) {
    Row(modifier = modifier) {
        Spacer(modifier = modifier.weight(1f))
        Button(onClick = onClick, modifier = modifier.testTag(TAG_TRACK_RESET_STATS)) {
            Text(text = stringResource(id = R.string.track_reset_stats))
        }
        Spacer(modifier = modifier.weight(1f))
    }
}

@Preview(name = "Light mode")
@Preview(
    name = "Dark mode",
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    showBackground = true
)
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
        lastErrorTime = "16:50:12"
        numberOfErrors = "1"
    }
    val model = PreviewTrackViewModel(state)

    LocationTellerTheme {
        TrackView(model = model, locationPermissionState = permissionState, openDrawer = {}, updateTrackState = {})
    }
}
