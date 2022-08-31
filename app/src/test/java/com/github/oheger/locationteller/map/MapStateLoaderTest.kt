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

import com.github.oheger.locationteller.server.TrackService

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

import io.mockk.coEvery
import io.mockk.mockk

/**
 * Test class for [MapStateLoader].
 */
class MapStateLoaderTest : StringSpec({
    "MapStateLoader should load data from the server" {
        val state = LocationTestHelper.createState(1..2)
        val service = createMockService()
        val newFiles = LocationTestHelper.createFiles(3..4)
        coEvery { service.readLocations(newFiles) } returns LocationTestHelper.createLocationDataMap(3..4)
        val loader = MapStateLoader(service)

        val newState = loader.loadMapState(state)

        newState shouldBe LocationTestHelper.createState(1..4)
    }

    "MapStateLoader should not call the track service if there are no new locations" {
        val state = LocationTestHelper.createState(0..4)
        val service = createMockService()
        val loader = MapStateLoader(service)

        val newState = loader.loadMapState(state)

        newState shouldBe LocationTestHelper.createState(1..4)
    }

    "MapStateLoader should only add files to the new state that could be resolved" {
        val state = LocationTestHelper.createState(1..2)
        val service = createMockService()
        val newFiles = LocationTestHelper.createFiles(3..4)
        coEvery { service.readLocations(newFiles) } returns LocationTestHelper.createLocationDataMap(3..3)
        val loader = MapStateLoader(service)

        val newState = loader.loadMapState(state)

        newState shouldBe LocationTestHelper.createState(1..3)
    }
})

/** A list simulating files retrieved from the server.*/
private val SERVER_FILES = LocationTestHelper.createFiles(1..4)

/**
 * Create a mock [TrackService] that is prepared to return the default server files.
 */
private fun createMockService(): TrackService =
    mockk<TrackService>().apply {
        coEvery { filesOnServer() } returns SERVER_FILES
    }
