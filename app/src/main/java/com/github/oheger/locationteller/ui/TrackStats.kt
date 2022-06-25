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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

import com.github.oheger.locationteller.R
import com.github.oheger.locationteller.ui.state.TrackStatsState

/**
 * Generate the tracking statistics screen based on [stats]. Output a [StatsLine] for each statistics data item.
 */
@Composable
fun TrackStats(stats: TrackStatsState, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        StatsLine(labelRes = R.string.stats_tracking_started, value = stats.startTime)
        StatsLine(labelRes = R.string.stats_tracking_stopped, value = stats.endTime)
        StatsLine(labelRes = R.string.stats_tracking_time, value = stats.elapsedTime)
        StatsLine(labelRes = R.string.stats_tracking_total_distance, value = stats.totalDistance)
        StatsLine(labelRes = R.string.stats_tracking_speed, value = stats.averageSpeed)
        StatsLine(labelRes = R.string.stats_tracking_last_distance, value = stats.lastDistance)
        StatsLine(labelRes = R.string.stats_tracking_check_count, value = stats.numberOfChecks)
        StatsLine(labelRes = R.string.stats_tracking_last_check, value = stats.lastCheckTime)
        StatsLine(labelRes = R.string.stats_tracking_update_count, value = stats.numberOfUpdates)
        StatsLine(labelRes = R.string.stats_tracking_last_update, value = stats.lastUpdateTime)
        StatsLine(labelRes = R.string.stats_tracking_error_count, value = stats.numberOfErrors)
        StatsLine(labelRes = R.string.stats_tracking_last_error, value = stats.lastErrorTime)
    }
}

/**
 * Generate one line of the tracking statistics screen, showing one statistics item. The item consists of a
 * [label][labelRes] and an optional [value].
 */
@Composable
fun StatsLine(labelRes: Int, value: String?, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.padding(all = 2.dp)
            .fillMaxWidth()
    ) {
        Text(text = stringResource(id = labelRes))
        Spacer(modifier = modifier.width(width = 4.dp))
        Text(
            text = value.orEmpty(),
            textAlign = TextAlign.Right,
            modifier = modifier.fillMaxWidth()
        )
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
