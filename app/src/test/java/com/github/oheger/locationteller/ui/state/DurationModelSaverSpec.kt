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

import com.github.oheger.locationteller.duration.DurationModel

import io.kotest.core.spec.style.WordSpec
import io.kotest.inspectors.forAll
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import io.mockk.mockk

/**
 * Test class for the _Saver_ implementation for _DurationModel_.
 */
class DurationModelSaverSpec : WordSpec({
    "the saver" should {
        "support a round-trip of a model" {
            val model = DurationModel.create(123456789, DurationModel.Component.HOUR)

            val scope = mockk<SaverScope>()
            val savedData = with(DURATION_SAVER) {
                scope.save(model)
            }
            val restoredModel = DURATION_SAVER.restore(savedData!!)

            restoredModel.shouldNotBeNull()
            restoredModel.duration() shouldBe model.duration()

            DurationModel.Component.values().forAll { component ->
                restoredModel[component] shouldBe model[component]
            }
        }

        "handle a serialized array with an unexpected number of components" {
            DURATION_SAVER.restore(arrayOf(1, 2, 3)) should beNull()
        }

        "handle a serialized array with an invalid maximum component" {
            DURATION_SAVER.restore(arrayOf(1000, 1000)) should beNull()
        }
    }


})
