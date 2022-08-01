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
package com.github.oheger.locationteller.ui

import android.location.Location
import android.os.Handler
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.oheger.locationteller.MockDispatcher
import com.github.oheger.locationteller.R
import com.github.oheger.locationteller.map.ConstantTimeDeltaAlphaCalculator
import com.github.oheger.locationteller.map.LocationFileState
import com.github.oheger.locationteller.map.MapMarkerState
import com.github.oheger.locationteller.map.MapUpdater
import com.github.oheger.locationteller.map.MarkerData
import com.github.oheger.locationteller.map.MarkerFactory
import com.github.oheger.locationteller.map.TimeDeltaAlphaCalculator
import com.github.oheger.locationteller.map.TimeDeltaFormatter
import com.github.oheger.locationteller.server.CurrentTimeService
import com.github.oheger.locationteller.server.LocationData
import com.github.oheger.locationteller.server.ServerConfig
import com.github.oheger.locationteller.server.TimeData
import com.github.oheger.locationteller.server.TimeService
import com.github.oheger.locationteller.track.LocationRetriever
import com.github.oheger.locationteller.track.LocationRetrieverFactory
import com.github.oheger.locationteller.config.PreferencesHandler
import com.github.oheger.locationteller.config.TrackServerConfig
import com.github.oheger.locationteller.track.TrackTestHelper
import com.github.oheger.locationteller.track.TrackTestHelper.asServerConfig
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.LatLng
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.CapturingSlot
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.slot
import io.mockk.spyk
import io.mockk.verify
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.coroutines.CoroutineContext

/**
 * Test class for [[MapFragment]].
 */
@RunWith(AndroidJUnit4::class)
class MapFragmentSpec {
    @Test
    fun `correct MapUpdater is created`() {
        val scenario = launchFragmentInContainer<MapFragmentTestImplWithConfig>()

        scenario.onFragment { fragment ->
            fragment.orgMapUpdater.serverConfig shouldBe TrackTestHelper.DEFAULT_SERVER_CONFIG.asServerConfig()
            val distTemplate = fragment.getString(R.string.map_distance)
            fragment.orgMapUpdater.distanceTemplate shouldBe distTemplate
        }
    }

    @Test
    fun `MapUpdater is invoked when all dependencies are present`() {
        val nextState = markerState("foo")
        val scenario = launchFragmentInContainer<MapFragmentTestImplWithConfig>()

        scenario.onFragment { fragment ->
            fragment.expectMapUpdate(nextState)
            fragment.initMap()
            coVerify {
                fragment.mockMapUpdater.updateMap(
                    fragment.mockMap, MapMarkerState(LocationFileState(emptyList(), emptyMap()), null),
                    null, any(), referenceTime.currentTime
                )
                fragment.mockMapUpdater.zoomToAllMarkers(fragment.mockMap, nextState.locations)
                fragment.mockMapUpdater.centerRecentMarker(fragment.mockMap, nextState.locations)
            }
        }
    }

    @Test
    fun `update is scheduled periodically`() {
        val nextState = markerState("foo_loc")
        val scenario = launchFragmentInContainer<MapFragmentTestImplWithConfig>()

        scenario.onFragment { fragment ->
            fragment.expectMapUpdate(nextState)
            fragment.initMap()
            verify {
                fragment.handler.postAtTime(any(), MapFragment.UPDATE_TOKEN, any())
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
        val nextState = markerState("data")
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
        val nextState = markerState()
        val scenario = launchFragmentInContainer<MapFragmentTestImplWithConfig>()

        scenario.onFragment { fragment ->
            fragment.expectMapUpdate(nextState)
            fragment.initMap()
            fragment.onPrepareOptionsMenu(mockMenu.menu)
            verify {
                mockMenu[R.id.item_updateMap].isEnabled = true
                mockMenu[R.id.item_own_location].isEnabled = true
                mockMenu[R.id.item_center].isEnabled = false
                mockMenu[R.id.item_zoomArea].isEnabled = false
            }
        }
    }

    @Test
    fun `update map command can be executed`() {
        val mockMenu = createMockMenu()
        val nextStates = listOf(
            markerState("location"),
            markerState("loc1", "loc2")
        )
        val scenario = launchFragmentInContainer<MapFragmentTestImplWithConfig>()

        scenario.onFragment { fragment ->
            coEvery {
                fragment.mockMapUpdater.updateMap(
                    fragment.mockMap,
                    any(),
                    any(),
                    any(),
                    any()
                )
            } returnsMany nextStates
            fragment.initMap()
            fragment.onOptionsItemSelected(mockMenu[R.id.item_updateMap])
            coVerify(exactly = 1) {
                fragment.mockMapUpdater.updateMap(fragment.mockMap, initMarkerState, any(), any(), any())
                fragment.mockMapUpdater.updateMap(fragment.mockMap, nextStates[0], any(), any(), any())
                fragment.mockMapUpdater.centerRecentMarker(fragment.mockMap, nextStates[0].locations)
                fragment.mockMapUpdater.centerRecentMarker(fragment.mockMap, nextStates[1].locations)
                fragment.mockMapUpdater.zoomToAllMarkers(fragment.mockMap, nextStates[0].locations)
            }
            verify {
                fragment.handler.removeCallbacksAndMessages(MapFragment.UPDATE_TOKEN)
            }
        }
    }

    @Test
    fun `center to last location is only done if the state changes`() {
        val mockMenu = createMockMenu()
        val nextState = markerState("loc")
        val scenario = launchFragmentInContainer<MapFragmentTestImplWithConfig>()

        scenario.onFragment { fragment ->
            fragment.expectMapUpdate(nextState)
            fragment.initMap()
            fragment.onOptionsItemSelected(mockMenu[R.id.item_updateMap])
            coVerify(exactly = 1) {
                fragment.mockMapUpdater.centerRecentMarker(fragment.mockMap, nextState.locations)
            }
        }
    }

    @Test
    fun `auto center can be disabled`() {
        val mockMenu = createMockMenu()
        val itemAutoCenter = mockMenu[R.id.item_autoCenter]
        every { itemAutoCenter.isChecked } returns true
        val nextStates = listOf(
            markerState("location"),
            markerState("loc1", "loc2")
        )
        val scenario = launchFragmentInContainer<MapFragmentTestImplWithConfig>()

        scenario.onFragment { fragment ->
            coEvery { fragment.mockMapUpdater.updateMap(any(), any(), any(), any(), any()) } returnsMany nextStates
            fragment.initMap()
            fragment.onOptionsItemSelected(mockMenu[R.id.item_autoCenter])
            fragment.onOptionsItemSelected(mockMenu[R.id.item_updateMap])
            coVerify(exactly = 0) {
                fragment.mockMapUpdater.centerRecentMarker(fragment.mockMap, nextStates[1].locations)
            }
            verify {
                itemAutoCenter.isChecked = false
            }
        }
    }

    @Test
    fun `center to recent marker command can be executed`() {
        val mockMenu = createMockMenu()
        val nextState = markerState("loc")
        val scenario = launchFragmentInContainer<MapFragmentTestImplWithConfig>()

        scenario.onFragment { fragment ->
            fragment.expectMapUpdate(nextState)
            fragment.initMap()
            fragment.onOptionsItemSelected(mockMenu[R.id.item_center])
            coVerify(exactly = 2) {
                fragment.mockMapUpdater.centerRecentMarker(fragment.mockMap, nextState.locations)
            }
        }
    }

    @Test
    fun `zoom to all markers command can be executed`() {
        val mockMenu = createMockMenu()
        val nextState = markerState("loc")
        val scenario = launchFragmentInContainer<MapFragmentTestImplWithConfig>()

        scenario.onFragment { fragment ->
            fragment.expectMapUpdate(nextState)
            fragment.initMap()
            fragment.onOptionsItemSelected(mockMenu[R.id.item_zoomArea])
            coVerify(exactly = 2) {
                fragment.mockMapUpdater.zoomToAllMarkers(fragment.mockMap, nextState.locations)
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
                mockMenu[R.id.item_own_location].isEnabled = false
            }
            coVerify(exactly = 0) {
                fragment.mockMapUpdater.updateMap(any(), any(), any(), any(), any())
            }
        }
    }

    @Test
    fun `updates are canceled on destroy`() {
        val scenario = launchFragmentInContainer<MapFragmentTestImplWithConfig>()

        scenario.onFragment { fragment ->
            scenario.moveToState(Lifecycle.State.DESTROYED)
            verify {
                fragment.handler.removeCallbacksAndMessages(MapFragment.UPDATE_TOKEN)
            }
        }
    }

    @Test
    fun `center to own position command is disabled if own location is unknown`() {
        val mockMenu = createMockMenu()
        val scenario = launchFragmentInContainer<MapFragmentTestImplWithConfig>()

        scenario.onFragment { fragment ->
            fragment.onPrepareOptionsMenu(mockMenu.menu)
            verify {
                mockMenu[R.id.item_center_own_location].isEnabled = false
            }
        }
    }

    @Test
    fun `show own location command is wrapped by a permission action`() {
        mockkObject(LocationPermAction)
        val mockPermAction = mockk<LocationPermAction>()
        every { LocationPermAction.create(any(), any(), any()) } returns mockPermAction
        every { mockPermAction.execute() } just runs

        val mockMenu = createMockMenu()
        val scenario = launchFragmentInContainer<MapFragmentTestImplWithConfig>()

        scenario.onFragment { fragment ->
            fragment.initMap()
            fragment.onOptionsItemSelected(mockMenu[R.id.item_own_location])
            fragment.onPrepareOptionsMenu(mockMenu.menu)
            fragment.onOptionsItemSelected(mockMenu[R.id.item_updateMap])
            verify {
                mockPermAction.execute()
            }
        }
    }

    @Test
    fun `the location permission action is correctly configured`() {
        mockkObject(LocationPermAction)
        val mockPermAction = mockk<LocationPermAction>()
        val slotFragment = slot<Fragment>()
        val slotAction = slot<() -> Unit>()
        every { LocationPermAction.create(capture(slotFragment), capture(slotAction), any()) } returns mockPermAction

        val mockMenu = createMockMenu()
        val nextState1 = markerState("location")
        val nextState2 = markerState("loc1", "loc2")
        val scenario = launchFragmentInContainer<MapFragmentTestImplWithConfig>()

        scenario.onFragment { fragment ->
            coEvery {
                fragment.mockMapUpdater.updateMap(fragment.mockMap, initMarkerState, null, any(), any())
            } returns nextState1
            coEvery {
                fragment.mockMapUpdater.updateMap(fragment.mockMap, nextState1, ownMarker, any(), any())
            } returns nextState2
            coEvery {
                fragment.mockMapUpdater.updateMap(fragment.mockMap, nextState2, ownMarker, any(), any())
            } returns markerState("loc3", "loc4")
            coEvery { fragment.mockLocationRetriever.fetchLocation() } returns mockOwnLocation()

            fragment.initMap()
            slotFragment.captured shouldBe fragment
            slotAction.captured()
            fragment.onPrepareOptionsMenu(mockMenu.menu)
            fragment.onOptionsItemSelected(mockMenu[R.id.item_updateMap])

            verify {
                fragment.handler.removeCallbacksAndMessages(MapFragment.UPDATE_TOKEN)
                fragment.mockMapUpdater.centerMarker(fragment.mockMap, ownMarker)
                mockMenu[R.id.item_center_own_location].isEnabled = true
            }
            coVerify {
                fragment.mockMapUpdater.updateMap(fragment.mockMap, nextState1, ownMarker, any(), any())
                fragment.mockMapUpdater.updateMap(fragment.mockMap, nextState2, ownMarker, any(), any())
            }
        }
    }

    @Test
    fun `show own location command should handle the case that no location can be retrieved`() {
        val slotAction = captureActionSlot()
        val nextState = markerState("location")
        val scenario = launchFragmentInContainer<MapFragmentTestImplWithConfig>()
        mockkStatic(Toast::class)
        val toast = mockk<Toast>()
        every { toast.show() } just runs

        scenario.onFragment { fragment ->
            every {
                Toast.makeText(fragment.requireContext(), R.string.map_no_own_location, Toast.LENGTH_SHORT)
            } returns toast
            coEvery {
                fragment.mockMapUpdater.updateMap(fragment.mockMap, any(), null, any(), any())
            } returns nextState
            coEvery { fragment.mockLocationRetriever.fetchLocation() } returns null

            fragment.initMap()
            slotAction.captured()
            verify { toast.show() }
        }
    }

    @Test
    fun `center to own location command can be executed`() {
        val slotAction = captureActionSlot()
        val mockMenu = createMockMenu()
        val nextState1 = markerState("location")
        val nextState2 = markerState("loc1", "loc2")
        val scenario = launchFragmentInContainer<MapFragmentTestImplWithConfig>()

        scenario.onFragment { fragment ->
            coEvery {
                fragment.mockMapUpdater.updateMap(fragment.mockMap, initMarkerState, null, any(), any())
            } returns nextState1
            coEvery {
                fragment.mockMapUpdater.updateMap(fragment.mockMap, nextState1, ownMarker, any(), any())
            } returns nextState2
            coEvery { fragment.mockLocationRetriever.fetchLocation() } returns mockOwnLocation()
            fragment.initMap()
            slotAction.captured()

            fragment.onOptionsItemSelected(mockMenu[R.id.item_center_own_location])
            verify(exactly = 2) {
                fragment.mockMapUpdater.centerMarker(fragment.mockMap, ownMarker)
            }
        }
    }

    @Test
    fun `update map command also updates the own location marker`() {
        val slotAction = captureActionSlot()
        val mockMenu = createMockMenu()
        val nextState1 = markerState("location")
        val nextState2 = markerState("loc1", "loc2")
        val scenario = launchFragmentInContainer<MapFragmentTestImplWithConfig>()

        scenario.onFragment { fragment ->
            coEvery {
                fragment.mockMapUpdater.updateMap(fragment.mockMap, initMarkerState, null, any(), any())
            } returns nextState1
            coEvery {
                fragment.mockMapUpdater.updateMap(fragment.mockMap, nextState1, ownMarker, any(), any())
            } returns nextState2
            coEvery { fragment.mockLocationRetriever.fetchLocation() } returns mockOwnLocation()
            every { fragment.mockMapUpdater.centerMarker(fragment.mockMap, any()) } just runs
            fragment.initMap()
            slotAction.captured()
            fragment.onOptionsItemSelected(mockMenu[R.id.item_updateMap])

            fragment.onOptionsItemSelected(mockMenu[R.id.item_center_own_location])
            coVerify {
                fragment.mockMapUpdater.updateMap(fragment.mockMap, nextState1, ownMarker, any(), any())
            }
        }
    }

    /**
     * Helper function to check the execution of a menu item related to the
     * fading mode.
     * @param itemId the ID of the menu item to be simulated
     */
    private fun checkFadingMode(itemId: Int) {
        val scenario = launchFragmentInContainer<MapFragmentTestImplWithConfig>()
        val expMarkerState = MapMarkerState(LocationFileState(emptyList(), emptyMap()), null)

        scenario.onFragment { fragment ->
            every { fragment.mockPrefHandler.setFadingMode(itemId) } just runs
            fragment.initMap()
            val noneItem = fragment.menu.findItem(R.id.item_fade_none)
            noneItem.isChecked shouldBe true
            val fadingItem = fragment.menu.findItem(itemId)
            fragment.onOptionsItemSelected(fadingItem)
            fragment.calculators shouldHaveSize 2
            val initCalc = fragment.calculators[0]
            initCalc.shouldBeInstanceOf<ConstantTimeDeltaAlphaCalculator>()
            initCalc.alpha shouldBe 1.0f
            fragment.calculators[1] shouldBe fragment.alphaCalculatorFor(itemId)
            fadingItem.isChecked shouldBe true
            verify {
                fragment.mockPrefHandler.setFadingMode(itemId)
            }
            coVerify {
                fragment.mockMapUpdater.updateMap(
                    fragment.mockMap, expMarkerState, null, any(), referenceTime.currentTime
                )
            }
        }
    }

    @Test
    fun `fading mode 'none' can be selected`() {
        checkFadingMode(R.id.item_fade_none)
    }

    @Test
    fun `fading mode 'slow' can be selected`() {
        checkFadingMode(R.id.item_fade_slow)
    }

    @Test
    fun `fading mode 'fast' can be selected`() {
        checkFadingMode(R.id.item_fade_fast)
    }

    @Test
    fun `fading mode 'fast and strong' can be selected`() {
        checkFadingMode(R.id.item_fade_fast_strong)
    }

    @Test
    fun `fading mode 'slow and strong' can be selected`() {
        checkFadingMode(R.id.item_fade_slow_strong)
    }

    @Test
    fun `fading mode is initialized from preferences`() {
        val selectedFadingMode = R.id.item_fade_slow_strong
        val scenario = launchFragmentInContainer<MapFragmentTestImplWithFadingMode>()

        scenario.onFragment { fragment ->
            fragment.calculators[0] shouldBe fragment.alphaCalculatorFor(selectedFadingMode)
            fragment.menu.findItem(selectedFadingMode).isChecked shouldBe true
        }
    }

    companion object {
        /** A reference time to be returned by the time service per default. */
        val referenceTime = TimeData(20200228182652L)

        /** The latitude of the own location. */
        private const val OWN_LAT = 33.12

        /** The longitude of the own location. */
        private const val OWN_LNG = 7.77

        /** A marker representing the own location. */
        private val ownMarker = MarkerData(LocationData(OWN_LAT, OWN_LNG, referenceTime), LatLng(OWN_LAT, OWN_LNG))

        /** The expected initial map marker state. */
        private val initMarkerState = MapMarkerState(LocationFileState(emptyList(), emptyMap()), null)

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
                R.id.item_autoCenter, R.id.item_own_location, R.id.item_center_own_location,
                R.id.item_fade_none, R.id.item_fade_fast, R.id.item_fade_slow,
                R.id.item_fade_fast_strong, R.id.item_fade_slow_strong
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

        /**
         * Creates a mock _Location_ with the coordinates of the own location.
         * @return the mock _Location_ object
         */
        private fun mockOwnLocation(): Location {
            val ownLoc = mockk<Location>()
            every { ownLoc.latitude } returns OWN_LAT
            every { ownLoc.longitude } returns OWN_LNG
            return ownLoc
        }

        /**
         * Convenience function to create a _MapMarkerState_ object from the
         * given parameters.
         * @param locFiles list with location file names
         * @return the resulting _MapMarkerState_
         */
        private fun markerState(vararg locFiles: String): MapMarkerState =
            MapMarkerState(LocationFileState(listOf(*locFiles), emptyMap()), mockk())

        /**
         * Return a [CapturingSlot] for the action to be executed when the
         * location permission is granted.
         */
        private fun captureActionSlot(): CapturingSlot<() -> Unit> {
            mockkObject(LocationPermAction)
            val mockPermAction = mockk<LocationPermAction>()
            val slotAction = slot<() -> Unit>()
            every { LocationPermAction.create(any(), capture(slotAction), any()) } returns mockPermAction
            return slotAction
        }
    }
}

/**
 * A test implementation base class of _MapFragment_ that injects mock objects
 * for important dependencies.
 */
open class MapFragmentTestImpl(private val serverConfig: TrackServerConfig = TrackTestHelper.DEFAULT_SERVER_CONFIG) :
    MapFragment() {
    override val coroutineContext: CoroutineContext = MockDispatcher()

    /** The mock for the preferences handler. */
    val mockPrefHandler = createMockPrefHandler()

    /** The mock for the map updater. */
    val mockMapUpdater = createMockMapUpdater()

    /** The mock for the map managed by the fragment. */
    val mockMap = mockk<GoogleMap>()

    /** The mock location retriever. */
    val mockLocationRetriever = mockk<LocationRetriever>()

    /** Records the calculators used to create a marker factory. */
    val calculators = mutableListOf<TimeDeltaAlphaCalculator>()

    /** A spy for the handler used by the fragment. */
    lateinit var handler: Handler

    /** The mock time service. */
    private val mockTimeService = createMockTimeService()

    /** Stores the original map updater that was created. */
    lateinit var orgMapUpdater: MapUpdater

    /** The menu of the fragment. */
    lateinit var menu: Menu

    override fun createPreferencesHandler(): PreferencesHandler = mockPrefHandler

    override fun createMapUpdater(serverConfig: ServerConfig): MapUpdater {
        orgMapUpdater = super.createMapUpdater(serverConfig)
        return mockMapUpdater
    }

    override fun createTimeService(): TimeService {
        super.createTimeService() shouldBe CurrentTimeService
        return mockTimeService
    }

    override fun createHandler(): Handler {
        val orgHandler = super.createHandler()
        handler = spyk(orgHandler)
        return handler
    }

    override fun createLocationRetrieverFactory(): LocationRetrieverFactory {
        val factory = mockk<LocationRetrieverFactory>()
        every {
            factory.createRetriever(
                requireContext(),
                TrackTestHelper.DEFAULT_TRACK_CONFIG,
                false
            )
        } returns mockLocationRetriever
        return factory
    }

    override fun createMarkerFactory(
        deltaFormatter: TimeDeltaFormatter,
        calculator: TimeDeltaAlphaCalculator
    ): MarkerFactory {
        calculators.add(calculator)
        return super.createMarkerFactory(deltaFormatter, calculator)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        this.menu = menu
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
    fun expectMapUpdate(nextState: MapMarkerState) {
        coEvery { mockMapUpdater.updateMap(any(), any(), any(), any(), any()) } returns nextState
    }

    /**
     * Creates the mock preferences handler. It is prepared to create the
     * server config passed to the constructor and a default tracking config.
     * @return the mock preferences handler
     */
    private fun createMockPrefHandler(): PreferencesHandler {
        val handler = mockk<PreferencesHandler>()
        every { handler.getFadingMode() } returns 0
        TrackTestHelper.prepareTrackConfigFromPreferences(handler)
        TrackTestHelper.prepareTrackServerConfigFromPreferences(handler, serverConfig)
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
open class MapFragmentTestImplWithConfig : MapFragmentTestImpl()

/**
 * An implementation of _MapFragment_ that simulates the scenario that no valid
 * server configuration is available. Here most actions should be disabled.
 */
class MapFragmentTestImplWithNoConfig : MapFragmentTestImpl(serverConfig = TrackTestHelper.UNDEFINED_SERVER_CONFIG)

/**
 * An implementation of _MapFragment_ that prepares the mock preferences
 * handler to return a fading mode. This is used to check whether this mode is
 * initialized correctly.
 */
class MapFragmentTestImplWithFadingMode : MapFragmentTestImplWithConfig() {
    override fun createPreferencesHandler(): PreferencesHandler {
        val handler = super.createPreferencesHandler()
        every { handler.getFadingMode() } returns R.id.item_fade_slow_strong
        return handler
    }
}
