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

import com.github.oheger.locationteller.MockDispatcher
import com.github.oheger.locationteller.ResetDispatcherListener
import com.github.oheger.locationteller.map.LocationTestHelper.createFiles
import com.github.oheger.locationteller.map.LocationTestHelper.createLocationDataMap
import com.github.oheger.locationteller.map.LocationTestHelper.createMarkerDataMap
import com.github.oheger.locationteller.map.LocationTestHelper.createState
import com.github.oheger.locationteller.server.ServerConfig
import com.github.oheger.locationteller.server.TrackService
import com.google.android.gms.maps.GoogleMap
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
            service.davClient().config shouldBe serverConfig
        }

        "MapUpdater should not update anything if there is not change" {
            val map = mockk<GoogleMap>()
            val state = mockk<LocationFileState>()
            val service = createMockService()
            every { state.stateChanged(serverFiles) } returns false

            invokeUpdater(map, state, service) shouldBe state
        }

        "MapUpdater should update the map as necessary" {
            val map = mockk<GoogleMap>()
            val state = createState(1..2)
            val service = createMockService()
            val newFiles = createFiles(3..4)
            val expMarkers = createMarkerDataMap(1..4)
            coEvery { service.readLocations(newFiles) } returns createLocationDataMap(3..4)
            every { map.clear() } just runs
            val markers = trackAddedMarkers(map)
            val mockDispatcher = MockDispatcher.installAsMain()

            val newState = invokeUpdater(map, state, service)
            newState shouldBe createState(1..4)
            verify { map.clear() }
            markers shouldHaveSize expMarkers.size
            val positions = markers.map { it.position }
            val expPositions = expMarkers.values.map { it.position }
            positions shouldContainExactlyInAnyOrder expPositions
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

            val newState = invokeUpdater(map, state, service)
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

            val newState = invokeUpdater(map, state, service)
            newState shouldBe createState(1..3)
        }
    }

    companion object {
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
         * @return the result of the invocation
         */
        private suspend fun invokeUpdater(map: GoogleMap, currentState: LocationFileState, service: TrackService):
                LocationFileState {
            val serviceFactory: (ServerConfig) -> TrackService = { config ->
                config shouldBe serverConfig
                service
            }
            return MapUpdater.updateMap(serverConfig, map, currentState, serviceFactory)
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
    }
}
