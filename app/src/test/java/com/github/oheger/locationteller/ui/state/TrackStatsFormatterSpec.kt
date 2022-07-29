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

import com.github.oheger.locationteller.server.TimeData
import com.github.oheger.locationteller.server.TimeService

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.longs.shouldBeLessThanOrEqual
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import io.mockk.every
import io.mockk.mockk

import java.text.DateFormat
import java.text.NumberFormat
import java.util.Calendar
import java.util.Date

import kotlin.math.abs

/**
 * Test class for [[TrackStatsFormatter]].
 */
class TrackStatsFormatterSpec : WordSpec({
    "create" should {
        "create a correct time service" {
            val formatter = TrackStatsFormatter.create()

            val currentTime = formatter.timeService.currentTime().currentTime
            val deltaT = abs(System.currentTimeMillis() - currentTime)
            deltaT shouldBeLessThanOrEqual 3000
        }
    }

    "formatNumber" should {
        "correctly format a number" {
            val value = 3.1415
            val format = NumberFormat.getInstance()
            format.minimumFractionDigits = 2
            format.maximumFractionDigits = 2
            val expectedResult = format.format(value)
            val formatter = TrackStatsFormatter.create()

            formatter.formatNumber(value) shouldBe expectedResult
        }

        "return null for values less than or equal zero" {
            val formatter = TrackStatsFormatter.create()

            formatter.formatNumber(-0.1) should beNull()
            formatter.formatNumber(0.0) should beNull()
        }
    }

    "formatDuration" should {
        "format a duration in seconds" {
            val formatter = TrackStatsFormatter.create()

            formatter.formatDuration(9000L) shouldBe "0:09"
        }

        "format a duration in minutes" {
            val formatter = TrackStatsFormatter.create()

            formatter.formatDuration(612000L) shouldBe "10:12"
        }

        "format a duration in hours" {
            val formatter = TrackStatsFormatter.create()

            formatter.formatDuration(7800000L) shouldBe "2:10:00"
        }

        "format a duration in days" {
            val formatter = TrackStatsFormatter.create()

            formatter.formatDuration(1036802000L) shouldBe "12:00:00:02"
        }

        "return null for durations less than or equal to zero" {
            val formatter = TrackStatsFormatter.create()

            formatter.formatDuration(0L) should beNull()
            formatter.formatDuration(-1L) should beNull()
        }
    }

    "formatDate" should {
        "format a date on the same day" {
            val date = toDate(22, 1, 12)
            val now = toDate(22, 2, 48)

            checkFormatDate(date, now, dateString(date, withDatePortion = false))
        }

        "format a date on another day" {
            val date = toDate(22, 1, 12, day = 5)
            val now = toDate(22, 18, 12)

            checkFormatDate(date, now, dateString(date, withDatePortion = true))
        }

        "format a date on another month" {
            val date = toDate(21, 33, 47, month = Calendar.MARCH)
            val now = toDate(21, 33, 47)

            checkFormatDate(date, now, dateString(date, withDatePortion = true))
        }

        "format a date on another year" {
            val date = toDate(21, 39, 55, year = 2019)
            val now = toDate(21, 39, 55)

            checkFormatDate(date, now, dateString(date, withDatePortion = true))
        }

        "format a null date" {
            val formatter = TrackStatsFormatter.create()

            formatter.formatDate(null) should beNull()
        }
    }
})

/**
 * Generate a [Date] object from the given parameters.
 */
private fun toDate(
    hour: Int,
    minute: Int,
    second: Int,
    year: Int = 2020,
    month: Int = Calendar.FEBRUARY,
    day: Int = 6
): Date =
    Calendar.getInstance().run {
        set(year, month, day, hour, minute, second)
        time
    }

/**
 * Check whether a [date] is correctly formatted to [expected] given the [current time][now].
 */
private fun checkFormatDate(date: Date, now: Date, expected: String) {
    val timeService = mockk<TimeService>()
    every { timeService.currentTime() } returns TimeData(now.time)
    val formatter = TrackStatsFormatter.create(timeService)

    val result = formatter.formatDate(date)
    result shouldBe expected
}

/**
 * Generate the formatted string to be displayed for [date] based on a [flag][withDatePortion] whether the date
 * portion should be included.
 */
private fun dateString(date: Date, withDatePortion: Boolean): String {
    val timeStr = DateFormat.getTimeInstance(DateFormat.MEDIUM).format(date)
    return timeStr.takeUnless { withDatePortion }
        ?: "${DateFormat.getDateInstance(DateFormat.MEDIUM).format(date)} $timeStr"
}
