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

import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.MarkerOptions

/**
 * A class for creating map markers from _MarkerData_ objects.
 *
 * This class is used by [MapUpdater] to generate the markers that are to be
 * added to the map.
 *
 * @param deltaFormatter the formatter for time deltas
 */
class MarkerFactory(val deltaFormatter: TimeDeltaFormatter) {
    /**
     * Creates a _MarkerOptions_ object that corresponds to the given input
     * parameters.
     * @param data the _MarkerData_ for which a marker is to be created
     * @param time the current time
     * @param recentMarker flag whether this is the most recent marker
     * @param zIndex a Z-index for the marker; markers with a high Z-index are
     * drawn on top of markers with a low index
     * @param text optional additional text for the marker
     * @param color an optional marker color
     */
    fun createMarker(
        data: MarkerData, time: Long, recentMarker: Boolean, zIndex: Float = 0f,
        text: String? = null, color: Float? = null
    ): MarkerOptions {
        return MarkerOptions()
            .position(data.position)
            .title(createTitle(data, time))
            .snippet(text)
            .zIndex(zIndex)
            .alpha(calcAlpha(data, time, recentMarker))
            .icon(iconForColor(color))
    }

    /**
     * Generates a title for the given _MarkerData_ object based on the age of
     * this marker. Uses a suitable time unit.
     * @param data the _MarkerData_
     * @param time the reference time
     * @return a title for the marker
     */
    private fun createTitle(data: MarkerData, time: Long): String =
        deltaFormatter.formatTimeDelta(time - data.locationData.time.currentTime)

    /**
     * Calculates an alpha value for a marker based on its age. There are
     * different areas of alpha values corresponding to the time units. The
     * most recent marker is always assigned an alpha value of 1.0.
     * @param data the _MarkerData_
     * @param time the reference time
     * @param isRecent flag whether this is the most recent marker
     * @return an alpha value for this marker
     */
    private fun calcAlpha(data: MarkerData, time: Long, isRecent: Boolean): Float =
        if (isRecent) 1f
        else {
            val deltaMin = (time - data.locationData.time.currentTime) / 1000 / 60
            if (deltaMin < 60) 1.0f - (1.0f - AlphaMinutesMin) * (deltaMin / 60f)
            else {
                val deltaHour = deltaMin / 60
                if (deltaHour < 24) AlphaHoursMax - (AlphaHoursMax - AlphaHoursMin) * (deltaHour / 24f)
                else AlphaDays
            }
        }

    companion object {
        /** The minimum alpha value used for markers in the minute range. */
        const val AlphaMinutesMin = 0.55f

        /** The maximum alpha value used for markers in the hour range. */
        const val AlphaHoursMax = 0.5f

        /** The minimum alpha value used for markers in the hour range. */
        const val AlphaHoursMin = 0.2f

        /** The alpha value used for markers in the day range. */
        const val AlphaDays = 0.1f

        /**
         * Obtains a special marker icon if a color has been provided.
         * @param color the optional color of the marker
         * @return the icon to be used for the marker
         */
        private fun iconForColor(color: Float?): BitmapDescriptor? =
            color?.let { BitmapDescriptorFactory.defaultMarker(it) }
    }
}
