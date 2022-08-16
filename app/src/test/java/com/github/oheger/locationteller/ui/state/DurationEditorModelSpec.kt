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

import androidx.compose.runtime.saveable.SaverScope

import io.kotest.core.spec.style.WordSpec
import io.kotest.inspectors.forAll
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import io.mockk.mockk

/**
 * Test class for [DurationEditorModel].
 */
class DurationEditorModelSpec : WordSpec({
    "create" should {
        "create an instance that populates all supported duration components" {
            val duration = 3 * SECS_PER_DAY + 15 * SECS_PER_HOUR + 44 * SECS_PER_MINUTE + 11

            val model = DurationEditorModel.create(duration, maxComponent = DurationEditorModel.Component.DAY)

            model[DurationEditorModel.Component.SECOND] shouldBe 11
            model[DurationEditorModel.Component.MINUTE] shouldBe 44
            model[DurationEditorModel.Component.HOUR] shouldBe 15
            model[DurationEditorModel.Component.DAY] shouldBe 3
        }

        "create an instance that populates a limited number of duration components" {
            val duration = 50 * SECS_PER_HOUR + 22 * SECS_PER_MINUTE + 59

            val model = DurationEditorModel.create(duration, maxComponent = DurationEditorModel.Component.HOUR)

            model[DurationEditorModel.Component.SECOND] shouldBe 59
            model[DurationEditorModel.Component.MINUTE] shouldBe 22
            model[DurationEditorModel.Component.HOUR] shouldBe 50
            model[DurationEditorModel.Component.DAY] shouldBe 0
        }
    }

    "duration" should {
        "compute the duration in seconds from the current components" {
            val orgDuration = 3 * SECS_PER_DAY + 15 * SECS_PER_HOUR + 44 * SECS_PER_MINUTE + 11
            val newDuration = 4 * SECS_PER_DAY + 14 * SECS_PER_HOUR + 45 * SECS_PER_MINUTE + 10
            val model = DurationEditorModel.create(orgDuration, DurationEditorModel.Component.DAY)

            model[DurationEditorModel.Component.DAY] = model[DurationEditorModel.Component.DAY] + 1
            model[DurationEditorModel.Component.MINUTE] = model[DurationEditorModel.Component.MINUTE] + 1
            model[DurationEditorModel.Component.HOUR] = model[DurationEditorModel.Component.HOUR] - 1
            model[DurationEditorModel.Component.SECOND] = model[DurationEditorModel.Component.SECOND] - 1

            model.duration() shouldBe newDuration
        }
    }

    "the saver" should {
        "support a round-trip of a model" {
            val model = DurationEditorModel.create(123456789, DurationEditorModel.Component.HOUR)

            val scope = mockk<SaverScope>()
            val savedData = with(DurationEditorModel.SAVER) {
                scope.save(model)
            }
            val restoredModel = DurationEditorModel.SAVER.restore(savedData!!)

            restoredModel.shouldNotBeNull()
            restoredModel.duration() shouldBe model.duration()

            DurationEditorModel.Component.values().forAll { component ->
                restoredModel[component] shouldBe model[component]
            }
        }

        "handle a serialized array with an unexpected number of components" {
            DurationEditorModel.SAVER.restore(arrayOf(1, 2, 3)) should beNull()
        }

        "handle a serialized array with an invalid maximum component" {
            DurationEditorModel.SAVER.restore(arrayOf(1000, 1000)) should beNull()
        }
    }
})

/** Constant for the seconds of a minute. */
private const val SECS_PER_MINUTE = 60

/** Constant for the seconds of an hour. */
private const val SECS_PER_HOUR = SECS_PER_MINUTE * 60

/** Constant for the seconds of a day. */
private const val SECS_PER_DAY = SECS_PER_HOUR * 24
