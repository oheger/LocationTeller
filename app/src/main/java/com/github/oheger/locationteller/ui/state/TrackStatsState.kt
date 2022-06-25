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

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * A class representing the UI state for tracking statistics.
 *
 * The class defines a number of properties to be displayed in the tracking statistics screen. These are all string
 * properties that have already been formatted to be shown to the end user.
 */
class TrackStatsState {
    /** The start time of the current tracking operation. */
    var startTime by mutableTrackState()

    /** The end time of the current tracking operation. */
    var endTime by mutableTrackState()

    /** The elapsed time the current tracking operation is ongoing. */
    var elapsedTime by mutableTrackState()

    /** The total distance (in m) that has been tracked. */
    var totalDistance by mutableTrackState()

    /** The average speed in km/h. */
    var averageSpeed by mutableTrackState()

    /** The last distance (in m) recorded for the latest change. */
    var lastDistance by mutableTrackState()

    /** The number of location checks that have been performed during this tracking operation. */
    var numberOfChecks by mutableTrackState()

    /** The time when the last change was recorded. */
    var lastCheckTime by mutableTrackState()

    /** The numb    er of location updates that have been recorded during this tracking operation. */
    var numberOfUpdates by mutableTrackState()

    /** The time when the last location update happened. */
    var lastUpdateTime by mutableTrackState()

    /** The number of errors encountered during this tracking operation. */
    var numberOfErrors by mutableTrackState()

    /** The time when the last tracking error was recorded. */
    var lastErrorTime by mutableTrackState()
}

/**
 * Create a [MutableState] object with a nullable string. This is the default type used by tracking statistics.
 */
private fun mutableTrackState(): MutableState<String?> = mutableStateOf(null)
