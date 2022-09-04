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

import com.github.oheger.locationteller.map.LocationFileState
import com.github.oheger.locationteller.map.MarkerData

import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.CameraPositionState

/**
 * A state class that wraps a [CameraPositionState] object.
 *
 * The wrapped [CameraPositionState] determines the position and the zoom level of the map view displayed in the
 * receiver UI. This class offers operations to manipulate this state.
 */
class ReceiverCameraState(
    /** The state object managed by this instance. */
    val cameraPositionState: CameraPositionState = CameraPositionState()
) {
    companion object {
        /**
         * Constant for the default zoom level. This is used when there are not enough markers available to calculate
         * a bounding box.
         */
        private const val DEFAULT_ZOOM_LEVEL = 15f

        /**
         * Return a new instance of [ReceiverCameraState] which wraps a new [CameraPositionState].
         */
        fun create(): ReceiverCameraState = ReceiverCameraState()
    }

    /**
     * Change the position of the managed state, so that the given [marker] is in the center. The zoom level is not
     * changed. If [marker] is *null*, this function has no effect.
     */
    fun centerMarker(marker: MarkerData?) {
        marker?.let {
            val update = CameraUpdateFactory.newLatLng(it.position)
            cameraPositionState.move(update)
        }
    }

    /**
     * Move the camera position, so that the recent marker of the given [state] is in the center. If [state] is empty,
     * this function has no effect.
     */
    fun centerRecentMarker(state: LocationFileState) {
        centerMarker(state.recentMarker())
    }

    /**
     * Change the zoom level and position of the managed camera so that all the markers in the given [state] are
     * visible. Special cases like an empty state or a state with only one location are handled properly.
     */
    fun zoomToAllMarkers(state: LocationFileState) {
        state.markerData.takeUnless { it.isEmpty() }?.let { markerData ->
            val update = if(markerData.size > 1) {
                val boundsBuilder = LatLngBounds.builder().apply {
                    markerData.forEach { include(it.value.position) }
                }
                CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 0)
            } else {
                CameraUpdateFactory.newLatLngZoom(markerData.values.single().position, DEFAULT_ZOOM_LEVEL)
            }

            cameraPositionState.move(update)
        }
    }
}
