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
import com.github.oheger.locationteller.map.LocationTestHelper
import com.github.oheger.locationteller.map.MarkerData

import com.google.android.gms.maps.CameraUpdate
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.CameraPositionState

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.verify

/**
 * Test class for [ReceiverCameraState].
 */
class ReceiverCameraStateSpec : WordSpec({
    beforeEach {
        mockkStatic(CameraUpdateFactory::class)
    }

    "centerMarker" should {
        "change the position to the given marker" {
            val marker = LocationTestHelper.createMarkerData(1)
            val update = mockk<CameraUpdate>()
            every { CameraUpdateFactory.newLatLng(marker.position)} returns update
            val cameraState = createCameraPositionStateMock()
            val state = ReceiverCameraState(cameraPositionState = cameraState)

            state.centerMarker(marker)

            verify {
                cameraState.move(update)
            }
        }

        "ignore an undefined marker" {
            val cameraState = mockk<CameraPositionState>()
            val state = ReceiverCameraState(cameraPositionState = cameraState)

            state.centerMarker(null)

            verify(exactly = 0) {
                cameraState.move(any())
            }
        }
    }

    "centerRecentMarker" should {
        "change the position to the recent marker of a given state" {
            val fileCount = 16
            val fileState = LocationTestHelper.createState(1..fileCount)
            val recentMarker = LocationTestHelper.createMarkerData(fileCount)
            val update = mockk<CameraUpdate>()
            every { CameraUpdateFactory.newLatLng(recentMarker.position) } returns update
            val cameraState = createCameraPositionStateMock()
            val state = ReceiverCameraState(cameraPositionState = cameraState)

            state.centerRecentMarker(fileState)

            verify {
                cameraState.move(update)
            }
        }

        "ignore a request with an empty state" {
            val cameraState = mockk<CameraPositionState>()
            val state = ReceiverCameraState(cameraPositionState = cameraState)

            state.centerRecentMarker(LocationFileState.EMPTY)

            verify(exactly = 0) {
                cameraState.move(any())
            }
        }
    }

    "zoomToAllMarkers" should {
        "set a zoom level to view all markers in the given state object" {
            val minLat = 47.125
            val maxLat = 47.985
            val minLng = 8.1
            val maxLng = 8.75
            val positions = listOf(
                LatLng(minLat, 8.5),
                LatLng(47.5, minLng),
                LatLng(47.62, 8.65),
                LatLng(maxLat, 8.2),
                LatLng(47.9, maxLng)
            )
            val markerData = positions.withIndex().map { (index, value) ->
                val locData = LocationTestHelper.createLocationData(index)  // only position is relevant
                MarkerData(locData, value)
            }
            val markerMap = markerData.associateBy { data ->
                data.locationData.stringRepresentation()
            }
            val fileState = LocationFileState(emptyList(), markerMap)  // only map is relevant
            val expBounds = LatLngBounds(LatLng(minLat, minLng), LatLng(maxLat, maxLng))
            val update = mockk<CameraUpdate>()
            every { CameraUpdateFactory.newLatLngBounds(expBounds, 0) } returns update
            val cameraState = createCameraPositionStateMock()
            val state = ReceiverCameraState(cameraPositionState = cameraState)

            state.zoomToAllMarkers(fileState)

            verify {
                cameraState.move(update)
            }
        }

        "handle the special case of only one marker when zooming in" {
            val markerData = LocationTestHelper.createMarkerData(5)
            val markerDataMap = mapOf(markerData.locationData.stringRepresentation() to markerData)
            val fileState = LocationFileState(emptyList(), markerDataMap)  // only map is relevant
            val update = mockk<CameraUpdate>()
            every { CameraUpdateFactory.newLatLngZoom(markerData.position, 15f) } returns update
            val cameraState = createCameraPositionStateMock()
            val state = ReceiverCameraState(cameraPositionState = cameraState)

            state.zoomToAllMarkers(fileState)

            verify {
                cameraState.move(update)
            }
        }

        "ignore a request to zoom when the state is empty" {
            val cameraState = mockk<CameraPositionState>()
            val state = ReceiverCameraState(cameraPositionState = cameraState)

            state.zoomToAllMarkers(LocationFileState.EMPTY)

            verify(exactly = 0) {
                cameraState.move(any())
            }
        }
    }

    "create" should {
        "create a new instance" {
            val state = ReceiverCameraState.create()

            state.cameraPositionState.position.target shouldBe LatLng(0.0, 0.0)
        }
    }
})

/**
 * Create a mock for a [CameraPositionState] object and prepare it to expect a [CameraPositionState.move] operation.
 */
private fun createCameraPositionStateMock(): CameraPositionState =
    mockk<CameraPositionState>().apply {
        every { move(any()) } just runs
    }
