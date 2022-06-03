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
package com.github.oheger.locationteller.map

import android.content.Context
import com.github.oheger.locationteller.R
import com.github.oheger.locationteller.server.TimeData

/**
 * A class to format time deltas as strings according to their age.
 *
 * The class has a _format()_ method that expects a time difference in
 * milliseconds. It produces a string with a representation of this difference
 * using an appropriate time unit.
 *
 * The time unit names are obtained from string resources and initialized when
 * constructing an instance.
 */
class TimeDeltaFormatter(
    /** String to be displayed for the unit "seconds". */
    private val unitSec: String,

    /** String to be displayed for the unit "minutes". */
    private val unitMin: String,

    /** String to be displayed for the unit "hours". */
    private val unitHour: String,

    /** String to be displayed for the unit "days". */
    private val unitDay: String
) {
    /**
     * Generates a string representation for the given time delta in
     * milliseconds.
     * @param deltaMillis the delta in milliseconds
     * @return a string representing this delta
     */
    fun formatTimeDelta(deltaMillis: Long): String {
        val deltaSec = deltaMillis / 1000
        if (deltaSec < 60) {
            return "$deltaSec $unitSec"
        } else {
            val deltaMin = deltaSec / 60
            return if (deltaMin < 60) {
                "$deltaMin $unitMin"
            } else {
                val deltaHour = deltaMin / 60
                if (deltaHour < 24) {
                    "$deltaHour $unitHour"
                } else {
                    val deltaDay = deltaHour / 24
                    val remainingHours = (deltaHour % 24).takeIf { it > 0 }
                        ?.let { " $it $unitHour" } ?: ""
                    "$deltaDay $unitDay$remainingHours"
                }
            }
        }
    }

    /**
     * Generates a string representation for the given time delta or uses the
     * string representation of the time itself if the delta is less than a
     * day. This function can be used to have more detailed information for
     * timestamps that are younger.
     * @param deltaMillis the delta in milliseconds
     * @param timeData the object with full time information
     * @return a string representing this delta
     */
    fun formatTimeDeltaOrTime(deltaMillis: Long, timeData: TimeData): String =
        if (deltaMillis < DAY_MILLIS)
            timeData.timeString.substring(0, 5).replace('_', ':')
        else formatTimeDelta(deltaMillis)

    companion object {
        /** The number of milliseconds of a day. */
        private const val DAY_MILLIS = 24 * 60 * 60 * 1000L

        /**
         * Creates a new instance of _TimeDeltaFormatter_ and initializes it
         * from the given Android context.
         * @param context the context
         * @return the new formatter
         */
        fun create(context: Context): TimeDeltaFormatter {
            val resources = context.resources
            return TimeDeltaFormatter(
                resources.getString(R.string.time_secs),
                resources.getString(R.string.time_minutes),
                resources.getString(R.string.time_hours),
                resources.getString(R.string.time_days)
            )
        }
    }
}