/*
 * Copyright 2019-2020 The Developers.
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

import com.github.oheger.locationteller.server.TimeData
import com.github.oheger.locationteller.server.TimeService
import io.kotlintest.matchers.numerics.shouldBeLessThanOrEqual
import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import io.mockk.every
import io.mockk.mockk
import java.text.DateFormat
import java.text.NumberFormat
import java.util.*
import kotlin.math.abs

/**
 * Test class for [[TrackStatsFormatter]].
 */
class TrackStatsFormatterSpec : StringSpec() {
    init {
        "TrackStatsFormatter should correctly format a number" {
            val value = 3.1415
            val format = NumberFormat.getInstance()
            format.minimumFractionDigits = 2
            format.maximumFractionDigits = 2
            val expectedResult = format.format(value)
            val formatter = TrackStatsFormatter.create(null)

            formatter.formatNumber(value) shouldBe expectedResult
        }

        "TrackStatsFormatter should format a duration in seconds" {
            val formatter = TrackStatsFormatter.create(null)

            formatter.formatDuration(9000L) shouldBe "0:09"
        }

        "TrackStatsFormatter should format a duration in minutes" {
            val formatter = TrackStatsFormatter.create(null)

            formatter.formatDuration(612000L) shouldBe "10:12"
        }

        "TrackStatsFormatter should format a duration in hours" {
            val formatter = TrackStatsFormatter.create(null)

            formatter.formatDuration(7800000L) shouldBe "2:10:00"
        }

        "TrackStatsFormatter should format a duration in days" {
            val formatter = TrackStatsFormatter.create(null)

            formatter.formatDuration(1036802000L) shouldBe "12:00:00:02"
        }

        "TrackingStatsFormatter should a zero duration" {
            val formatter = TrackStatsFormatter.create(null)

            formatter.formatDuration(0L) shouldBe "0:00"
        }

        "TrackingStatsFormatter should create a correct time service" {
            val formatter = TrackStatsFormatter.create(null)

            val currentTime = formatter.timeService.currentTime().currentTime
            val deltaT = abs(System.currentTimeMillis() - currentTime)
            deltaT shouldBeLessThanOrEqual 3000
        }

        "TrackingStatsFormatter should format a date on the same day" {
            val date = toDate(22, 1, 12)
            val now = toDate(22, 2, 48)

            checkFormatDate(date, now, dateString(date, withDatePortion = false))
        }

        "TrackingStatsFormatter should format a date on another day" {
            val date = toDate(22, 1, 12, day = 5)
            val now = toDate(22, 18, 12)

            checkFormatDate(date, now, dateString(date, withDatePortion = true))
        }

        "TrackingStatsFormatter should format a date on another month" {
            val date = toDate(21, 33, 47, month = Calendar.MARCH)
            val now = toDate(21, 33, 47)

            checkFormatDate(date, now, dateString(date, withDatePortion = true))
        }

        "TrackingStatsFormatter should format a date on another year" {
            val date = toDate(21, 39, 55, year = 2019)
            val now = toDate(21, 39, 55)

            checkFormatDate(date, now, dateString(date, withDatePortion = true))
        }

        "TrackingStatsFormatter should format a null date" {
            val formatter = TrackStatsFormatter.create(null)

            formatter.formatDate(null) shouldBe ""
        }
    }

    companion object {
        /**
         * Generates a date object with the given time portion and an optional
         * date portion.
         * @param hour the hour of the day
         * @param minute the minute
         * @param second the second
         * @param year the year
         * @param month the month
         * @param day the day of month
         * @return the resulting date
         */
        private fun toDate(
            hour: Int, minute: Int, second: Int, year: Int = 2020,
            month: Int = Calendar.FEBRUARY, day: Int = 6
        ): Date {
            val cal = Calendar.getInstance()
            cal.set(year, month, day, hour, minute, second)
            return cal.time
        }

        /**
         * Helper function to check whether a date is formatted correctly.
         * @param date the date to format
         * @param now the current time
         * @param expected the expected string result
         */
        private fun checkFormatDate(date: Date, now: Date, expected: String) {
            val timeService = mockk<TimeService>()
            every { timeService.currentTime() } returns TimeData(now.time)
            val formatter = TrackStatsFormatter.create(timeService)

            val result = formatter.formatDate(date)
            result shouldBe expected
        }

        /**
         * Generates the formatted string to be displayed for a date based on
         * the given criteria.
         * @param date the date
         * @param withDatePortion flag whether the date part should be
         * contained
         * @return the formatted date string
         */
        private fun dateString(date: Date, withDatePortion: Boolean): String {
            val format = if (withDatePortion) DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM)
            else DateFormat.getTimeInstance(DateFormat.MEDIUM)
            return format.format(date)
        }
    }
}
