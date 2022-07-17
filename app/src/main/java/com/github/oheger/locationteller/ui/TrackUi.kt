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

import androidx.compose.foundation.layout.*
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

import com.github.oheger.locationteller.R
import com.github.oheger.locationteller.ui.state.TrackStatsState
import com.github.oheger.locationteller.ui.state.TrackViewModel

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

/**
 * Generate the test tag of for the label element associated with the value defined by [tag].
 */
internal fun labelTag(tag: String): String = "${tag}_label"

/**
 * Generate the while tracking UI based on [model].
 */
@Composable
fun TrackUi(model: TrackViewModel = viewModel(), modifier: Modifier = Modifier) {
    TrackStats(stats = model.trackStatistics, modifier = modifier)
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

@Preview
@Composable
fun TrackStatsPreview() {
    val state = TrackStatsState()
    state.startTime = "2022-06-25 16:45:55"
    state.elapsedTime = "16:12"
    state.totalDistance = "2500"
    state.averageSpeed = "3.5"
    state.lastDistance = "142"
    state.numberOfChecks = "28"
    state.lastCheckTime = "17:01:31"
    state.numberOfUpdates = "20"
    state.lastUpdateTime = "17:01:31"

    TrackStats(stats = state)
}
