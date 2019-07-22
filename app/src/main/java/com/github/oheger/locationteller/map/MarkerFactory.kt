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

import com.google.android.gms.maps.model.MarkerOptions

/**
 * A class for creating map markers from _MarkerData_ objects.
 *
 * This class is used by [MapUpdater] to generate the markers that are to be
 * added to the map.
 */
class MarkerFactory {
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
        return MarkerOptions().position(data.position)
    }
}
