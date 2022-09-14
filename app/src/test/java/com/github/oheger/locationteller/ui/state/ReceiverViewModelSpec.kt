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

import android.app.Application

import com.github.oheger.locationteller.R
import com.github.oheger.locationteller.config.ConfigManager
import com.github.oheger.locationteller.config.PreferencesHandler
import com.github.oheger.locationteller.config.ReceiverConfig
import com.github.oheger.locationteller.config.TrackServerConfig
import com.github.oheger.locationteller.map.DisabledFadeOutAlphaCalculator
import com.github.oheger.locationteller.map.LocationFileState
import com.github.oheger.locationteller.map.LocationTestHelper
import com.github.oheger.locationteller.map.MapStateLoader
import com.github.oheger.locationteller.map.MapStateUpdater
import com.github.oheger.locationteller.map.MarkerFactory
import com.github.oheger.locationteller.server.TimeData
import com.github.oheger.locationteller.server.TrackService
import com.github.oheger.locationteller.track.TrackTestHelper
import com.github.oheger.locationteller.track.TrackTestHelper.asServerConfig

import com.google.android.gms.maps.model.MarkerOptions
import com.google.maps.android.compose.CameraPositionState

import io.kotest.core.spec.style.WordSpec
import io.kotest.core.test.TestCase
import io.kotest.inspectors.forAll
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.slot
import io.mockk.spyk
import io.mockk.verify

/**
 * Test class for [ReceiverViewModel] and its default implementation.
 */
class ReceiverViewModelSpec : WordSpec() {
    /** The mock preferences handler. */
    private lateinit var preferencesHandler: PreferencesHandler

    /** The mock application context. */
    private lateinit var application: Application

    /** The mock for the config manager. */
    private lateinit var configManager: ConfigManager

    /** The mock for the [TrackService]. */
    private lateinit var trackService: TrackService

    /** The mock for the [MapStateUpdater]. */
    private lateinit var updater: MapStateUpdater

    /** The mock for the receiver camera state. */
    private lateinit var cameraState: ReceiverCameraState

    override suspend fun beforeAny(testCase: TestCase) {
        mockkObject(PreferencesHandler, TrackService, MapStateUpdater, ReceiverCameraState)
        application = createApplicationMock()
        preferencesHandler = createPreferencesHandlerMock()
        configManager = createConfigManager()
        trackService = mockk()
        updater = mockk()
        cameraState = mockk()

        every { PreferencesHandler.getInstance(application) } returns preferencesHandler
        every { TrackService.create(TrackTestHelper.DEFAULT_SERVER_CONFIG.asServerConfig()) } returns trackService
        every { MapStateUpdater.create(any(), any(), any(), any(), any()) } returns updater
        every { updater.close() } just runs
        every { ReceiverCameraState.create() } returns cameraState
    }

    init {
        "onCleared" should {
            "perform cleanup" {
                val model = createModel()
                val receiveConfigChangeListener = receiverConfigChangeListener()
                val serverConfigChangeListener = serverConfigChangeListener()

                model.clear()

                verify {
                    configManager.removeReceiverConfigChangeListener(receiveConfigChangeListener)
                    configManager.removeServerConfigChangeListener(serverConfigChangeListener)
                    updater.close()
                }
            }
        }

        "receiverConfig" should {
            "return a configuration initialized from preferences" {
                val model = createModel()

                model.receiverConfig shouldBe RECEIVER_CONFIG
            }
        }

        "updateReceiverConfig" should {
            "pass the new configuration to the config manager" {
                val newConfig = ReceiverConfig(
                    updateInterval = 600,
                    fadeOutEnabled = true,
                    centerNewPosition = false,
                    fastFadeOut = true,
                    strongFadeOut = false
                )
                every { configManager.updateReceiverConfig(application, newConfig) } just runs
                val model = createModel()

                model.updateReceiverConfig(newConfig)

                verify { configManager.updateReceiverConfig(application, newConfig) }
            }

            "react on change notifications" {
                val newConfig = RECEIVER_CONFIG.copy(updateInterval = 222)
                val model = createModel()

                receiverConfigChangeNotification(newConfig)

                model.receiverConfig shouldBe newConfig
            }
        }

        "the markerFactory" should {
            "be configured with a disabled alpha calculator" {
                val model = createModel()

                receiverConfigChangeNotification(RECEIVER_CONFIG.copy(fadeOutEnabled = false))

                model.markerFactory.alphaCalculator shouldBe DisabledFadeOutAlphaCalculator
            }

            "be configured with a strong and slow alpha calculator" {
                val model = createModel()

                model.markerFactory.alphaCalculator shouldBe ReceiverViewModelImpl.CALCULATOR_SLOW_STRONG
            }

            "be configured with a normal and slow alpha calculator" {
                val model = createModel()

                receiverConfigChangeNotification(RECEIVER_CONFIG.copy(strongFadeOut = false))

                model.markerFactory.alphaCalculator shouldBe ReceiverViewModelImpl.CALCULATOR_SLOW
            }

            "be configured with a normal and fast alpha calculator" {
                val model = createModel()

                receiverConfigChangeNotification(RECEIVER_CONFIG.copy(strongFadeOut = false, fastFadeOut = true))

                model.markerFactory.alphaCalculator shouldBe ReceiverViewModelImpl.CALCULATOR_FAST
            }

            "be configured with a strong and fast alpha calculator" {
                val model = createModel()

                receiverConfigChangeNotification(RECEIVER_CONFIG.copy(fastFadeOut = true))

                model.markerFactory.alphaCalculator shouldBe ReceiverViewModelImpl.CALCULATOR_FAST_STRONG
            }

            "be configured with a correct delta formatter" {
                val model = createModel()

                val formatter = model.markerFactory.deltaFormatter

                formatter.unitDay shouldBe UNIT_DAY
                formatter.unitHour shouldBe UNIT_HOUR
                formatter.unitMin shouldBe UNIT_MINUTE
                formatter.unitSec shouldBe UNIT_SECOND
            }
        }

        "the loader provider" should {
            "return an initial loader" {
                createModel()

                val creation = MapStateUpdaterCreation.fetch()

                creation.loader.trackService shouldBe trackService
            }

            "update the loader when there is a change on the server config" {
                val newServerConfig = TrackTestHelper.DEFAULT_SERVER_CONFIG.copy(user = "otherUser")
                val newTrackService = mockk<TrackService>()
                every { TrackService.create(newServerConfig.asServerConfig()) } returns newTrackService
                createModel()

                serverConfigChangeNotification(newServerConfig)

                val creation = MapStateUpdaterCreation.fetch()
                creation.loader.trackService shouldBe newTrackService
            }
        }

        "the camera position state" should {
            "be obtained from the receiver camera state" {
                val positionState = mockk<CameraPositionState>()
                every { cameraState.cameraPositionState } returns positionState

                val model = createModel()

                model.cameraPositionState shouldBe positionState
            }
        }

        "the location file state" should {
            "initially be empty" {
                val model = createModel()

                model.locationFileState shouldBe LocationFileState.EMPTY
            }

            "be updated from the updater" {
                val newLocations = LocationTestHelper.createState(1..4)
                every { cameraState.zoomToAllMarkers(newLocations) } just runs
                every { cameraState.centerRecentMarker(newLocations) } just runs
                val model = createModel()

                val creation = MapStateUpdaterCreation.fetch()
                creation.sendStateUpdate(newLocations)

                model.locationFileState shouldBe newLocations
            }
        }

        "the seconds to the next update" should {
            "initially be 0" {
                val model = createModel()

                model.secondsToNextUpdate shouldBe 0
            }

            "be updated from the updater" {
                val timeToUpdate = 42
                val model = createModel()

                val creation = MapStateUpdaterCreation.fetch()
                creation.sendCountDown(timeToUpdate)

                model.secondsToNextUpdate shouldBe timeToUpdate
            }
        }

        "the formatted seconds to the next update" should {
            "initially be empty" {
                val model = createModel()

                model.secondsToNextUpdateString shouldBe ""
            }

            "be updated from the updater" {
                val model = createModel()

                val creation = MapStateUpdaterCreation.fetch()
                creation.sendCountDown(42)

                model.secondsToNextUpdateString shouldBe "42 $UNIT_SECOND"
            }
        }

        "the camera position" should {
            "be initialized when the first state arrives" {
                val newLocations = LocationTestHelper.createState(1..2)
                every { cameraState.zoomToAllMarkers(newLocations) } just runs
                every { cameraState.centerRecentMarker(newLocations) } just runs
                createModel()

                val creation = MapStateUpdaterCreation.fetch()
                creation.sendStateUpdate(newLocations)

                verify {
                    cameraState.zoomToAllMarkers(newLocations)
                    cameraState.centerRecentMarker(newLocations)
                }
            }

            "be centered to the latest marker if configured" {
                val newLocations1 = LocationTestHelper.createState(1..2)
                val newLocations2 = LocationTestHelper.createState(2..3)
                every { cameraState.zoomToAllMarkers(newLocations1) } just runs
                every { cameraState.centerRecentMarker(any()) } just runs
                createModel()

                val creation = MapStateUpdaterCreation.fetch()
                creation.sendStateUpdate(newLocations1)
                creation.sendStateUpdate(newLocations2)

                verify {
                    cameraState.centerRecentMarker(newLocations1)
                    cameraState.centerRecentMarker(newLocations2)
                }
            }

            "not be centered again if there is no change in the recent marker" {
                val newLocations1 = LocationTestHelper.createState(1..3)
                val newLocations2 = LocationTestHelper.createState(2..3)
                every { cameraState.zoomToAllMarkers(newLocations1) } just runs
                every { cameraState.centerRecentMarker(newLocations1) } just runs
                createModel()

                val creation = MapStateUpdaterCreation.fetch()
                creation.sendStateUpdate(newLocations1)
                creation.sendStateUpdate(newLocations2)
            }

            "not be centered if this configuration setting is disabled" {
                val newConfig = RECEIVER_CONFIG.copy(centerNewPosition = false)
                val newLocations = LocationTestHelper.createState(1..2)
                every { cameraState.zoomToAllMarkers(newLocations) } just runs
                createModel()

                receiverConfigChangeNotification(newConfig)
                val creation = MapStateUpdaterCreation.fetch()
                creation.sendStateUpdate(newLocations)
            }
        }

        "the updater" should {
            "be recreated on a change of the update interval" {
                val newConfig = RECEIVER_CONFIG.copy(updateInterval = RECEIVER_CONFIG.updateInterval - 1)
                val newUpdater = mockk<MapStateUpdater>(relaxed = true)
                createModel()
                every { MapStateUpdater.create(any(), any(), any(), any(), any()) } returns newUpdater

                receiverConfigChangeNotification(newConfig)

                MapStateUpdaterCreation.fetch(numberOfCalls = 2, expectedInterval = newConfig.updateInterval)
                verify {
                    updater.close()
                }
            }

            "not be recreated if the update interval stays the same" {
                val newConfig = RECEIVER_CONFIG.copy(fadeOutEnabled = true)
                createModel()

                receiverConfigChangeNotification(newConfig)

                MapStateUpdaterCreation.fetch()
            }
        }

        "isUpdating" should {
            "return true if an update is in progress" {
                val model = createModel()

                model.isUpdating() shouldBe true
            }

            "return false if no update is in progress" {
                val model = createModel()
                val creation = MapStateUpdaterCreation.fetch()

                creation.sendCountDown(1)

                model.isUpdating() shouldBe false
            }
        }

        "recentLocationTime" should {
            "return null if no locations are available" {
                val model = createModel()

                model.recentLocationTime() should beNull()
            }

            "return the age of the recent location" {
                val markerData = LocationTestHelper.createMarkerData(1)
                val markerTime = System.currentTimeMillis() - 10 * 60 * 1000
                val timedMarkerData =
                    markerData.copy(locationData = markerData.locationData.copy(time = TimeData(markerTime)))
                val locationFile = LocationTestHelper.createFile(1)
                val locationFileState =
                    LocationFileState(files = listOf(locationFile), mapOf(locationFile to timedMarkerData))
                every { cameraState.zoomToAllMarkers(any()) } just runs
                every { cameraState.centerRecentMarker(any()) } just runs
                val model = createModel()

                val creation = MapStateUpdaterCreation.fetch()
                creation.sendStateUpdate(locationFileState)

                model.recentLocationTime() shouldBe "10 $UNIT_MINUTE"
            }
        }

        "onAction" should {
            "handle an UPDATE action" {
                every { updater.update() } just runs
                val model = createModel()

                model.onAction(ReceiverAction.UPDATE)

                verify {
                    updater.update()
                }
            }

            "handle a CENTER_RECENT_POSITION action" {
                val locationFileState = LocationTestHelper.createState(4..8)
                every { cameraState.centerRecentMarker(any()) } just runs
                every { cameraState.zoomToAllMarkers(any()) } just runs
                val model = createModel()
                val creation = MapStateUpdaterCreation.fetch()
                creation.sendStateUpdate(locationFileState)

                model.onAction(ReceiverAction.CENTER_RECENT_POSITION)

                verify {
                    cameraState.centerRecentMarker(locationFileState)
                }
            }

            "handle a ZOOM_TRACKED_AREA action" {
                val locationFileState = LocationTestHelper.createState(2..8)
                every { cameraState.centerRecentMarker(any()) } just runs
                every { cameraState.zoomToAllMarkers(any()) } just runs
                val model = createModel()
                val creation = MapStateUpdaterCreation.fetch()
                creation.sendStateUpdate(locationFileState)

                model.onAction(ReceiverAction.ZOOM_TRACKED_AREA)

                verify(exactly = 2) {
                    cameraState.zoomToAllMarkers(locationFileState)
                }
            }

            "handle an UPDATE_OWN_POSITION action" {
                every { updater.queryLocation(any()) } just runs
                val model = createModel()

                model.onAction(ReceiverAction.UPDATE_OWN_POSITION)

                verify {
                    updater.queryLocation(application)
                }
            }
        }

        "markers" should {
            "be created correctly for the data in a LocationFileState" {
                val locationFileState = LocationTestHelper.createState(1..3)
                val marker1 = mockk<MarkerOptions>()
                val marker2 = mockk<MarkerOptions>()
                val marker3 = mockk<MarkerOptions>()
                val markerFactory = mockk<MarkerFactory>()
                val model = spyk(createModel())
                every { model.markerFactory } returns markerFactory

                every {
                    markerFactory.createMarker(
                        LocationTestHelper.createMarkerData(1),
                        recentMarker = false,
                        zIndex = 0f
                    )
                } returns marker1
                every {
                    markerFactory.createMarker(
                        LocationTestHelper.createMarkerData(2),
                        recentMarker = false,
                        zIndex = 1f
                    )
                } returns marker2
                every {
                    markerFactory.createMarker(
                        LocationTestHelper.createMarkerData(3),
                        recentMarker = true,
                        zIndex = 2f
                    )
                } returns marker3

                val markers = with(model) {
                    locationFileState.createMarkers()
                }

                markers should containExactly(marker1, marker2, marker3)
            }

            "be empty initially" {
                val model = createModel()

                model.markers should beEmpty()
            }

            "contain the expected data from the LocationFileState" {
                val locationFileState = LocationTestHelper.createState(1..4)
                every { cameraState.centerRecentMarker(any()) } just runs
                every { cameraState.zoomToAllMarkers(any()) } just runs
                val model = createModel()
                val creation = MapStateUpdaterCreation.fetch()
                creation.sendStateUpdate(locationFileState)

                val markers = model.markers

                locationFileState.markerData.values.forAll { markerData ->
                    markers.find { it.position == markerData.position }.shouldNotBeNull()
                }
            }

            "be replaced when the marker factory is changed" {
                val locationFileState = LocationTestHelper.createState(1..32)
                every { cameraState.centerRecentMarker(any()) } just runs
                every { cameraState.zoomToAllMarkers(any()) } just runs
                val model = createModel()
                val creation = MapStateUpdaterCreation.fetch()
                creation.sendStateUpdate(locationFileState)
                val markers = model.markers

                receiverConfigChangeNotification(RECEIVER_CONFIG.copy(fastFadeOut = true))

                model.markers shouldNotBe markers
            }
        }
    }

    /**
     * Obtain the listener for changes on the [ReceiverConfig] that was registered at the [ConfigManager].
     */
    private fun receiverConfigChangeListener(): (ReceiverConfig) -> Unit {
        val slotListener = slot<(ReceiverConfig) -> Unit>()
        verify { configManager.addReceiverConfigChangeListener(capture(slotListener)) }
        return slotListener.captured
    }

    /**
     * Trigger a notification that the [ReceiverConfig] was changed to [newConfig].
     */
    private fun receiverConfigChangeNotification(newConfig: ReceiverConfig) {
        val listener = receiverConfigChangeListener()
        listener(newConfig)
    }

    /**
     * Obtain the listener for changes on the [TrackServerConfig] that was registered at the [ConfigManager].
     */
    private fun serverConfigChangeListener(): (TrackServerConfig) -> Unit {
        val slotListener = slot<(TrackServerConfig) -> Unit>()
        verify { configManager.addServerConfigChangeListener(capture(slotListener)) }
        return slotListener.captured
    }

    /**
     * Trigger a notification that the [TrackServerConfig] was changed to [newConfig].
     */
    private fun serverConfigChangeNotification(newConfig: TrackServerConfig) {
        val listener = serverConfigChangeListener()
        listener(newConfig)
    }

    /**
     * Create a mock for the [ConfigManager] that is already prepared for some expected interactions.
     */
    private fun createConfigManager(): ConfigManager {
        val configManagerMock = TrackTestHelper.prepareConfigManager(application, receiverConfig = RECEIVER_CONFIG)
        every { configManagerMock.addReceiverConfigChangeListener(any()) } just runs
        every { configManagerMock.addServerConfigChangeListener(any()) } just runs
        every { configManagerMock.removeReceiverConfigChangeListener(any()) } just runs
        every { configManagerMock.removeServerConfigChangeListener(any()) } just runs

        return configManagerMock
    }

    /**
     * Create a new test instance of [ReceiverViewModelImpl].
     */
    private fun createModel(): ReceiverViewModelImpl = ReceiverViewModelImpl(application)
}

/** A test configuration. */
private val RECEIVER_CONFIG = ReceiverConfig(
    updateInterval = 500,
    fadeOutEnabled = true,
    fastFadeOut = false,
    strongFadeOut = true,
    centerNewPosition = true
)

/** The unit to display for days. */
private const val UNIT_DAY = "dayStr"

/** The unit to display for hours. */
private const val UNIT_HOUR = "hourStr"

/** The unit to display for minutes. */
private const val UNIT_MINUTE = "minuteStr"

/** The unit to display for seconds. */
private const val UNIT_SECOND = "secondStr"

/**
 * Create a mock for the [PreferencesHandler] that is prepared to expect updates.
 */
private fun createPreferencesHandlerMock(): PreferencesHandler =
    mockk<PreferencesHandler>().apply {
        every { update(any()) } just runs
    }

/**
 * Create a mock for the [Application] that is prepared to be queried for the required string resources.
 */
private fun createApplicationMock(): Application =
    mockk<Application>().apply {
        every { getString(R.string.time_secs) } returns UNIT_SECOND
        every { getString(R.string.time_minutes) } returns UNIT_MINUTE
        every { getString(R.string.time_hours) } returns UNIT_HOUR
        every { getString(R.string.time_days) } returns UNIT_DAY
    }

/**
 * A class that stores the complex parameters of a creation of a [MapStateUpdater] object.
 */
private class MapStateUpdaterCreation(
    /** The function to return the [MapStateLoader]. */
    private val loaderProvider: () -> MapStateLoader,

    /** The function to update the state. */
    private val updateState: (LocationFileState) -> Unit,

    /** The function to update the count-down value. */
    private val countDown: (Int) -> Unit
) {
    companion object {
        /**
         * Return an instance with data obtained from the latest creation of a [MapStateUpdater], expecting that
         * [expectedInterval] is used as update interval and that the given [numberOfCalls] have been recorded.
         */
        fun fetch(
            expectedInterval: Int = RECEIVER_CONFIG.updateInterval,
            numberOfCalls: Int = 1
        ): MapStateUpdaterCreation {
            val intervals = mutableListOf<Int>()
            val providers = mutableListOf<() -> MapStateLoader>()
            val updates = mutableListOf<(LocationFileState) -> Unit>()
            val countDowns = mutableListOf<(Int) -> Unit>()
            verify {
                MapStateUpdater.create(
                    capture(intervals),
                    capture(providers),
                    capture(updates),
                    capture(countDowns),
                    any()
                )
            }

            providers shouldHaveSize numberOfCalls
            intervals.last() shouldBe expectedInterval

            return MapStateUpdaterCreation(providers.last(), updates.last(), countDowns.last())
        }
    }

    /** The current [MapStateLoader] obtained from the provider. */
    val loader: MapStateLoader
        get() = loaderProvider()

    /**
     * Pass the given [state] to the update state function.
     */
    fun sendStateUpdate(state: LocationFileState) {
        updateState(state)
    }

    /**
     * Pass the given [value] to the count-down function.
     */
    fun sendCountDown(value: Int) {
        countDown(value)
    }
}
