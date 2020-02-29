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
package com.github.oheger.locationteller.ui

import android.os.Handler
import android.view.Menu
import android.view.MenuItem
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.oheger.locationteller.R
import com.github.oheger.locationteller.map.LocationFileState
import com.github.oheger.locationteller.map.MapUpdater
import com.github.oheger.locationteller.server.ServerConfig
import com.github.oheger.locationteller.server.TimeData
import com.github.oheger.locationteller.server.TimeService
import com.github.oheger.locationteller.track.PreferencesHandler
import com.github.oheger.locationteller.track.TrackTestHelper
import com.google.android.gms.maps.GoogleMap
import io.kotlintest.shouldBe
import io.mockk.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test class for [[MapFragment]].
 */
@RunWith(AndroidJUnit4::class)
class MapFragmentSpec {
    @Test
    fun `server config is used to create MapUpdater`() {
        val scenario = launchFragmentInContainer<MapFragmentTestImplWithConfig>()

        scenario.onFragment { fragment ->
            fragment.configForMapUpdater shouldBe TrackTestHelper.defServerConfig
        }
    }

    @Test
    fun `MapUpdater is invoked when all dependencies are present`() {
        val nextState = LocationFileState(files = listOf("foo"), markerData = emptyMap())
        val scenario = launchFragmentInContainer<MapFragmentTestImplWithConfig>()

        scenario.onFragment { fragment ->
            fragment.expectMapUpdate(nextState)
            fragment.initMap()
            coVerify {
                fragment.mockMapUpdater.updateMap(
                    fragment.mockMap, LocationFileState(emptyList(), emptyMap()),
                    any(), referenceTime.currentTime
                )
                fragment.mockMapUpdater.zoomToAllMarkers(fragment.mockMap, nextState)
                fragment.mockMapUpdater.centerRecentMarker(fragment.mockMap, nextState)
            }
        }
    }

    @Test
    fun `update is scheduled periodically`() {
        val nextState = LocationFileState(files = listOf("foo_loc"), markerData = emptyMap())
        val scenario = launchFragmentInContainer<MapFragmentTestImplWithConfig>()

        scenario.onFragment { fragment ->
            fragment.expectMapUpdate(nextState)
            fragment.initMap()
            verify {
                fragment.handler.postAtTime(any(), MapFragment.updateToken, any())
            }
        }
    }

    @Test
    fun `menu items are disabled if not all dependencies are present`() {
        val disabledItems = listOf(R.id.item_updateMap, R.id.item_center, R.id.item_zoomArea)
        val mockMenu = createMockMenu()
        val scenario = launchFragmentInContainer<MapFragmentTestImplWithConfig>()

        scenario.onFragment { fragment ->
            fragment.onPrepareOptionsMenu(mockMenu.menu)
            verify {
                disabledItems.forEach { mockMenu[it].isEnabled = false }
            }
        }
    }

    @Test
    fun `menu items are enabled if markers are available`() {
        val enabledItems = listOf(R.id.item_updateMap, R.id.item_center, R.id.item_zoomArea)
        val mockMenu = createMockMenu()
        val nextState = LocationFileState(files = listOf("data"), markerData = emptyMap())
        val scenario = launchFragmentInContainer<MapFragmentTestImplWithConfig>()

        scenario.onFragment { fragment ->
            fragment.expectMapUpdate(nextState)
            fragment.initMap()
            fragment.onPrepareOptionsMenu(mockMenu.menu)
            verify {
                enabledItems.forEach { mockMenu[it].isEnabled = true }
            }
        }
    }

    @Test
    fun `some menu items are enabled only if markers are available`() {
        val mockMenu = createMockMenu()
        val nextState = LocationFileState(files = emptyList(), markerData = emptyMap())
        val scenario = launchFragmentInContainer<MapFragmentTestImplWithConfig>()

        scenario.onFragment { fragment ->
            fragment.expectMapUpdate(nextState)
            fragment.initMap()
            fragment.onPrepareOptionsMenu(mockMenu.menu)
            verify {
                mockMenu[R.id.item_updateMap].isEnabled = true
                mockMenu[R.id.item_center].isEnabled = false
                mockMenu[R.id.item_zoomArea].isEnabled = false
            }
        }
    }

    @Test
    fun `update map command can be executed`() {
        val mockMenu = createMockMenu()
        val initState = LocationFileState(emptyList(), emptyMap())
        val nextStates = listOf(
            LocationFileState(files = listOf("location"), markerData = emptyMap()),
            LocationFileState(files = listOf("loc1", "loc2"), markerData = emptyMap())
        )
        val scenario = launchFragmentInContainer<MapFragmentTestImplWithConfig>()

        scenario.onFragment { fragment ->
            coEvery { fragment.mockMapUpdater.updateMap(fragment.mockMap, any(), any(), any()) } returnsMany nextStates
            fragment.initMap()
            fragment.onOptionsItemSelected(mockMenu[R.id.item_updateMap])
            coVerify(exactly = 1) {
                fragment.mockMapUpdater.updateMap(fragment.mockMap, initState, any(), any())
                fragment.mockMapUpdater.updateMap(fragment.mockMap, nextStates[0], any(), any())
                fragment.mockMapUpdater.centerRecentMarker(fragment.mockMap, nextStates[0])
                fragment.mockMapUpdater.centerRecentMarker(fragment.mockMap, nextStates[1])
                fragment.mockMapUpdater.zoomToAllMarkers(fragment.mockMap, nextStates[0])
            }
            verify {
                fragment.handler.removeCallbacksAndMessages(MapFragment.updateToken)
            }
        }
    }

    @Test
    fun `center to last location is only done if the state changes`() {
        val mockMenu = createMockMenu()
        val nextState = LocationFileState(files = listOf("loc"), markerData = emptyMap())
        val scenario = launchFragmentInContainer<MapFragmentTestImplWithConfig>()

        scenario.onFragment { fragment ->
            fragment.expectMapUpdate(nextState)
            fragment.initMap()
            fragment.onOptionsItemSelected(mockMenu[R.id.item_updateMap])
            coVerify(exactly = 1) {
                fragment.mockMapUpdater.centerRecentMarker(fragment.mockMap, nextState)
            }
        }
    }

    @Test
    fun `auto center can be disabled`() {
        val mockMenu = createMockMenu()
        val itemAutoCenter = mockMenu[R.id.item_autoCenter]
        every { itemAutoCenter.isChecked } returns true
        val nextStates = listOf(
            LocationFileState(files = listOf("location"), markerData = emptyMap()),
            LocationFileState(files = listOf("loc1", "loc2"), markerData = emptyMap())
        )
        val scenario = launchFragmentInContainer<MapFragmentTestImplWithConfig>()

        scenario.onFragment { fragment ->
            coEvery { fragment.mockMapUpdater.updateMap(any(), any(), any(), any()) } returnsMany nextStates
            fragment.initMap()
            fragment.onOptionsItemSelected(mockMenu[R.id.item_autoCenter])
            fragment.onOptionsItemSelected(mockMenu[R.id.item_updateMap])
            coVerify(exactly = 0) {
                fragment.mockMapUpdater.centerRecentMarker(fragment.mockMap, nextStates[1])
            }
            verify {
                itemAutoCenter.isChecked = false
            }
        }
    }

    @Test
    fun `center to recent marker command can be executed`() {
        val mockMenu = createMockMenu()
        val nextState = LocationFileState(files = listOf("loc"), markerData = emptyMap())
        val scenario = launchFragmentInContainer<MapFragmentTestImplWithConfig>()

        scenario.onFragment { fragment ->
            fragment.expectMapUpdate(nextState)
            fragment.initMap()
            fragment.onOptionsItemSelected(mockMenu[R.id.item_center])
            coVerify(exactly = 2) {
                fragment.mockMapUpdater.centerRecentMarker(fragment.mockMap, nextState)
            }
        }
    }

    @Test
    fun `zoom to all markers command can be executed`() {
        val mockMenu = createMockMenu()
        val nextState = LocationFileState(files = listOf("loc"), markerData = emptyMap())
        val scenario = launchFragmentInContainer<MapFragmentTestImplWithConfig>()

        scenario.onFragment { fragment ->
            fragment.expectMapUpdate(nextState)
            fragment.initMap()
            fragment.onOptionsItemSelected(mockMenu[R.id.item_zoomArea])
            coVerify(exactly = 2) {
                fragment.mockMapUpdater.zoomToAllMarkers(fragment.mockMap, nextState)
            }
        }
    }

    @Test
    fun `missing server configuration is handled`() {
        val mockMenu = createMockMenu()
        val scenario = launchFragmentInContainer<MapFragmentTestImplWithNoConfig>()

        scenario.onFragment { fragment ->
            fragment.initMap()
            fragment.onPrepareOptionsMenu(mockMenu.menu)
            verify {
                mockMenu[R.id.item_updateMap].isEnabled = false
            }
            coVerify(exactly = 0) {
                fragment.mockMapUpdater.updateMap(any(), any(), any(), any())
            }
        }
    }

    @Test
    fun `updates are canceled on destroy`() {
        val scenario = launchFragmentInContainer<MapFragmentTestImplWithConfig>()

        scenario.onFragment { fragment ->
            scenario.moveToState(Lifecycle.State.DESTROYED)
            verify {
                fragment.handler.removeCallbacksAndMessages(MapFragment.updateToken)
            }
        }
    }

    companion object {
        /** A reference time to be returned by the time service per default. */
        val referenceTime = TimeData(20200228182652L)

        /**
         * A class storing the data of the mocked options menu. The class
         * stores both the menu mock and mocks for the items, keyed by their
         * IDs.
         *
         * @param menu the mock for the menu
         * @param items a map with the menu items
         */
        data class MockMenu(val menu: Menu, val items: Map<Int, MenuItem>) {
            operator fun get(id: Int): MenuItem =
                items[id] ?: throw NoSuchElementException("Unknown menu item: $id")
        }

        /**
         * Creates a _MockMenu_ object for the options menu of the map
         * fragment.
         * @return the _MockMenu_ for the items fragment
         */
        private fun createMockMenu(): MockMenu {
            val itemIds = listOf(
                R.id.item_updateMap, R.id.item_zoomArea, R.id.item_center,
                R.id.item_autoCenter
            )
            val itemMocks = itemIds.map { id ->
                val item = mockk<MenuItem>(relaxed = true)
                every { item.itemId } returns id
                item
            }
            val itemsMap = itemIds.zip(itemMocks).toMap()
            val menu = mockk<Menu>()
            itemsMap.forEach { entry ->
                every { menu.findItem(entry.key) } returns entry.value
            }
            return MockMenu(menu, itemsMap)
        }
    }
}

/**
 * A test implementation base class of _MapFragment_ that injects mock objects
 * for important dependencies.
 */
open class MapFragmentTestImpl(private val serverConfig: ServerConfig? = TrackTestHelper.defServerConfig) :
    MapFragment() {
    /** The mock for the preferences handler. */
    private val mockPrefHandler = createMockPrefHandler()

    /** The mock for the map updater. */
    val mockMapUpdater = createMockMapUpdater()

    /** The mock for the map managed by the fragment. */
    val mockMap = mockk<GoogleMap>()

    /** A spy for the handler used by the fragment. */
    lateinit var handler: Handler

    /** The mock time service. */
    private val mockTimeService = createMockTimeService()

    /** Stores the config used for the creation of the map updater. */
    var configForMapUpdater: ServerConfig? = null

    override fun createPreferencesHandler(): PreferencesHandler = mockPrefHandler

    override fun createMapUpdater(serverConfig: ServerConfig): MapUpdater {
        configForMapUpdater = serverConfig
        return mockMapUpdater
    }

    override fun createTimeService(): TimeService = mockTimeService

    override fun createHandler(): Handler {
        val orgHandler = super.createHandler()
        handler = spyk(orgHandler)
        return handler
    }

    /**
     * Initializes the map of the fragment.
     */
    fun initMap() {
        onMapReady(mockMap)
    }

    /**
     * Prepares the mock for the map updater to expect an invocation and return
     * the given next state.
     */
    fun expectMapUpdate(nextState: LocationFileState) {
        coEvery { mockMapUpdater.updateMap(any(), any(), any(), any()) } returns nextState
    }

    /**
     * Creates the mock preferences handler. It is prepared to create the
     * server config passed to the constructor and a default tracking config.
     * @return the mock preferences handler
     */
    private fun createMockPrefHandler(): PreferencesHandler {
        val handler = mockk<PreferencesHandler>()
        every { handler.createServerConfig() } returns serverConfig
        every { handler.createTrackConfig() } returns TrackTestHelper.defTrackConfig
        return handler
    }

    /**
     * Creates the mock map updater.
     * @return the updater
     */
    private fun createMockMapUpdater(): MapUpdater {
        return mockk(relaxed = true)
    }

    /**
     * Creates the mock time service and prepares it to return a standard
     * reference time.
     */
    private fun createMockTimeService(): TimeService {
        val service = mockk<TimeService>()
        every { service.currentTime() } returns MapFragmentSpec.referenceTime
        return service
    }
}

/**
 * An implementation of _MapFragment_ with a valid server configuration. Here
 * everything should be active.
 */
class MapFragmentTestImplWithConfig : MapFragmentTestImpl()

/**
 * An implementation of _MapFragment_ that simulates the scenario that no valid
 * server configuration is available. Here most actions should be disabled.
 */
class MapFragmentTestImplWithNoConfig : MapFragmentTestImpl(serverConfig = null)
