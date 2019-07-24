/*
 * Copyright 2019 The Developers.
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
import com.github.oheger.locationteller.R
import com.google.android.gms.maps.model.MarkerOptions

/**
 * A class for creating map markers from _MarkerData_ objects.
 *
 * This class is used by [MapUpdater] to generate the markers that are to be
 * added to the map. In order to assign time information to markers, different
 * temporal units are used. The names of these units are passed to the
 * constructor.
 */
class MarkerFactory(
    private val unitSec: String, private val unitMin: String, private val unitHour: String,
    private val unitDay: String
) {
    /**
     * Creates a _MarkerOptions_ object that corresponds to the given input
     * parameters. The _MarkerData_ that is the basis for the options to be
     * created is selected by the given key into a [LocationFileState] object.
     * So all relevant information about the state as a whole is available as
     * well.
     * @param state the current _LocationFileState_
     * @param key the key into the state (i.e. the path of the file)
     * @param time the current time
     */
    fun createMarker(state: LocationFileState, key: String, time: Long): MarkerOptions {
        val data = state.markerData[key] ?: throw IllegalArgumentException("Cannot resolve key $key in state $state!")
        return MarkerOptions()
            .position(data.position)
            .title(createTitle(data, time))
    }

    private fun createTitle(data: MarkerData, time: Long): String {
        val deltaSec = (time - data.locationData.time.currentTime) / 1000
        if (deltaSec < 60) {
            return "$deltaSec $unitSec"
        } else {
            val deltaMin = deltaSec / 60
            return if (deltaMin < 60) {
                "$deltaMin $unitMin"
            } else {
                val deltaHour = deltaMin / 60
                if (deltaHour < 24) {
                    "$deltaHour $unitHour"
                } else {
                    val deltaDay = deltaHour / 24
                    "$deltaDay $unitDay"
                }
            }
        }
    }

    companion object {
        /**
         * Creates a new instance of _MarkerFactory_ and initializes it from
         * the given context object.
         * @param context the context
         * @return the newly created instance
         */
        fun create(context: Context): MarkerFactory {
            val resources = context.resources
            return MarkerFactory(
                resources.getString(R.string.time_secs),
                resources.getString(R.string.time_minutes),
                resources.getString(R.string.time_hours),
                resources.getString(R.string.time_days)
            )
        }
    }
}
