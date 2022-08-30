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
            val duration = 3 * DurationModel.Component.DAY.toSeconds() +
                    15 * DurationModel.Component.HOUR.toSeconds() +
                    44 * DurationModel.Component.MINUTE.toSeconds() + 11

            val model = DurationModel.create(duration, maxComponent = DurationModel.Component.DAY)

            model[DurationModel.Component.SECOND] shouldBe 11
            model[DurationModel.Component.MINUTE] shouldBe 44
            model[DurationModel.Component.HOUR] shouldBe 15
            model[DurationModel.Component.DAY] shouldBe 3
        }

        "create an instance that populates a limited number of duration components" {
            val duration = 50 * DurationModel.Component.HOUR.toSeconds() +
                    22 * DurationModel.Component.MINUTE.toSeconds() + 59

            val model = DurationModel.create(duration, maxComponent = DurationModel.Component.HOUR)

            model[DurationModel.Component.SECOND] shouldBe 59
            model[DurationModel.Component.MINUTE] shouldBe 22
            model[DurationModel.Component.HOUR] shouldBe 50
            model[DurationModel.Component.DAY] shouldBe 0
        }
    }

    "duration" should {
        "compute the duration in seconds from the current components" {
            val orgDuration = 3 * DurationModel.Component.DAY.toSeconds() +
                    15 * DurationModel.Component.HOUR.toSeconds() +
                    44 * DurationModel.Component.MINUTE.toSeconds() + 11
            val newDuration = 4 * DurationModel.Component.DAY.toSeconds() +
                    14 * DurationModel.Component.HOUR.toSeconds() +
                    45 * DurationModel.Component.MINUTE.toSeconds() + 10
            val model = DurationModel.create(orgDuration, DurationModel.Component.DAY)

            model[DurationModel.Component.DAY] = model[DurationModel.Component.DAY] + 1
            model[DurationModel.Component.MINUTE] = model[DurationModel.Component.MINUTE] + 1
            model[DurationModel.Component.HOUR] = model[DurationModel.Component.HOUR] - 1
            model[DurationModel.Component.SECOND] = model[DurationModel.Component.SECOND] - 1

            model.duration() shouldBe newDuration
        }
    }

    "Component" should {
        "return the milliseconds per second" {
            DurationModel.Component.SECOND.toMillis() shouldBe 1000L
        }

        "return the milliseconds per minute" {
            DurationModel.Component.MINUTE.toMillis() shouldBe 60000L
        }

        "return the milliseconds per hour" {
            DurationModel.Component.HOUR.toMillis() shouldBe 60 * 60000L
        }

        "return the milliseconds per day" {
            DurationModel.Component.DAY.toMillis() shouldBe 24 * 60 * 60000L
        }
    }
})

/**
 * Return the number of seconds contained in this component.
 */
fun DurationModel.Component.toSeconds(): Int = (toMillis() / 1000).toInt()
