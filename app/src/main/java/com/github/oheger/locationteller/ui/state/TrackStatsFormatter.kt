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

import com.github.oheger.locationteller.server.CurrentTimeService
import com.github.oheger.locationteller.server.TimeService

import java.text.DateFormat
import java.text.NumberFormat

import java.util.Calendar
import java.util.Date

import kotlin.text.StringBuilder

/**
 * A class providing functionality to format values (especially dates or
 * numbers) related to tracking statistics.
 *
 * The class is used by the UI to display tracking statistics. It manages
 * formatter objects to handle the locale-dependent formatting.
 */
class TrackStatsFormatter private constructor(
    /** The object to query the current time. */
    val timeService: TimeService
) {
    /** An object to format numbers.*/
    val numberFormat = createNumberFormat()

    /** The formatter for a date object. */
    private val dateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM)

    /** The formatter for the time portion of a date. */
    private val timeFormat = DateFormat.getTimeInstance(DateFormat.MEDIUM)

    /**
     * Format the given number [value]. Values less than or equal to zero are considered invalid and yield a
     * *null* result.
     */
    fun formatNumber(value: Double): String? = value.takeIf { it > 0 }?.let(numberFormat::format)

    /**
     * Format a [duration in milliseconds][deltaMillis] to a string. Values less than or equal to zero are considered
     * invalid and yield a *null* result.
     */
    fun formatDuration(deltaMillis: Long): String? = deltaMillis.takeIf { it > 0 }?.let {
        buildString {
            val deltaSecs = deltaMillis / MILLIS_PER_SEC
            val days = deltaSecs / SECS_PER_DAY
            formatTimeComponent(days, force = false, withSeparator = true)
            val hours = (deltaSecs % SECS_PER_DAY)
            formatTimeComponent(hours / SECS_PER_HOUR, force = false, withSeparator = true)
            val minutes = hours % SECS_PER_HOUR
            formatTimeComponent(minutes / SECS_PER_MINUTE, force = true, withSeparator = true)
            val secs = minutes % SECS_PER_MINUTE
            formatTimeComponent(secs, force = true, withSeparator = false)
        }
    }

    /**
     * Format [date] to a string. The resulting string is kept to the minimum: If the date to be formatted is on the
     * same day as today, only the time portion is printed; otherwise the full date is contained in the output. *null*
     * values as input lead to *null* output.
     */
    fun formatDate(date: Date?): String? {
        return date?.let {
            val current = timeService.currentTime()
            val calNow = Calendar.getInstance()
            calNow.timeInMillis = current.currentTime
            val calDate = Calendar.getInstance()
            calDate.time = it

            val timePart = timeFormat.format(date)
            return if (isAnotherDay(calDate, calNow))
                "${dateFormat.format(it)} $timePart"
            else timePart
        }
    }

    companion object {
        /** The number of milliseconds in a second.*/
        private const val MILLIS_PER_SEC = 1000

        /** The number of seconds per minute.*/
        private const val SECS_PER_MINUTE = 60

        /** The number of seconds per hour.*/
        private const val SECS_PER_HOUR = SECS_PER_MINUTE * 60

        /** The number of seconds per day.*/
        private const val SECS_PER_DAY = 24 * SECS_PER_HOUR

        /**
         * A default instance that is configured with [CurrentTimeService]. _Note:_ This instance must only be used
         * from the main thread, since formatter objects are not thread-safe.
         */
        val INSTANCE = create()

        /**
         * Create a new instance of [TrackStatsFormatter]. Use the provided [timeService] or fall back to a default
         * one if it is undefined.
         */
        fun create(timeService: TimeService = CurrentTimeService): TrackStatsFormatter =
            TrackStatsFormatter(timeService)

        /**
         * Create an object to be used for formatting fractional numbers.
         */
        private fun createNumberFormat(): NumberFormat =
            NumberFormat.getNumberInstance().apply {
                minimumFractionDigits = 2
                maximumFractionDigits = 2
            }

        /**
         * Add a formatted [time] component to this [StringBuilder]. Add the component only if necessary (if it is not
         * 0, or other components already exists, or the [force] flag is set). If [withSeparator] is *true*, add a
         * trailing separator. [time] must be in the range between 0 and 59.
         */
        private fun StringBuilder.formatTimeComponent(time: Long, force: Boolean, withSeparator: Boolean) {
            val existing = isNotEmpty()
            if (force || existing || time > 0) {
                if (existing && time < 10) {
                    append(0)
                }
                append(time)
                if (withSeparator) {
                    append(':')
                }
            }
        }

        /**
         * Check whether the two calendars [cal1] and [cal2] refer to different days. In this case, formatting has to
         * be done differently.
         */
        private fun isAnotherDay(cal1: Calendar, cal2: Calendar): Boolean =
            cal1.get(Calendar.DATE) != cal2.get(Calendar.DATE) ||
                    cal1.get(Calendar.MONTH) != cal2.get(Calendar.MONTH) ||
                    cal1.get(Calendar.YEAR) != cal2.get(Calendar.YEAR)
    }
}