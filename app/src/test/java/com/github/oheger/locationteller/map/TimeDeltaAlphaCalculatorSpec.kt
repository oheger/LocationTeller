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
package com.github.oheger.locationteller.map

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import kotlin.math.pow

class TimeDeltaAlphaCalculatorSpec : WordSpec({
    "ConstantTimeDeltaAlphaCalculator" should {
        "return the constant alpha value" {
            val alpha = 0.75f
            val calculator = ConstantTimeDeltaAlphaCalculator(alpha)

            (0 until 20).map { 2f.pow(it).toLong() }
                .forEach { calculator.calculateAlpha(it) shouldBe alpha }
        }
    }

    "RangeTimeDeltaAlphaCalculator" should {
        "return the minimum alpha if no ranges are provided" {
            val alpha = 0.4f
            val calculator = RangeTimeDeltaAlphaCalculator(emptyList(), alpha)

            calculator.calculateAlpha(42) shouldBe alpha
        }

        "return the minimum alpha if the maximum time of the last range is exceeded" {
            val alpha = 0.2f
            val calculator = RangeTimeDeltaAlphaCalculator(
                listOf(
                    AlphaRange(1f, 0.7f, 100),
                    AlphaRange(0.6f, 0.5f, 1000)
                ), alpha
            )

            calculator.calculateAlpha(1000) shouldBe alpha
        }

        "return the maximum alpha on the start time of a range" {
            val alpha = 0.75f
            val calculator = RangeTimeDeltaAlphaCalculator(
                listOf(
                    AlphaRange(1f, 0.8f, 100),
                    AlphaRange(alpha, 0.5f, 1000)
                ), 0f
            )

            calculator.calculateAlpha(100) shouldBe alpha
        }

        "return a correct alpha value in the relative position of a range" {
            val range = AlphaRange(1f, 0.5f, 10)
            val calculator = RangeTimeDeltaAlphaCalculator(listOf(range), 0f)

            calculator.calculateAlpha(5) shouldBe 0.75f
        }
    }
})
