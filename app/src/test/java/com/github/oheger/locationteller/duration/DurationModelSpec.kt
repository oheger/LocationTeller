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

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe

/**
 * Test class for [DurationModel].
 */
class DurationModelSpec : WordSpec({
    "create" should {
        "create an instance that populates all supported duration components" {
            val duration = 3 * SECS_PER_DAY + 15 * SECS_PER_HOUR + 44 * SECS_PER_MINUTE + 11

            val model = DurationModel.create(duration, maxComponent = DurationModel.Component.DAY)

            model[DurationModel.Component.SECOND] shouldBe 11
            model[DurationModel.Component.MINUTE] shouldBe 44
            model[DurationModel.Component.HOUR] shouldBe 15
            model[DurationModel.Component.DAY] shouldBe 3
        }

        "create an instance that populates a limited number of duration components" {
            val duration = 50 * SECS_PER_HOUR + 22 * SECS_PER_MINUTE + 59

            val model = DurationModel.create(duration, maxComponent = DurationModel.Component.HOUR)

            model[DurationModel.Component.SECOND] shouldBe 59
            model[DurationModel.Component.MINUTE] shouldBe 22
            model[DurationModel.Component.HOUR] shouldBe 50
            model[DurationModel.Component.DAY] shouldBe 0
        }
    }

    "duration" should {
        "compute the duration in seconds from the current components" {
            val orgDuration = 3 * SECS_PER_DAY + 15 * SECS_PER_HOUR + 44 * SECS_PER_MINUTE + 11
            val newDuration = 4 * SECS_PER_DAY + 14 * SECS_PER_HOUR + 45 * SECS_PER_MINUTE + 10
            val model = DurationModel.create(orgDuration, DurationModel.Component.DAY)

            model[DurationModel.Component.DAY] = model[DurationModel.Component.DAY] + 1
            model[DurationModel.Component.MINUTE] = model[DurationModel.Component.MINUTE] + 1
            model[DurationModel.Component.HOUR] = model[DurationModel.Component.HOUR] - 1
            model[DurationModel.Component.SECOND] = model[DurationModel.Component.SECOND] - 1

            model.duration() shouldBe newDuration
        }
    }
})

/** Constant for the seconds of a minute. */
private const val SECS_PER_MINUTE = 60

/** Constant for the seconds of an hour. */
private const val SECS_PER_HOUR = SECS_PER_MINUTE * 60

/** Constant for the seconds of a day. */
private const val SECS_PER_DAY = SECS_PER_HOUR * 24
