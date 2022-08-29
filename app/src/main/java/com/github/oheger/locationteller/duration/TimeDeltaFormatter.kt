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
package com.github.oheger.locationteller.duration

import com.github.oheger.locationteller.server.TimeData

/**
 * A class to format time deltas as strings according to their age.
 *
 * The class has a _format()_ method that expects a time difference in
 * milliseconds. It produces a string with a representation of this difference
 * using an appropriate time unit.
 *
 * The time unit names must be passed when constructing an instance.
 */
class TimeDeltaFormatter(
    /** String to be displayed for the unit "seconds". */
    val unitSec: String,

    /** String to be displayed for the unit "minutes". */
    val unitMin: String,

    /** String to be displayed for the unit "hours". */
    val unitHour: String,

    /** String to be displayed for the unit "days". */
    val unitDay: String
) {
    /** A mapping from duration components to their units. */
    private val componentUnits = mapOf(
        DurationModel.Component.HOUR to unitHour,
        DurationModel.Component.MINUTE to unitMin,
        DurationModel.Component.SECOND to unitSec
    )

    /**
     * Generate a string representation for the given [time delta in milliseconds][deltaMillis].
     */
    fun formatTimeDelta(deltaMillis: Long): String {
        val duration = DurationModel.create((deltaMillis / 1000).toInt(), DurationModel.Component.DAY)

        val maxComponent = DurationModel.Component.values().reversed().find { duration[it] > 0 }
        return if (maxComponent == DurationModel.Component.DAY) {
            val remainingHours = duration[DurationModel.Component.HOUR].takeIf { it > 0 }
                ?.let { " $it $unitHour" }.orEmpty()
            "${duration[DurationModel.Component.DAY]} $unitDay$remainingHours"
        } else {
            maxComponent?.let { "${duration[it]} ${componentUnits.getValue(it)}" } ?: "0 $unitSec"
        }
    }

    /**
     * Generate a string representation for the given [time delta in milliseconds][deltaMillis] or use the
     * string representation of the [timeData] itself if the delta is less than a day. This function can be used to
     * have more detailed information for timestamps that are younger.
     */
    fun formatTimeDeltaOrTime(deltaMillis: Long, timeData: TimeData): String =
        if (deltaMillis < DAY_MILLIS)
            timeData.timeString.substring(0, 5).replace('_', ':')
        else formatTimeDelta(deltaMillis)

    companion object {
        /** The number of milliseconds of a day. */
        private const val DAY_MILLIS = 24 * 60 * 60 * 1000L
    }
}
