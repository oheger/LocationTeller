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
import android.content.SharedPreferences

import com.github.oheger.locationteller.config.PreferencesHandler
import com.github.oheger.locationteller.track.TrackStorage

import io.kotest.core.spec.style.WordSpec
import io.kotest.core.test.TestCase
import io.kotest.matchers.shouldBe

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify

import java.util.Date

/**
 * Test class for [TrackViewModel].
 */
class TrackViewModelSpec : WordSpec() {
    /** A mock for the preferences handler that can be used by test instances. */
    private lateinit var preferencesHandler: PreferencesHandler

    private lateinit var preferences: SharedPreferences

    /** A mock formatter that is used by test instances. */
    private lateinit var formatter: TrackStatsFormatter

    override suspend fun beforeAny(testCase: TestCase) {
        preferencesHandler = mockPreferencesHandler()
        preferences = preferencesHandler.preferences
        formatter = mockFormatter()
    }

    init {
        "The property change listener" should {
            "watch the tracking start property" {
                val startTime = Date(20220628215901L)
                val formattedTime = "2022-06-28 21:59:01"
                every { preferencesHandler.getDate(TrackStorage.PROP_TRACKING_START) } returns startTime
                every { formatter.formatDate(startTime) } returns formattedTime

                val model = createModel()
                fetchAndTriggerPreferencesListener(TrackStorage.PROP_TRACKING_START)

                model.trackStatistics.startTime shouldBe formattedTime
            }

            "watch the tracking end property" {
                val endTime = Date(20220628221642L)
                val formattedTime = "2022-06-28 22:16:42"
                every { preferencesHandler.getDate(TrackStorage.PROP_TRACKING_END) } returns endTime
                every { formatter.formatDate(endTime) } returns formattedTime

                val model = createModel()
                fetchAndTriggerPreferencesListener(TrackStorage.PROP_TRACKING_END)

                model.trackStatistics.endTime shouldBe formattedTime
            }

            "watch the last check time property" {
                val lastCheckTime = Date(20220629213616L)
                val formattedTime = "2022-06-29 21:36:16"
                every { preferencesHandler.getDate(TrackStorage.PROP_LAST_CHECK) } returns lastCheckTime
                every { formatter.formatDate(lastCheckTime) } returns formattedTime

                val model = createModel()
                fetchAndTriggerPreferencesListener(TrackStorage.PROP_LAST_CHECK)

                model.trackStatistics.lastCheckTime shouldBe formattedTime
            }

            "watch the last update time property" {
                val lastUpdateTime = Date(20220629214143L)
                val formattedTime = "2022-06-29 21:41:43"
                every { preferencesHandler.getDate(TrackStorage.PROP_LAST_UPDATE) } returns lastUpdateTime
                every { formatter.formatDate(lastUpdateTime) } returns formattedTime

                val model = createModel()
                fetchAndTriggerPreferencesListener(TrackStorage.PROP_LAST_UPDATE)

                model.trackStatistics.lastUpdateTime shouldBe formattedTime
            }

            "watch the last error time property" {
                val lastErrorTime = Date(20220629214550L)
                val formattedTime = "2022-06-29 21:42:50"
                every { preferencesHandler.getDate(TrackStorage.PROP_LAST_ERROR) } returns lastErrorTime
                every { formatter.formatDate(lastErrorTime) } returns formattedTime

                val model = createModel()
                fetchAndTriggerPreferencesListener(TrackStorage.PROP_LAST_ERROR)

                model.trackStatistics.lastErrorTime shouldBe formattedTime
            }

            "watch the check count property" {
                val checkCount = 128
                every { preferences.getInt(TrackStorage.PROP_CHECK_COUNT, 0) } returns checkCount

                val model = createModel()
                fetchAndTriggerPreferencesListener(TrackStorage.PROP_CHECK_COUNT)

                model.trackStatistics.numberOfChecks shouldBe checkCount.toString()
            }

            "watch the update count property" {
                val updateCount = 77
                every { preferences.getInt(TrackStorage.PROP_UPDATE_COUNT, 0) } returns updateCount

                val model = createModel()
                fetchAndTriggerPreferencesListener(TrackStorage.PROP_UPDATE_COUNT)

                model.trackStatistics.numberOfUpdates shouldBe updateCount.toString()
            }

            "watch the error count property" {
                val errorCount = 42
                every { preferences.getInt(TrackStorage.PROP_ERROR_COUNT, 0) } returns errorCount

                val model = createModel()
                fetchAndTriggerPreferencesListener(TrackStorage.PROP_ERROR_COUNT)

                model.trackStatistics.numberOfErrors shouldBe errorCount.toString()
            }

            "watch the last distance property" {
                val lastDistance = 333
                every {
                    preferences.getInt(TrackStorage.PROP_LAST_DISTANCE, 0)
                } returns lastDistance

                val model = createModel()
                fetchAndTriggerPreferencesListener(TrackStorage.PROP_LAST_DISTANCE)

                model.trackStatistics.lastDistance shouldBe lastDistance.toString()
            }

            "watch the total distance property" {
                val totalDistance = 10256L
                every {
                    preferences.getLong(TrackStorage.PROP_TOTAL_DISTANCE, 0)
                } returns totalDistance

                val model = createModel()
                fetchAndTriggerPreferencesListener(TrackStorage.PROP_TOTAL_DISTANCE)

                model.trackStatistics.totalDistance shouldBe totalDistance.toString()
            }

            "ignore unrelated properties" {
                createModel()

                fetchAndTriggerPreferencesListener("someOtherProperty")

                verify(exactly = 0) {
                    preferences.getInt(any(), any())
                    preferencesHandler.getDate(any())
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

                val prefs = preferencesHandler.preferences
                properties.forEach {
                    every { prefs.contains(it) } returns true
                }
                every { prefs.getInt(TrackStorage.PROP_CHECK_COUNT, 0) } returns 28
                every { prefs.getInt(TrackStorage.PROP_ERROR_COUNT, 0) } returns 4
                every { prefs.getInt(TrackStorage.PROP_LAST_DISTANCE, 0) } returns 111
                every { prefs.getLong(TrackStorage.PROP_TOTAL_DISTANCE, 0) } returns 10789L
                every { prefs.getInt(TrackStorage.PROP_UPDATE_COUNT, 0) } returns 20
                every { preferencesHandler.getDate(TrackStorage.PROP_LAST_CHECK) } returns lastCheck
                every { preferencesHandler.getDate(TrackStorage.PROP_LAST_ERROR) } returns lastError
                every { preferencesHandler.getDate(TrackStorage.PROP_LAST_UPDATE) } returns lastUpdate
                every { preferencesHandler.getDate(TrackStorage.PROP_TRACKING_START) } returns startTime
                every { preferencesHandler.getDate(TrackStorage.PROP_TRACKING_END) } returns endTime
                every { formatter.formatDate(startTime) } returns "startTime"
                every { formatter.formatDate(endTime) } returns "endTime"
                every { formatter.formatDate(lastCheck) } returns "lastCheck"
                every { formatter.formatDate(lastError) } returns "lastError"
                every { formatter.formatDate(lastUpdate) } returns "lastUpdate"

                val model = createModel()

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
            }
        }
    }

    /**
     * Create a test [TrackViewModel] instance that is associated with the managed mock [PreferencesHandler].
     */
    private fun createModel(): TrackViewModel {
        val application = mockk<Application>()
        mockkObject(PreferencesHandler)
        every { PreferencesHandler.getInstance(application) } returns preferencesHandler

        return TrackViewModel(application)
    }

    /**
     * Verify that a preferences listener has been registered at the managed [PreferencesHandler] and return it.
     */
    private fun fetchPreferencesListener(): SharedPreferences.OnSharedPreferenceChangeListener {
        val slotListener = slot<SharedPreferences.OnSharedPreferenceChangeListener>()
        verify {
            preferencesHandler.registerListener(capture(slotListener))
        }

        return slotListener.captured
    }

    /**
     * Invoke [listener] to simulate a preferences change event for the given [property].
     */
    private fun triggerPreferenceChangedEvent(listener: SharedPreferences.OnSharedPreferenceChangeListener, property: String) {
        listener.onSharedPreferenceChanged(preferencesHandler.preferences, property)
    }

    /**
     * Fetch the listener that has been registered on the managed [PreferencesHandler] and invoke it to simulate a
     * change event for the given [property].
     */
    private fun fetchAndTriggerPreferencesListener(property: String) {
        triggerPreferenceChangedEvent(fetchPreferencesListener(), property)
    }
}

/**
 * Create a mock [PreferencesHandler] object that is equipped with mock [SharedPreferences] and set some default
 * expectations.
 */
private fun mockPreferencesHandler(): PreferencesHandler =
    mockk<PreferencesHandler>().apply {
        val sp = mockk<SharedPreferences>()
        every { preferences } returns sp
        every { sp.contains(any()) } returns false
        every { registerListener(any()) } just runs
    }

/**
 * Create a mock [TrackStatsFormatter] that is going to be used by a test instance.
 */
private fun mockFormatter(): TrackStatsFormatter =
    mockk<TrackStatsFormatter>().apply {
        mockkObject(TrackStatsFormatter)
        every { TrackStatsFormatter.create() } returns this
    }
