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
import java.lang.StringBuilder
import java.text.DateFormat
import java.text.NumberFormat
import java.util.*

/**
 * A class providing functionality to format values (especially dates or
 * numbers) related to tracking statistics.
 *
 * The class is used by the UI to display tracking statistics. It manages
 * formatter objects to handle the locale-dependent formatting.
 *
 * @param timeService the object to query the current time
 */
class TrackStatsFormatter private constructor(val timeService: TimeService) {
    /** An object to format numbers.*/
    private val numberFormat = createNumberFormat()

    /** The formatter for a date object. */
    private val dateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM)

    /** The formatter for the time portion of a date. */
    private val timeFormat = DateFormat.getTimeInstance(DateFormat.MEDIUM)

    /**
     * Formats the given number value.
     * @param value the value to be formatted
     * @return the resulting formatted string
     */
    fun formatNumber(value: Double): String = numberFormat.format(value)

    /**
     * Formats a duration in milliseconds to a string.
     * @param deltaMillis the duration in millis
     * @return the formatted duration string
     */
    fun formatDuration(deltaMillis: Long): String {
        val buf = StringBuilder(11)
        val deltaSecs = deltaMillis / MILLIS_PER_SEC
        val days = deltaSecs / SECS_PER_DAY
        formatTimeComponent(buf, days, force = false, withSeparator = true)
        val hours = (deltaSecs % SECS_PER_DAY)
        formatTimeComponent(buf, hours / SECS_PER_HOUR, force = false, withSeparator = true)
        val minutes = hours % SECS_PER_HOUR
        formatTimeComponent(buf, minutes / SECS_PER_MINUTE, force = true, withSeparator = true)
        val secs = minutes % SECS_PER_MINUTE
        formatTimeComponent(buf, secs, force = true, withSeparator = false)
        return buf.toString()
    }

    /**
     * Formats a date to a string. The resulting string is kept to the minimum:
     * If the date to be formatted is on the same day as today, only the time
     * portion is printed; otherwise the full date is contained in the output.
     * @param date the date to be formatted
     * @return the formatted date
     */
    fun formatDate(date: Date?): String {
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
        } ?: ""
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
         * Creates a new instance of _TrackStatsFormatter_. Optionally, a
         * _TimeService_ to be used can be passed in; otherwise, a default
         * service is used.
         * @param timeService reference to a [TimeService]
         */
        fun create(timeService: TimeService = CurrentTimeService): TrackStatsFormatter =
            TrackStatsFormatter(timeService)

        /**
         * Creates an object to be used for formatting fractional numbers.
         * @return the format object
         */
        private fun createNumberFormat(): NumberFormat =
            NumberFormat.getNumberInstance().apply {
                minimumFractionDigits = 2
                maximumFractionDigits = 2
            }

        /**
         * Adds a formatted time component to the given buffer. The component
         * is only added if necessary (if not 0, or other components already
         * exists, or the _force_ flag is set). The value must be in the range
         * between 0 and 59.
         * @param buf the buffer to add the text
         * @param time the time component to be formatted
         * @param force flag whether this component needs to be added
         * @param withSeparator flag whether a trailing separator needs to be
         * added
         */
        private fun formatTimeComponent(buf: StringBuilder, time: Long, force: Boolean, withSeparator: Boolean) {
            val existing = buf.isNotEmpty()
            if (force || existing || time > 0) {
                if (existing && time < 10) {
                    buf.append(0)
                }
                buf.append(time)
                if (withSeparator) {
                    buf.append(':')
                }
            }
        }

        /**
         * Checks whether two calendars refer to different days. In this case,
         * formatting has to be done differently.
         * @param cal1 one calendar
         * @param cal2 the other calendar
         * @return a flag whether the calendars have a different day
         */
        private fun isAnotherDay(cal1: Calendar, cal2: Calendar): Boolean =
            cal1.get(Calendar.DATE) != cal2.get(Calendar.DATE) ||
                    cal1.get(Calendar.MONTH) != cal2.get(Calendar.MONTH) ||
                    cal1.get(Calendar.YEAR) != cal2.get(Calendar.YEAR)
    }
}