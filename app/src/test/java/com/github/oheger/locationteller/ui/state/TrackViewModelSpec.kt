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
import android.content.Intent
import android.content.SharedPreferences

import com.github.oheger.locationteller.config.PreferencesHandler
import com.github.oheger.locationteller.config.TrackConfig
import com.github.oheger.locationteller.server.TimeData
import com.github.oheger.locationteller.server.TimeService
import com.github.oheger.locationteller.track.TrackStorage

import io.kotest.core.spec.style.WordSpec
import io.kotest.core.test.TestCase
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.slot
import io.mockk.spyk
import io.mockk.verify

import java.util.Date

/**
 * Test class for [TrackViewModelImpl].
 */
class TrackViewModelSpec : WordSpec() {
    /** A mock for the storage of tracking-related properties. */
    private lateinit var storage: TrackStorage

    /** A mock formatter that is used by test instances. */
    private lateinit var formatter: TrackStatsFormatter

    override suspend fun beforeAny(testCase: TestCase) {
        val preferencesHandler = mockk<PreferencesHandler>(relaxed = true)
        val prefs = mockk<SharedPreferences>()
        storage = mockk(relaxed = true)

        every { storage.preferencesHandler } returns preferencesHandler
        every { preferencesHandler.preferences } returns prefs
        every { preferencesHandler.registerListener(any()) } just runs
        every { prefs.contains(any()) } returns false

        formatter = mockFormatter()
    }

    init {
        "The secondary constructor" should {
            "create a correct TrackStorage instance" {
                val application = mockk<Application>()
                val preferencesHandler = storage.preferencesHandler
                mockkObject(PreferencesHandler, TrackConfig)
                every { PreferencesHandler.getInstance(application) } returns preferencesHandler
                every { TrackConfig.fromPreferences(preferencesHandler) } returns TrackConfig.DEFAULT

                val model = TrackViewModelImpl(application)

                model.trackStorage.preferencesHandler shouldBe preferencesHandler
            }
        }

        "The property change listener" should {
            "watch the tracking start property" {
                val startTime = Date(20220628215901L)
                val formattedTime = "2022-06-28 21:59:01"
                every { storage.trackingStartDate() } returns startTime
                every { storage.trackingEndDate() } returns null
                every { formatter.formatDate(startTime) } returns formattedTime

                val model = createModel()
                fetchAndTriggerPreferencesListener(TrackStorage.PROP_TRACKING_START)

                model.trackStatistics.startTime shouldBe formattedTime
            }

            "watch the tracking end property" {
                val endTime = Date(20220628221642L)
                val formattedTime = "2022-06-28 22:16:42"
                every { storage.trackingEndDate() } returns endTime
                every { storage.trackingStartDate() } returns null
                every { formatter.formatDate(endTime) } returns formattedTime

                val model = createModel()
                fetchAndTriggerPreferencesListener(TrackStorage.PROP_TRACKING_END)

                model.trackStatistics.endTime shouldBe formattedTime
            }

            "watch the last check time property" {
                val lastCheckTime = Date(20220629213616L)
                val formattedTime = "2022-06-29 21:36:16"
                every { storage.lastCheck() } returns lastCheckTime
                every { formatter.formatDate(lastCheckTime) } returns formattedTime

                val model = createModel()
                fetchAndTriggerPreferencesListener(TrackStorage.PROP_LAST_CHECK)

                model.trackStatistics.lastCheckTime shouldBe formattedTime
            }

            "watch the last update time property" {
                val lastUpdateTime = Date(20220629214143L)
                val formattedTime = "2022-06-29 21:41:43"
                every { storage.lastUpdate() } returns lastUpdateTime
                every { formatter.formatDate(lastUpdateTime) } returns formattedTime

                val model = createModel()
                fetchAndTriggerPreferencesListener(TrackStorage.PROP_LAST_UPDATE)

                model.trackStatistics.lastUpdateTime shouldBe formattedTime
            }

            "watch the last error time property" {
                val lastErrorTime = Date(20220629214550L)
                val formattedTime = "2022-06-29 21:42:50"
                every { storage.lastError() } returns lastErrorTime
                every { formatter.formatDate(lastErrorTime) } returns formattedTime

                val model = createModel()
                fetchAndTriggerPreferencesListener(TrackStorage.PROP_LAST_ERROR)

                model.trackStatistics.lastErrorTime shouldBe formattedTime
            }

            "watch the check count property" {
                val checkCount = 128
                every { storage.checkCount() } returns checkCount

                val model = createModel()
                fetchAndTriggerPreferencesListener(TrackStorage.PROP_CHECK_COUNT)

                model.trackStatistics.numberOfChecks shouldBe checkCount.toString()
            }

            "watch the update count property" {
                val updateCount = 77
                every { storage.updateCount() } returns updateCount

                val model = createModel()
                fetchAndTriggerPreferencesListener(TrackStorage.PROP_UPDATE_COUNT)

                model.trackStatistics.numberOfUpdates shouldBe updateCount.toString()
            }

            "watch the error count property" {
                val errorCount = 42
                every { storage.errorCount() } returns errorCount

                val model = createModel()
                fetchAndTriggerPreferencesListener(TrackStorage.PROP_ERROR_COUNT)

                model.trackStatistics.numberOfErrors shouldBe errorCount.toString()
            }

            "watch the last distance property" {
                val lastDistance = 333
                every { storage.lastDistance() } returns lastDistance

                val model = createModel()
                fetchAndTriggerPreferencesListener(TrackStorage.PROP_LAST_DISTANCE)

                model.trackStatistics.lastDistance shouldBe lastDistance.toString()
            }

            "watch the total distance property" {
                val totalDistance = 10256L
                every { storage.totalDistance() } returns totalDistance
                every { storage.trackingStartDate() } returns null
                every { storage.trackingEndDate() } returns null

                val model = createModel()
                fetchAndTriggerPreferencesListener(TrackStorage.PROP_TOTAL_DISTANCE)

                model.trackStatistics.totalDistance shouldBe totalDistance.toString()
            }

            "ignore unrelated properties" {
                createModel()

                fetchAndTriggerPreferencesListener("someOtherProperty")

                verify(exactly = 0) {
                    storage.trackingStartDate()
                }
            }

            "update the tracking time" {
                val formattedDuration = "0:09"
                every { storage.trackingStartDate() } returns Date(1657225007000L)
                every { storage.trackingEndDate() } returns Date(1657225016000L)
                every { storage.totalDistance() } returns 0
                every { formatter.formatDuration(9000L) } returns formattedDuration

                val model = createModel()
                val listener = fetchPreferencesListener()
                triggerPreferenceChangedEvent(listener, TrackStorage.PROP_TRACKING_END)
                triggerPreferenceChangedEvent(listener, TrackStorage.PROP_TRACKING_START)

                model.trackStatistics.elapsedTime shouldBe formattedDuration
            }

            "update the tracking time if tracking is still in progress" {
                val startTime = CURRENT_TIME.currentTime - 60_000
                val formattedDuration = "1:00"
                every { storage.trackingStartDate() } returns Date(startTime)
                every { storage.trackingEndDate() } returns null
                every { storage.totalDistance() } returns 0
                every { formatter.formatDuration(60_000L) } returns formattedDuration

                val model = createModel()
                val listener = fetchPreferencesListener()
                triggerPreferenceChangedEvent(listener, TrackStorage.PROP_TRACKING_END)
                triggerPreferenceChangedEvent(listener, TrackStorage.PROP_TRACKING_START)

                model.trackStatistics.elapsedTime shouldBe formattedDuration
            }

            "reset the tracking time if the start date property is undefined" {
                every { storage.trackingStartDate() } returns Date(1657225007000L)
                every { storage.trackingEndDate() } returns Date(1657225016000L)
                every { storage.totalDistance() } returns 0

                val model = createModel()
                val listener = fetchPreferencesListener()
                triggerPreferenceChangedEvent(listener, TrackStorage.PROP_TRACKING_END)
                triggerPreferenceChangedEvent(listener, TrackStorage.PROP_TRACKING_START)

                every { storage.trackingStartDate() } returns null
                triggerPreferenceChangedEvent(listener, TrackStorage.PROP_TRACKING_START)

                model.trackStatistics.elapsedTime should beNull()
            }

            "update the tracking average speed if the total distance changes" {
                val distance = 1250L
                val startTime = Date(CURRENT_TIME.currentTime - 1_000_000)
                val formattedSpeed = "4.5"
                every { formatter.formatNumber(4.5) } returns formattedSpeed
                every { storage.totalDistance() } returns distance
                every { storage.trackingStartDate() } returns startTime
                every { storage.trackingEndDate() } returns null

                val model = createModel()
                fetchAndTriggerPreferencesListener(TrackStorage.PROP_TOTAL_DISTANCE)

                model.trackStatistics.averageSpeed shouldBe formattedSpeed
            }

            "update the tracking average speed if the end time changes" {
                val distance = 1250L
                val startTime = Date(1657310400000L)
                val endTime = Date(1657311400000L)
                val formattedSpeed = "4.5"
                every { formatter.formatNumber(4.5) } returns formattedSpeed
                every { storage.totalDistance() } returns distance
                every { storage.trackingStartDate() } returns startTime
                every { storage.trackingEndDate() } returns endTime

                val model = createModel()
                fetchAndTriggerPreferencesListener(TrackStorage.PROP_TRACKING_END)

                model.trackStatistics.averageSpeed shouldBe formattedSpeed
            }

            "reset the tracking average speed if the start time is cleared" {
                every { storage.totalDistance() } returns 1111
                every {
                    storage.trackingStartDate()
                } returns Date(CURRENT_TIME.currentTime - 300_000)
                every { storage.trackingEndDate() } returns null

                val model = createModel()
                val listener = fetchPreferencesListener()
                triggerPreferenceChangedEvent(listener, TrackStorage.PROP_TOTAL_DISTANCE)

                every { storage.trackingStartDate() } returns null
                triggerPreferenceChangedEvent(listener, TrackStorage.PROP_TRACKING_START)

                model.trackStatistics.averageSpeed should beNull()
            }

            "reset the tracking average speed if the tracking time is 0" {
                every { storage.trackingStartDate() } returns Date(CURRENT_TIME.currentTime)
                every { storage.trackingEndDate() } returns null
                every { storage.totalDistance() } returns 0

                val model = createModel()
                fetchAndTriggerPreferencesListener(TrackStorage.PROP_TOTAL_DISTANCE)

                model.trackStatistics.averageSpeed should beNull()
            }

            "set undefined numeric statistics to null" {
                every { storage.trackingStartDate() } returns Date(CURRENT_TIME.currentTime)
                every { storage.trackingEndDate() } returns null
                every { storage.lastDistance() } returns 0
                every { storage.totalDistance() } returns 0
                every { storage.checkCount() } returns 0
                every { storage.updateCount() } returns 0
                every { storage.errorCount() } returns 0

                val model = createModel()
                val listener = fetchPreferencesListener()
                listOf(
                    TrackStorage.PROP_CHECK_COUNT,
                    TrackStorage.PROP_ERROR_COUNT,
                    TrackStorage.PROP_UPDATE_COUNT,
                    TrackStorage.PROP_TOTAL_DISTANCE,
                    TrackStorage.PROP_LAST_DISTANCE
                ).forEach { triggerPreferenceChangedEvent(listener, it) }

                with(model.trackStatistics) {
                    lastDistance should beNull()
                    totalDistance should beNull()
                    numberOfChecks should beNull()
                    numberOfUpdates should beNull()
                    numberOfErrors should beNull()
                }
            }
        }

        "A newly created instance" should {
            "initialize itself from shared preferences" {
                val properties = listOf(
                    TrackStorage.PROP_CHECK_COUNT,
                    TrackStorage.PROP_ERROR_COUNT,
                    TrackStorage.PROP_LAST_CHECK,
                    TrackStorage.PROP_LAST_DISTANCE,
                    TrackStorage.PROP_LAST_ERROR,
                    TrackStorage.PROP_LAST_UPDATE,
                    TrackStorage.PROP_TOTAL_DISTANCE,
                    TrackStorage.PROP_TRACKING_START,
                    TrackStorage.PROP_TRACKING_END,
                    TrackStorage.PROP_UPDATE_COUNT,
                )
                val startTime = Date(20220703215011L)
                val endTime = Date(20220703215031L)
                val lastCheck = Date(20220703215048L)
                val lastUpdate = Date(20220703215101L)
                val lastError = Date(20220703215116L)

                val preferencesHandler = storage.preferencesHandler
                val prefs = preferencesHandler.preferences
                every { preferencesHandler.preferences } returns prefs
                properties.forEach {
                    every { prefs.contains(it) } returns true
                }
                every { storage.preferencesHandler } returns preferencesHandler
                every { storage.checkCount() } returns 28
                every { storage.errorCount() } returns 4
                every { storage.lastDistance() } returns 111
                every { storage.totalDistance() } returns 10789L
                every { storage.updateCount() } returns 20
                every { storage.lastCheck() } returns lastCheck
                every { storage.lastError() } returns lastError
                every { storage.lastUpdate() } returns lastUpdate
                every { storage.trackingStartDate() } returns startTime
                every { storage.trackingEndDate() } returns endTime
                every { formatter.formatDate(startTime) } returns "startTime"
                every { formatter.formatDate(endTime) } returns "endTime"
                every { formatter.formatDate(lastCheck) } returns "lastCheck"
                every { formatter.formatDate(lastError) } returns "lastError"
                every { formatter.formatDate(lastUpdate) } returns "lastUpdate"
                every { formatter.formatDuration(endTime.time - startTime.time) } returns "elapsedTime"

                val model = createModel(autoReset = true)

                model.trackStatistics.numberOfChecks shouldBe "28"
                model.trackStatistics.numberOfErrors shouldBe "4"
                model.trackStatistics.numberOfUpdates shouldBe "20"
                model.trackStatistics.lastDistance shouldBe "111"
                model.trackStatistics.totalDistance shouldBe "10789"
                model.trackStatistics.startTime shouldBe "startTime"
                model.trackStatistics.endTime shouldBe "endTime"
                model.trackStatistics.lastCheckTime shouldBe "lastCheck"
                model.trackStatistics.lastErrorTime shouldBe "lastError"
                model.trackStatistics.lastUpdateTime shouldBe "lastUpdate"
                model.trackStatistics.elapsedTime shouldBe "elapsedTime"

                val expectedConfig = TrackConfig.DEFAULT.copy(autoResetStats = true)
                model.trackConfig shouldBe expectedConfig
            }

            "return an initial tracking state of false" {
                val model = createModel()

                model.trackingEnabled shouldBe false
            }

            "reset the tracking state to false in the storage" {
                createModel()

                verify {
                    storage.setTrackingEnabled(false)
                }
            }
        }

        "updateTrackingState" should {
            "not stop tracking if it is disabled" {
                val model = createModel()

                model.updateTrackingState(false)

                verify(exactly = 0) {
                    storage.recordTrackingEnd(any())
                }
            }

            "start tracking" {
                val model = createModel()

                model.updateTrackingState(enabled = true)

                model.trackingEnabled shouldBe true

                val intent = model.serviceIntent()
                val application = model.getApplication<Application>()
                verify {
                    application.startService(intent)
                    storage.recordTrackingStart(CURRENT_TIME)
                    storage.setTrackingEnabled(true)
                }
            }

            "not start tracking again if it is enabled" {
                val model = createModel()
                model.updateTrackingState(enabled = true)

                model.updateTrackingState(enabled = true)

                val intent = model.serviceIntent()
                val application = model.getApplication<Application>()
                verify(exactly = 1) {
                    application.startService(intent)
                    storage.recordTrackingStart(CURRENT_TIME)
                    storage.setTrackingEnabled(true)
                }
            }

            "stop tracking" {
                val model = createModel()
                model.updateTrackingState(enabled = true)

                model.updateTrackingState(enabled = false)

                model.trackingEnabled shouldBe false

                val intent = model.serviceIntent()
                val application = model.getApplication<Application>()
                verify(exactly = 2) {
                    application.startService(intent)
                    storage.setTrackingEnabled(false)
                }
                verify(exactly = 1) {
                    storage.recordTrackingEnd(CURRENT_TIME)
                }
                verify(exactly = 0) {
                    storage.resetStatistics()
                }
            }

            "reset statistics on start if this option is enabled" {
                val model = createModel(autoReset = true)

                model.updateTrackingState(enabled = true)

                verify {
                    storage.resetStatistics()
                }
            }

            "only reset statistics on start if this option is enabled" {
                val model = createModel()

                model.updateTrackingState(enabled = true)

                verify(exactly = 0) {
                    storage.resetStatistics()
                }
            }
        }

        "updateTrackingConfig" should {
            "replace and persist the tracking configuration" {
                val newConfig = mockk<TrackConfig>()
                every { newConfig.save(storage.preferencesHandler) } just runs
                val model = createModel()

                model.updateTrackConfig(newConfig)

                model.trackConfig shouldBe newConfig
                verify {
                    newConfig.save(storage.preferencesHandler)
                }
            }
        }
    }

    /**
     * Create a test [TrackViewModelImpl] instance that is associated with the managed mock [PreferencesHandler].
     * Prepare the creation of a [TrackConfig] with the given value of [autoReset].
     */
    private fun createModel(autoReset: Boolean = false): TrackViewModelImpl {
        mockkObject(TrackConfig)
        val config = TrackConfig.DEFAULT.copy(autoResetStats = autoReset)
        val prefHandler = storage.preferencesHandler
        every { TrackConfig.fromPreferences(prefHandler) } returns config

        val intent = mockk<Intent>()
        val model = TrackViewModelImpl(storage, mockk(relaxed = true))

        val modelSpy = spyk(model)
        every { modelSpy.serviceIntent() } returns intent

        return modelSpy
    }

    /**
     * Verify that a preferences listener has been registered at the managed [PreferencesHandler] and return it.
     */
    private fun fetchPreferencesListener(): SharedPreferences.OnSharedPreferenceChangeListener {
        val handler = storage.preferencesHandler
        val slotListener = slot<SharedPreferences.OnSharedPreferenceChangeListener>()
        verify {
            handler.registerListener(capture(slotListener))
        }

        return slotListener.captured
    }

    /**
     * Invoke [listener] to simulate a preferences change event for the given [property].
     */
    private fun triggerPreferenceChangedEvent(
        listener: SharedPreferences.OnSharedPreferenceChangeListener,
        property: String
    ) {
        listener.onSharedPreferenceChanged(storage.preferencesHandler.preferences, property)
    }

    /**
     * Fetch the listener that has been registered on the managed [PreferencesHandler] and invoke it to simulate a
     * change event for the given [property].
     */
    private fun fetchAndTriggerPreferencesListener(property: String) {
        triggerPreferenceChangedEvent(fetchPreferencesListener(), property)
    }
}

/** The current time returned by the time service used by mock formatter. */
private val CURRENT_TIME = TimeData(1657306256000L)

/**
 * Create a mock [TrackStatsFormatter] that is going to be used by a test instance.
 */
private fun mockFormatter(): TrackStatsFormatter {
    val formatter = mockk<TrackStatsFormatter>(relaxed = true)
    val timeService = mockk<TimeService>()
    every { timeService.currentTime() } returns CURRENT_TIME
    every { formatter.timeService } returns timeService

    mockkObject(TrackStatsFormatter)
    every { TrackStatsFormatter.create() } returns formatter

    return formatter
}
