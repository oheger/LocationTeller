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

import com.github.oheger.locationteller.MockDispatcher
import com.github.oheger.locationteller.ResetDispatcherListener
import com.github.oheger.locationteller.map.LocationTestHelper.createFiles
import com.github.oheger.locationteller.map.LocationTestHelper.createLocationData
import com.github.oheger.locationteller.map.LocationTestHelper.createLocationDataMap
import com.github.oheger.locationteller.map.LocationTestHelper.createMarkerData
import com.github.oheger.locationteller.map.LocationTestHelper.createMarkerDataMap
import com.github.oheger.locationteller.map.LocationTestHelper.createState
import com.github.oheger.locationteller.server.ServerConfig
import com.github.oheger.locationteller.server.TrackService
import com.google.android.gms.maps.CameraUpdate
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MarkerOptions
import io.kotlintest.extensions.TestListener
import io.kotlintest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotlintest.matchers.collections.shouldHaveSize
import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi

/**
 * Test class for [MapUpdater].
 */
@ExperimentalCoroutinesApi
class MapUpdaterSpec : StringSpec() {
    override fun listeners(): List<TestListener> = listOf(ResetDispatcherListener)

    init {
        "MapUpdater should define a default track server factory" {
            val service = MapUpdater.defaultTrackServerFactory(serverConfig)
            service.davClient.config shouldBe serverConfig
        }

        "MapUpdater should not update anything if there is not change" {
            val map = mockk<GoogleMap>()
            val state = mockk<LocationFileState>()
            val service = createMockService()
            every { state.files } returns emptyList()
            every { state.stateChanged(serverFiles) } returns false

            invokeUpdater(map, state, service) shouldBe state
        }

        "MapUpdater should update the map as necessary" {
            val map = mockk<GoogleMap>()
            val state = createState(1..2)
            val service = createMockService()
            val newFiles = createFiles(3..4)
            coEvery { service.readLocations(newFiles) } returns createLocationDataMap(3..4)
            every { map.clear() } just runs
            val markerFactory = mockk<MarkerFactory>()
            val expMarkers = prepareMarkerFactory(markerFactory, createState(1..4))
            val actMarkers = trackAddedMarkers(map)
            val mockDispatcher = MockDispatcher.installAsMain()

            val newState = invokeUpdater(map, state, service, markerFactory = markerFactory)
            newState shouldBe createState(1..4)
            verify { map.clear() }
            actMarkers shouldHaveSize expMarkers.size
            actMarkers shouldContainExactlyInAnyOrder expMarkers
            mockDispatcher.tasks shouldHaveSize 1
        }

        "MapUpdater should not call the track service if there are no new locations" {
            val map = mockk<GoogleMap>()
            val state = createState(0..4)
            val service = createMockService()
            val expMarkers = createMarkerDataMap(1..4)
            every { map.clear() } just runs
            val markers = trackAddedMarkers(map)
            MockDispatcher.installAsMain()

            val newState = invokeUpdater(map, state, service, markerFactory = createRelaxedMarkerFactory())
            newState shouldBe createState(1..4)
            verify { map.clear() }
            markers shouldHaveSize expMarkers.size
        }

        "MapUpdater should only add files to the new state that could be resolved" {
            val map = mockk<GoogleMap>()
            val state = createState(1..2)
            val service = createMockService()
            val newFiles = createFiles(3..4)
            coEvery { service.readLocations(newFiles) } returns createLocationDataMap(3..3)
            every { map.clear() } just runs
            trackAddedMarkers(map)
            MockDispatcher.installAsMain()

            val newState = invokeUpdater(map, state, service, markerFactory = createRelaxedMarkerFactory())
            newState shouldBe createState(1..3)
        }

        "MapUpdater should set a zoom level to view all markers in the given state object" {
            val minLat = 47.125
            val maxLat = 47.985
            val minLng = 8.1
            val maxLng = 8.75
            val positions = listOf(
                LatLng(minLat, 8.5), LatLng(47.5, minLng), LatLng(47.62, 8.65),
                LatLng(maxLat, 8.2), LatLng(47.9, maxLng)
            )
            val markerData = positions.withIndex().map { iv ->
                val locData = createLocationData(iv.index)  // only position is relevant
                MarkerData(locData, iv.value)
            }
            val markerMap = markerData.map { data ->
                Pair(data.locationData.stringRepresentation(), data)
            }.toMap()
            val state = LocationFileState(emptyList(), markerMap)  // only map is relevant
            val expBounds = LatLngBounds(LatLng(minLat, minLng), LatLng(maxLat, maxLng))
            mockkStatic(CameraUpdateFactory::class)
            val update = mockk<CameraUpdate>()
            val map = mockk<GoogleMap>()
            every { CameraUpdateFactory.newLatLngBounds(expBounds, 0) } returns update
            every { map.moveCamera(update) } just runs

            MapUpdater.zoomToAllMarkers(map, state)
            verify { map.moveCamera(update) }
        }

        "MapUpdater should handle the special case of only one marker when zooming in" {
            val markerData = createMarkerData(5)
            val markerDataMap = mapOf(markerData.locationData.stringRepresentation() to markerData)
            val state = LocationFileState(emptyList(), markerDataMap)  // only map is relevant
            mockkStatic(CameraUpdateFactory::class)
            val update = mockk<CameraUpdate>()
            val map = mockk<GoogleMap>()
            every { CameraUpdateFactory.newLatLngZoom(markerData.position, 15f) } returns update
            every { map.moveCamera(update) } just runs

            MapUpdater.zoomToAllMarkers(map, state)
            verify { map.moveCamera(update) }
        }

        "MapUpdater should ignore a request to zoom when the state is empty" {
            val state = LocationFileState(emptyList(), emptyMap())
            val map = mockk<GoogleMap>()

            MapUpdater.zoomToAllMarkers(map, state)  // not interactions with mocks
        }

        "MapUpdater should center the map to the most recent marker" {
            val state = createState(1..16)
            val zoomLevel = 16f
            val oldCameraPosition = CameraPosition.builder()
                .target(LatLng(47.0, 8.0))
                .zoom(zoomLevel)
                .build()
            val cameraPosition = CameraPosition.Builder()
                .target(state.recentMarker()?.position)
                .zoom(zoomLevel)
                .build()
            mockkStatic(CameraUpdateFactory::class)
            val update = mockk<CameraUpdate>()
            val map = mockk<GoogleMap>()
            every { map.cameraPosition } returns oldCameraPosition
            every { CameraUpdateFactory.newCameraPosition(cameraPosition) } returns update
            every { map.moveCamera(update) } just runs

            MapUpdater.centerRecentMarker(map, state)
            verify { map.moveCamera(update) }
        }

        "MapUpdater should ignore a center request for an empty state" {
            val state = LocationFileState(emptyList(), emptyMap())
            val map = mockk<GoogleMap>()

            MapUpdater.centerRecentMarker(map, state)  // not interactions with mocks
        }
    }

    companion object {
        /** Constant for the current time.*/
        private const val time = 20190722181704L

        /** A test server configuration used by this class.*/
        private val serverConfig = ServerConfig(
            serverUri = "https://test-track.org",
            basePath = "/my-tracks", user = "scott", password = "tiger"
        )

        /** A list simulating files retrieved from the server.*/
        private val serverFiles = createFiles(1..4)

        /**
         * Creates a mock track service that is prepared to return the default
         * server files.
         * @return the mock track service
         */
        private fun createMockService(): TrackService {
            val service = mockk<TrackService>()
            coEvery { service.filesOnServer() } returns serverFiles
            return service
        }

        /**
         * Helper function to invoke the updater with some default settings.
         * This function especially makes sure that the given mock track
         * service is used.
         * @param map the mock for the map
         * @param currentState the current file state
         * @param service the mock track service
         * @param markerFactory an optional marker factory
         * @return the result of the invocation
         */
        private suspend fun invokeUpdater(
            map: GoogleMap, currentState: LocationFileState, service: TrackService,
            markerFactory: MarkerFactory? = null
        ):
                LocationFileState {
            val serviceFactory: (ServerConfig) -> TrackService = { config ->
                config shouldBe serverConfig
                service
            }
            val currentMarkerFactory = markerFactory ?: createMarkerFactory(currentState)
            return MapUpdater.updateMap(serverConfig, map, currentState, currentMarkerFactory, time, serviceFactory)
        }

        /**
         * Prepares the given map object to expect markers to be added. Each
         * marker that has been added is later available in the list returned
         * by this function.
         * @param map the mock for the map
         * @return a list with the options for the markers that have been added
         */
        private fun trackAddedMarkers(map: GoogleMap): List<MarkerOptions> {
            val markers = mutableListOf<MarkerOptions>()
            every { map.addMarker(any()) } answers {
                markers.add(arg(0))
                null
            }
            return markers
        }

        /**
         * Prepares a mock for a marker factory to create markers for the given
         * state. For each marker a mock options is generated. The list
         * returned contains exactly these mock marker options.
         */
        private fun prepareMarkerFactory(factory: MarkerFactory, state: LocationFileState): List<MarkerOptions> {
            val markerOptions = mutableListOf<MarkerOptions>()
            state.files.forEach { file ->
                val option = mockk<MarkerOptions>()
                every { factory.createMarker(state, file, time) } returns option
                markerOptions.add(option)
            }
            return markerOptions
        }

        /**
         * Creates a mock marker factory that answers requests based on the
         * given state object. This function can be used if the concrete
         * options created by the factory are irrelevant.
         * @param state the state
         * @return the mock marker factory
         */
        private fun createMarkerFactory(state: LocationFileState): MarkerFactory {
            val factory = mockk<MarkerFactory>()
            prepareMarkerFactory(factory, state)
            return factory
        }

        /**
         * Creates a mock for a marker factory that does not check its input,
         * but always returns new mock marker options.
         * @return the 'relaxed' marker factory
         */
        private fun createRelaxedMarkerFactory(): MarkerFactory {
            val factory = mockk<MarkerFactory>()
            every { factory.createMarker(any(), any(), time) } returns mockk()
            return factory
        }
    }
}
