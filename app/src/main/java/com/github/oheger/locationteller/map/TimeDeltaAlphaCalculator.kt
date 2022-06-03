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

/**
 * An interface for a component that calculates an alpha value for a marker
 * with a specific age.
 *
 * The idea is that positions markers should be rendered more and more
 * transparently the older they become. By having different implementations of
 * this interface, the fading out of old positions can be configured.
 */
interface TimeDeltaAlphaCalculator {
    /**
     * Calculates the alpha value for a marker with the given age in
     * milliseconds.
     * @param ageMillis the age of the marker in milliseconds
     * @return the alpha value to be used for this marker
     */
    fun calculateAlpha(ageMillis: Long): Float
}

/**
 * A trivial implementation of [TimeDeltaAlphaCalculator], which always returns
 * the same alpha value, independent on the passed in age.
 *
 * This implementation is useful if old markers should not fade out.
 */
class ConstantTimeDeltaAlphaCalculator(
    /** The constant alpha value to be returned by this object. */
    val alpha: Float
) : TimeDeltaAlphaCalculator {
    override fun calculateAlpha(ageMillis: Long): Float = alpha
}

/**
 * A data class defining a range for an alpha calculation.
 *
 * The meaning of the properties of this class is that in a time range between
 * the maximum time of the previous range and the maximum time of this range,
 * the alpha value decreases from [alphaMax] to [alphaMin].
 */
data class AlphaRange(
    /** The maximum alpha value (at the beginning of the time range). */
    val alphaMax: Float,

    /** The minimum alpha value (at the end of the time range). */
    val alphaMin: Float,

    /** The end time of this range. */
    val timeMax: Long
)

/**
 * An implementation of [TimeDeltaAlphaCalculator] that calculates an alpha
 * value based on a list of [AlphaRange] objects.
 *
 * The calculator determines the first element in its range list, for which the
 * maximum time is larger than the time delta passed in. Then the properties of
 * this range are used to determine the alpha value. With this approach, alpha
 * values can decrease with different speeds in different, non-linear
 * intervals.
 */
class RangeTimeDeltaAlphaCalculator(
    /** The ranges to be used by this calculator (must be sorted). */
    val ranges: List<AlphaRange>,

    /**
     * The minimum alpha value. This value is returned if the time delta
     * exceeds the last range defined for this object.
     */
    val alphaMin: Float
) : TimeDeltaAlphaCalculator {
    override fun calculateAlpha(ageMillis: Long): Float {
        val rangeIdx = ranges.indexOfFirst { it.timeMax > ageMillis }
        return rangeIdx.takeIf { it >= 0 }?.let { idx ->
            val range = ranges[idx]
            val timeMin = if (idx > 0) ranges[idx - 1].timeMax
            else 0
            with(range) {
                (alphaMax - alphaMin) / (timeMin - timeMax) * (ageMillis - timeMin) + alphaMax
            }
        } ?: alphaMin
    }
}
