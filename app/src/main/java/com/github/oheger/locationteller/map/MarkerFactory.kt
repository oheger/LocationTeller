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

import com.github.oheger.locationteller.duration.TimeDeltaFormatter

import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.MarkerOptions

/**
 * A class for creating map markers from [MarkerData] objects.
 *
 * This class is used by the receiver UI to generate the markers that are to be added to the map.
 */
class MarkerFactory(
    /** The formatter for time deltas to generate labels. */
    val deltaFormatter: TimeDeltaFormatter,

    /** The calculator for the alpha values of position markers. */
    val alphaCalculator: TimeDeltaAlphaCalculator
) {
    /**
     * Create a [MarkerOptions] object for the given [data]. Use the given [zIndex] to determine the Z-order. With
     * [text] an additional text for the marker can be provided. Also, a [color] for the marker can be set. The
     * recent marker available is handled differently, since it is always assigned an alpha value of 1.0. Use the given
     * [recentMarker] flag to determine whether this is the recent marker. Consider the given [time] as the current
     * time; based on this, some properties of the marker are computed.
     */
    fun createMarker(
        data: MarkerData,
        time: Long,
        recentMarker: Boolean,
        zIndex: Float = 0f,
        text: String? = null,
        color: Float? = null
    ): MarkerOptions = MarkerOptions()
        .position(data.position)
        .title(createTitle(data, time))
        .snippet(text)
        .zIndex(zIndex)
        .alpha(calcAlpha(data, time, recentMarker))
        .icon(iconForColor(color))

    /**
     * Generate a title for the given [data] based on the age of this marker compared to the specified [time]. Use a
     * suitable time unit.
     */
    private fun createTitle(data: MarkerData, time: Long): String =
        deltaFormatter.formatTimeDeltaOrTime(
            time - data.locationData.time.currentTime,
            data.locationData.time
        )

    /**
     * Calculate an alpha value for the given [data] based on its age compared to the specified [time]. There are
     * different areas of alpha values corresponding to the time units. The most recent marker is always assigned an
     * alpha value of 1.0. Use the [isRecent] flag to determine whether this is the recent marker.
     */
    private fun calcAlpha(data: MarkerData, time: Long, isRecent: Boolean): Float =
        if (isRecent) 1f
        else {
            val delta = (time - data.locationData.time.currentTime)
            alphaCalculator.calculateAlpha(delta)
        }

    companion object {
        /**
         * Obtain a special marker icon if a [color] has been provided.
         */
        private fun iconForColor(color: Float?): BitmapDescriptor? =
            color?.let { BitmapDescriptorFactory.defaultMarker(it) }
    }
}
