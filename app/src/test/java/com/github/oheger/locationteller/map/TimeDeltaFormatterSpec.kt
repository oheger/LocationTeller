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
package com.github.oheger.locationteller.map

import android.content.Context
import android.content.res.Resources
import com.github.oheger.locationteller.R
import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import io.mockk.every
import io.mockk.mockk

/**
 * Test class for _TimeDeltaFormatter_.
 */
class TimeDeltaFormatterSpec : StringSpec() {
    init {
        "TimeDeltaFormatter should set a title using the seconds time unit" {
            val result = formatDelta(59 * 1000L)

            result shouldBe "59 $unitSec"
        }

        "TimeDeltaFormatter should set a title using the minutes time unit for a delta > 1 minute" {
            val result = formatDelta(60 * 1000L)

            result shouldBe "1 $unitMin"
        }

        "TimeDeltaFormatter should set a title using the minutes time unit for a delta < 1 hour" {
            val result = formatDelta(60 * 60 * 1000L - 1)

            result shouldBe "59 $unitMin"
        }

        "TimeDeltaFormatter should set a title using the hours time unit for a delta >= 1 hour" {
            val result = formatDelta(60 * 60 * 1000L)

            result shouldBe "1 $unitHour"
        }

        "TimeDeltaFormatter should set a title using the hours time unit for a delta < 1 day" {
            val result = formatDelta(24 * 60 * 60 * 1000L - 1)

            result shouldBe "23 $unitHour"
        }

        "TimeDeltaFormatter should set a title using the days time unit for a delta >= 1 day" {
            val result = formatDelta(24 * 60 * 60 * 1000L)

            result shouldBe "1 $unitDay"
        }

        "TimeDeltaFormatter should set a title using the days time unit for larger deltas" {
            val result = formatDelta(5 * 24 * 60 * 60 * 1000L - 1)

            result shouldBe "4 $unitDay"
        }
    }

    companion object {
        /** Time unit for seconds.*/
        private const val unitSec = "s"

        /** Time unit for minutes.*/
        private const val unitMin = "m"

        /** Time unit for hours.*/
        private const val unitHour = "h"

        /** Time unit for days.*/
        private const val unitDay = "d"

        /**
         * Creates an Android context that is prepared to return resources for
         * the time units used by the factory.
         */
        private fun createContext(): Context {
            val context = mockk<Context>()
            val resources = mockk<Resources>()
            every { context.resources } returns resources
            every { resources.getString(R.string.time_secs) } returns unitSec
            every { resources.getString(R.string.time_minutes) } returns unitMin
            every { resources.getString(R.string.time_hours) } returns unitHour
            every { resources.getString(R.string.time_days) } returns unitDay
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
    }
}
