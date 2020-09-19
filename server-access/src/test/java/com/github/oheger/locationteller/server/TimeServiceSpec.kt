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
package com.github.oheger.locationteller.server

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.shouldBe
import java.lang.Math.abs
import java.util.Calendar

/**
 * Test class for [TimeService] implementations and related classes.
 */
class TimeServiceSpec : StringSpec() {
    private fun initCalendar(year: Int, month: Int, day: Int, hour: Int = 0, minute: Int = 0, sec: Int = 0): Calendar {
        val calendar = Calendar.getInstance()
        calendar.clear()
        calendar.set(year, month, day, hour, minute, sec)
        return calendar
    }

    init {
        "CurrentTimeService should return an object close to the current time" {
            val now = System.currentTimeMillis()

            val currentTime = CurrentTimeService.currentTime()
            abs(currentTime.currentTime - now) shouldBeLessThan 3000
        }

        "TimeData should generate a correct date string" {
            val cal = initCalendar(2019, Calendar.JUNE, 18)
            val time = TimeData(cal.timeInMillis)

            time.dateString shouldBe "2019-06-18"
        }

        "TimeData should generate a correct time string" {
            val cal = initCalendar(2019, Calendar.JUNE, 19, 21, 28, 7)
            val time = TimeData(cal.timeInMillis)

            time.timeString shouldBe "21_28_07"
        }
    }
}
