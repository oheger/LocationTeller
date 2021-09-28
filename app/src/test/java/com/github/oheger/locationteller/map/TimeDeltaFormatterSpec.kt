/*
 * Copyright 2019-2021 The Developers.
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
import android.content.res.Resources
import com.github.oheger.locationteller.R
import com.github.oheger.locationteller.server.TimeData
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.text.SimpleDateFormat
import java.util.Calendar

/**
 * Test class for _TimeDeltaFormatter_.
 */
class TimeDeltaFormatterSpec : StringSpec() {
    init {
        "TimeDeltaFormatter should format a delta using the seconds time unit" {
            val result = formatDelta(59 * 1000L)

            result shouldBe "59 $UNIT_SEC"
        }

        "TimeDeltaFormatter should format a delta using the minutes time unit for a delta > 1 minute" {
            val result = formatDelta(60 * 1000L)

            result shouldBe "1 $UNIT_MIN"
        }

        "TimeDeltaFormatter should format a delta using the minutes time unit for a delta < 1 hour" {
            val result = formatDelta(60 * 60 * 1000L - 1)

            result shouldBe "59 $UNIT_MIN"
        }

        "TimeDeltaFormatter should format a delta using the hours time unit for a delta >= 1 hour" {
            val result = formatDelta(60 * 60 * 1000L)

            result shouldBe "1 $UNIT_HOUR"
        }

        "TimeDeltaFormatter should format a delta using the hours time unit for a delta < 1 day" {
            val result = formatDelta(24 * 60 * 60 * 1000L - 1)

            result shouldBe "23 $UNIT_HOUR"
        }

        "TimeDeltaFormatter should format a delta using the days time unit for a delta >= 1 day" {
            val result = formatDelta(24 * 60 * 60 * 1000L)

            result shouldBe "1 $UNIT_DAY"
        }

        "TimeDeltaFormatter should format a delta using the days time unit for larger deltas" {
            val result = formatDelta(5 * 24 * 60 * 60 * 1000L - 1)

            result shouldBe "4 $UNIT_DAY 23 $UNIT_HOUR"
        }

        "TimeDeltaFormatter should generate a delta time string for a recent time delta" {
            val result = formatDeltaOrTime(0)

            result shouldBe TIME_STR
        }

        "TimeDeltaFormatter should generate a delta time string if the delta is less than a day" {
            val result = formatDeltaOrTime(24 * 60 * 60 * 1000L - 1)

            result shouldBe TIME_STR
        }

        "TimeDeltaFormatter should generate a delta time string if the delta is greater than a day" {
            val result = formatDeltaOrTime(24 * 60 * 60 * 1000L)

            result shouldBe "1 $UNIT_DAY"
        }
    }

    companion object {
        /** Time unit for seconds.*/
        private const val UNIT_SEC = "s"

        /** Time unit for minutes.*/
        private const val UNIT_MIN = "m"

        /** Time unit for hours.*/
        private const val UNIT_HOUR = "h"

        /** Time unit for days.*/
        private const val UNIT_DAY = "d"

        /**
         * Constant for a test time. The long value corresponds to
         * 2020-10-13T20:10:24.
         */
        private val TIME = TimeData(1602619824123L)

        /** The time string to be produced when formatting a timestamp. */
        private val TIME_STR = generateTimeString()

        /**
         * Creates an Android context that is prepared to return resources for
         * the time units used by the factory.
         */
        private fun createContext(): Context {
            val context = mockk<Context>()
            val resources = mockk<Resources>()
            every { context.resources } returns resources
            every { resources.getString(R.string.time_secs) } returns UNIT_SEC
            every { resources.getString(R.string.time_minutes) } returns UNIT_MIN
            every { resources.getString(R.string.time_hours) } returns UNIT_HOUR
            every { resources.getString(R.string.time_days) } returns UNIT_DAY
            return context
        }

        /**
         * Creates a test instance of the formatter.
         * @return the test instance of the formatter
         */
        private fun createFormatter(): TimeDeltaFormatter =
            TimeDeltaFormatter.create(createContext())

        /**
         * Convenience function to format a delta with a newly created test
         * formatter.
         * @param delta the delta to be formatted
         * @return the formatted delta
         */
        private fun formatDelta(delta: Long): String = createFormatter().formatTimeDelta(delta)

        /**
         * Convenience function to format a delta or a timestamp with a newly
         * created test formatter.
         * @param delta the delta to be formatted
         * @return the formatted delta
         */
        private fun formatDeltaOrTime(delta: Long): String =
            createFormatter().formatTimeDeltaOrTime(delta, TIME)

        /**
         * Generates the time string for the test time. Note: To avoid problems
         * with time zone conversions, the string needs to be generated via a
         * _Calendar_ instance.
         * @return the time string used by tests
         */
        private fun generateTimeString(): String {
            val fmt = SimpleDateFormat("HH:mm")
            return Calendar.getInstance().run {
                timeInMillis = TIME.currentTime
                fmt.format(time)
            }
        }
    }
}
