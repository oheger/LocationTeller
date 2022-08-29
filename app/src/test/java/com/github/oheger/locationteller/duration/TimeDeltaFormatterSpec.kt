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

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe

import java.text.SimpleDateFormat
import java.util.Calendar

/**
 * Test class for [TimeDeltaFormatter].
 */
class TimeDeltaFormatterSpec : WordSpec() {
    init {
        "formatTimeDelta" should {
            "format a delta using the seconds time unit" {
                val result = formatDelta(59 * 1000L)

                result shouldBe "59 $UNIT_SEC"
            }

            "format a delta using the minutes time unit for a delta > 1 minute" {
                val result = formatDelta(60 * 1000L)

                result shouldBe "1 $UNIT_MIN"
            }

            "format a delta using the minutes time unit for a delta < 1 hour" {
                val result = formatDelta(60 * 60 * 1000L - 1)

                result shouldBe "59 $UNIT_MIN"
            }

            "format a delta using the hours time unit for a delta >= 1 hour" {
                val result = formatDelta(60 * 60 * 1000L)

                result shouldBe "1 $UNIT_HOUR"
            }

            "format a delta using the hours time unit for a delta < 1 day" {
                val result = formatDelta(24 * 60 * 60 * 1000L - 1)

                result shouldBe "23 $UNIT_HOUR"
            }

            "format a delta using the days time unit for a delta >= 1 day" {
                val result = formatDelta(24 * 60 * 60 * 1000L)

                result shouldBe "1 $UNIT_DAY"
            }

            "format a delta using the days time unit for larger deltas" {
                val result = formatDelta(5 * 24 * 60 * 60 * 1000L - 1)

                result shouldBe "4 $UNIT_DAY 23 $UNIT_HOUR"
            }
        }

        "formatTimeDeltaOrTime" should {
            "generate a delta time string for a recent time delta" {
                val result = formatDeltaOrTime(0)

                result shouldBe TIME_STR
            }

            "generate a delta time string if the delta is less than a day" {
                val result = formatDeltaOrTime(24 * 60 * 60 * 1000L - 1)

                result shouldBe TIME_STR
            }

            "generate a delta time string if the delta is greater than a day" {
                val result = formatDeltaOrTime(24 * 60 * 60 * 1000L)

                result shouldBe "1 $UNIT_DAY"
            }
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

        /** Constant for a test time. The long value corresponds to 2020-10-13T20:10:24. */
        private val TIME = TimeData(1602619824123L)

        /** The time string to be produced when formatting a timestamp. */
        private val TIME_STR = generateTimeString()

        /**
         * Create a test instance of [TimeDeltaFormatter].
         */
        private fun createFormatter(): TimeDeltaFormatter =
            TimeDeltaFormatter(UNIT_SEC, UNIT_MIN, UNIT_HOUR, UNIT_DAY)

        /**
         * Convenience function to format the given [delta] with a newly created test formatter.
         */
        private fun formatDelta(delta: Long): String = createFormatter().formatTimeDelta(delta)

        /**
         * Convenience function to format the given [delta] either as a duration or a timestamp with a newly
         * created test formatter.
         */
        private fun formatDeltaOrTime(delta: Long): String =
            createFormatter().formatTimeDeltaOrTime(delta, TIME)

        /**
         * Generate the time string for the test time. Note: To avoid problems with time zone conversions, the
         * string needs to be generated via a [Calendar] instance.
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
