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

import android.location.Location
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
import com.google.android.gms.maps.model.*
import io.kotest.core.listeners.TestListener
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
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
            val updater = MapUpdater(serverConfig, distanceTemplate)
            val service = updater.trackServiceFactory(serverConfig)
            service.davClient.config shouldBe serverConfig
        }

        "MapUpdater should not update anything if there is no change" {
            val map = mockk<GoogleMap>()
            val state = mockk<LocationFileState>()
            val service = createMockService()
            every { state.files } returns emptyList()
            every { state.stateChanged(serverFiles) } returns false

            invokeUpdater(map, state, service) shouldBe MapMarkerState(state, null)
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

            val newState = invokeUpdater(map, state, service, markerFactory = markerFactory).locations
            newState shouldBe createState(1..4)
            verify { map.clear() }
            actMarkers shouldHaveSize expMarkers.size
            extractMarkerOptions(actMarkers) shouldContainExactlyInAnyOrder expMarkers
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

            val newState = invokeUpdater(map, state, service, markerFactory = createRelaxedMarkerFactory()).locations
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

            val newState = invokeUpdater(map, state, service, markerFactory = createRelaxedMarkerFactory()).locations
            newState shouldBe createState(1..3)
        }

        "MapUpdater should handle the own location when updating the map" {
            val map = mockk<GoogleMap>()
            val state = createState(1..4)
            val ownLocation = createMarkerData(42)
            val lastLocation = state.recentMarker()!!
            val distance = 1249.51f
            val service = createMockService()
            val newFiles = createFiles(1..4)
            coEvery { service.readLocations(newFiles) } returns createLocationDataMap(1..4)
            every { map.clear() } just runs
            mockkStatic(Location::class)
            every {
                Location.distanceBetween(
                    lastLocation.locationData.latitude, lastLocation.locationData.longitude,
                    ownLocation.locationData.latitude, ownLocation.locationData.longitude, any()
                )
            } answers {
                val res = arg<FloatArray>(4)
                res[0] = distance
                res.size shouldBe 1
            }
            val markerOptionsOwn = mockk<MarkerOptions>()
            val markerFactory = mockk<MarkerFactory>()
            val expMarkers = prepareMarkerFactory(markerFactory, state).toMutableList()
            every {
                markerFactory.createMarker(
                    ownLocation, time, recentMarker = false,
                    zIndex = 5f, text = "1250 m", color = BitmapDescriptorFactory.HUE_GREEN
                )
            } returns markerOptionsOwn
            val actMarkers = trackAddedMarkers(map)
            expMarkers.add(markerOptionsOwn)
            MockDispatcher.installAsMain()

            val nextState = invokeUpdater(
                map, LocationFileState(emptyList(), emptyMap()), service, markerFactory = markerFactory,
                ownLocation = ownLocation
            )
            extractMarkerOptions(actMarkers) shouldContainExactlyInAnyOrder expMarkers
            val ownMarker = actMarkers.last().second
            nextState.ownMarker shouldBe ownMarker
        }

        "MapUpdater should handle the own location if there are no locations from the server" {
            val map = mockk<GoogleMap>()
            val ownLocation = createMarkerData(42)
            val service = mockk<TrackService>()
            coEvery { service.filesOnServer() } returns emptyList()
            val markerOptionsOwn = mockk<MarkerOptions>()
            val markerFactory = mockk<MarkerFactory>()
            every {
                markerFactory.createMarker(
                    ownLocation, time, recentMarker = false,
                    zIndex = 1f, color = BitmapDescriptorFactory.HUE_GREEN
                )
            } returns markerOptionsOwn
            val actMarkers = trackAddedMarkers(map)
            MockDispatcher.installAsMain()

            val nextState = invokeUpdater(
                map, LocationFileState(emptyList(), emptyMap()), service, markerFactory = markerFactory,
                ownLocation = ownLocation
            )
            extractMarkerOptions(actMarkers) shouldContainExactlyInAnyOrder listOf(markerOptionsOwn)
            val ownMarker = actMarkers.last().second
            nextState.ownMarker shouldBe ownMarker
        }

        "MapUpdater should remove the old marker for the own position" {
            val map = mockk<GoogleMap>()
            val ownLocation = createMarkerData(42)
            val service = mockk<TrackService>()
            coEvery { service.filesOnServer() } returns emptyList()
            val ownMarker = mockk<Marker>()
            every { ownMarker.remove() } just runs
            val markerFactory = mockk<MarkerFactory>()
            every {
                markerFactory.createMarker(
                    any(), time, recentMarker = any(), zIndex = any(),
                    color = any(), text = any()
                )
            } returns mockk()
            every { map.addMarker(any()) } returns mockk()
            MockDispatcher.installAsMain()

            invokeUpdater(
                map, LocationFileState(emptyList(), emptyMap()), service, ownLocation = ownLocation,
                ownMarker = ownMarker, markerFactory = markerFactory
            )
            verify { ownMarker.remove() }
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
            val updater = MapUpdater(serverConfig, distanceTemplate)

            updater.zoomToAllMarkers(map, state)
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
            val updater = MapUpdater(serverConfig, distanceTemplate)

            updater.zoomToAllMarkers(map, state)
            verify { map.moveCamera(update) }
        }

        "MapUpdater should ignore a request to zoom when the state is empty" {
            val state = LocationFileState(emptyList(), emptyMap())
            val map = mockk<GoogleMap>()
            val updater = MapUpdater(serverConfig, distanceTemplate)

            updater.zoomToAllMarkers(map, state)  // no interactions with mocks
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
            val updater = MapUpdater(serverConfig, distanceTemplate)

            updater.centerRecentMarker(map, state)
            verify { map.moveCamera(update) }
        }

        "MapUpdater should ignore a center request for an empty state" {
            val state = LocationFileState(emptyList(), emptyMap())
            val map = mockk<GoogleMap>()
            val updater = MapUpdater(serverConfig, distanceTemplate)

            updater.centerRecentMarker(map, state)  // no interactions with mocks
        }
    }

    companion object {
        /** Constant for the current time.*/
        private const val time = 20190722181704L

        /** The template to generate the distance information. */
        private const val distanceTemplate = "%d m"

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
         * @param ownLocation optional _MarkerData_ for the own location
         * @param ownMarker optional marker for the own location
         * @return the result of the invocation
         */
        private suspend fun invokeUpdater(
            map: GoogleMap, currentState: LocationFileState, service: TrackService,
            markerFactory: MarkerFactory? = null, ownLocation: MarkerData? = null,
            ownMarker: Marker? = null
        ):
                MapMarkerState {
            val serviceFactory: (ServerConfig) -> TrackService = { config ->
                config shouldBe serverConfig
                service
            }
            val currentMapState = MapMarkerState(currentState, ownMarker)
            val currentMarkerFactory = markerFactory ?: createMarkerFactory(currentState)
            val updater = MapUpdater(serverConfig, distanceTemplate, serviceFactory)
            return updater.updateMap(map, currentMapState, ownLocation, currentMarkerFactory, time)
        }

        /**
         * Prepares the given map object to expect markers to be added. Each
         * marker that has been added is later available in the list returned
         * by this function, as well as the options used to obtain the marker.
         * @param map the mock for the map
         * @return a list storing the markers and their options that have been
         * added
         */
        private fun trackAddedMarkers(map: GoogleMap): List<Pair<MarkerOptions, Marker>> {
            val markers = mutableListOf<Pair<MarkerOptions, Marker>>()
            every { map.addMarker(any()) } answers {
                val options = arg<MarkerOptions>(0)
                val marker = mockk<Marker>()
                markers.add(Pair(options, marker))
                marker
            }
            return markers
        }

        /**
         * Obtains only the marker options that were used to add markers to the
         * test map.
         * @param data the list with marker options and markers
         * @return a list containing only the marker options
         */
        private fun extractMarkerOptions(data: List<Pair<MarkerOptions, Marker>>): List<MarkerOptions> =
            data.unzip().first

        /**
         * Prepares a mock for a marker factory to create markers for the given
         * state. For each marker a mock options is generated. The list
         * returned contains exactly these mock marker options.
         */
        private fun prepareMarkerFactory(factory: MarkerFactory, state: LocationFileState): List<MarkerOptions> {
            val markerOptions = mutableListOf<MarkerOptions>()
            state.files.withIndex().forEach { item ->
                val data = state.markerData[item.value]
                if (data != null) {
                    val recent = item.index == state.files.size - 1
                    val option = mockk<MarkerOptions>()
                    every {
                        factory.createMarker(
                            data,
                            time,
                            recent,
                            zIndex = item.index.toFloat()
                        )
                    } returns option
                    markerOptions.add(option)
                }
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
            every { factory.createMarker(any(), time, any(), any()) } returns mockk()
            return factory
        }
    }
}
