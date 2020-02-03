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

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.github.oheger.locationteller.R
import com.github.oheger.locationteller.server.TimeData
import com.github.oheger.locationteller.server.TimeService
import com.github.oheger.locationteller.track.PreferencesHandler
import io.kotlintest.matchers.numerics.shouldBeLessThanOrEqual
import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import io.mockk.*
import java.text.DateFormat
import java.text.NumberFormat
import java.util.*
import kotlin.math.abs

/**
 * Test class for [TrackingStatsListAdapter].
 */
class TrackingStatsListAdapterSpec : StringSpec() {
    /**
     * Helper function for testing whether the statistics for the tracking time
     * is correctly calculated.
     * @param startTime the tracking start time
     * @param endTime the tracking end time
     * @param expected the expected duration string
     */
    private fun checkElapsedTrackingTime(startTime: Date, endTime: Date, expected: String) {
        val helper = AdapterTestHelper()

        helper.initPreferences { handler ->
            every { handler.trackingStartDate() } returns startTime
            every { handler.trackingEndDate() } returns null
        }.initCurrentTime(endTime.time)
            .checkView(2, R.string.stats_tracking_time, expected)
    }

    init {
        "TrackingStatsListAdapter should create a default time service" {
            val context = mockk<Context>()
            every { context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) } returns mockk<LayoutInflater>()
            val adapter = TrackingStatsListAdapter.create(context, mockk())

            val currentTime = adapter.timeService.currentTime().currentTime
            val deltaT = abs(System.currentTimeMillis() - currentTime)
            deltaT shouldBeLessThanOrEqual 3000
        }

        "TrackingStatsListAdapter should return the correct count" {
            val helper = AdapterTestHelper()

            helper.adapter.count shouldBe 12
        }

        "TrackingStatsListAdapter should return undefined item IDs" {
            val helper = AdapterTestHelper()

            (0 until helper.adapter.count).forEach {
                helper.adapter.getItemId(it) shouldBe 0
            }
        }

        "TrackingStatsListAdapter should display the tracking start time" {
            val startTime = toDate(22, 19, 8)
            val helper = AdapterTestHelper()

            helper.initPreferences { handler ->
                every { handler.trackingStartDate() } returns startTime
            }.checkView(0, R.string.stats_tracking_started, dateString(startTime))
        }

        "TrackingStatsListAdapter should display the tracking end time" {
            val endTime = toDate(22, 27, 15)
            val helper = AdapterTestHelper()

            helper.initPreferences { handler ->
                every { handler.trackingEndDate() } returns endTime
            }.checkViewWithHolder(1, R.string.stats_tracking_stopped, dateString(endTime))
        }

        "TrackingStatsListAdapter should handle null date objects" {
            val helper = AdapterTestHelper()

            helper.initPreferences { handler ->
                every { handler.trackingStartDate() } returns null
            }.checkViewWithHolder(0, R.string.stats_tracking_started, "")
        }

        "TrackingStatsListAdapter should display the elapsed tracking time in seconds" {
            val startTime = toDate(22, 5, 59)
            val currentTime = toDate(22, 6, 8)
            checkElapsedTrackingTime(startTime, currentTime, "0:09")
        }

        "TrackingStatsListAdapter should display the elapsed tracking time in minutes" {
            val startTime = toDate(21, 22, 56)
            val currentTime = toDate(21, 33, 8)
            checkElapsedTrackingTime(startTime, currentTime, "10:12")
        }

        "TrackingStatsListAdapter should display the elapsed tracking time in hours" {
            val startTime = toDate(21, 24, 10)
            val currentTime = toDate(23, 34, 10)
            checkElapsedTrackingTime(startTime, currentTime, "2:10:00")
        }

        "TrackingStatsListAdapter should display the elapsed tracking time in days" {
            val startTime = toDate(21, 25, 40)
            val currentTime = toDate(21, 25, 42, days = 12)
            checkElapsedTrackingTime(startTime, currentTime, "12:00:00:02")
        }

        "TrackingStatsListAdapter should display a 0 elapsed tracking time" {
            val startTime = toDate(21, 25, 40)
            val currentTime = toDate(21, 25, 40)
            checkElapsedTrackingTime(startTime, currentTime, "0:00")
        }

        "TrackingStatsListAdapter should display the elapsed time if there is no start time" {
            val helper = AdapterTestHelper()

            helper.initPreferences { handler ->
                every { handler.trackingStartDate() } returns null
            }.checkView(2, R.string.stats_tracking_time, "")
        }

        "TrackingStatsListAdapter should calculate the tracking time if tracking is stopped" {
            val startTime = toDate(10, 30, 0)
            val endTime = toDate(22, 0, 59)
            val helper = AdapterTestHelper()

            helper.initPreferences { handler ->
                every { handler.trackingStartDate() } returns startTime
                every { handler.trackingEndDate() } returns endTime
            }.checkViewWithHolder(2, R.string.stats_tracking_time, "11:30:59")
        }

        "TrackingStatsListAdapter should display the total tracked distance" {
            val distance = 42000L
            val helper = AdapterTestHelper()

            helper.initPreferences { handler ->
                every { handler.totalDistance() } returns distance
            }.checkView(3, R.string.stats_tracking_total_distance, distance.toString())
        }

        "TrackingStatsListAdapter should display the last tracked distance" {
            val distance = 512
            val helper = AdapterTestHelper()

            helper.initPreferences { handler ->
                every { handler.lastDistance() } returns distance
            }.checkViewWithHolder(5, R.string.stats_tracking_last_distance, distance.toString())
        }

        "TrackingStatsListAdapter should display the last check time" {
            val time = toDate(22, 22, 40)
            val helper = AdapterTestHelper()

            helper.initPreferences { handler ->
                every { handler.lastCheck() } returns time
            }.checkView(7, R.string.stats_tracking_last_check, dateString(time))
        }

        "TrackingStatsListAdapter should display the last update time" {
            val time = toDate(21, 23, 18)
            val helper = AdapterTestHelper()

            helper.initPreferences { handler ->
                every { handler.lastUpdate() } returns time
            }.checkViewWithHolder(9, R.string.stats_tracking_last_update, dateString(time))
        }

        "TrackingStatsListAdapter should display the number of errors" {
            val errorCount = 42
            val helper = AdapterTestHelper()

            helper.initPreferences { handler ->
                every { handler.errorCount() } returns errorCount
            }.checkView(10, R.string.stats_tracking_error_count, errorCount.toString())
        }

        "TrackingStatsListAdapter should display the last error time" {
            val time = toDate(21, 33, 29)
            val helper = AdapterTestHelper()

            helper.initPreferences { handler ->
                every { handler.lastError() } returns time
            }.checkViewWithHolder(11, R.string.stats_tracking_last_error, dateString(time))
        }

        "TrackingStatsListAdapter should display the number of checks" {
            val checkCount = 128
            val helper = AdapterTestHelper()

            helper.initPreferences { handler ->
                every { handler.checkCount() } returns checkCount
            }.checkView(6, R.string.stats_tracking_check_count, checkCount.toString())
        }

        "TrackingStatsListAdapter should display the number of updates" {
            val updateCount = 64
            val helper = AdapterTestHelper()

            helper.initPreferences { handler ->
                every { handler.updateCount() } returns updateCount
            }.checkViewWithHolder(8, R.string.stats_tracking_update_count, updateCount.toString())
        }

        "TrackingStatsListAdapter should display the average speed" {
            val distance = 1250L
            val startTime = toDate(21, 0, 0)
            val endTime = toDate(21, 16, 40)
            val helper = AdapterTestHelper()

            helper.initPreferences { handler ->
                every { handler.totalDistance() } returns distance
                every { handler.trackingStartDate() } returns startTime
                every { handler.trackingEndDate() } returns endTime
            }.checkView(4, R.string.stats_tracking_speed, numberString(4.5))
        }

        "TrackingStatsListAdapter should display the average speed if tracking is in progress" {
            val distance = 750L
            val startTime = toDate(21, 30, 0)
            val currentTime = toDate(21, 40, 30)
            val helper = AdapterTestHelper()

            helper.initPreferences { handler ->
                every { handler.totalDistance() } returns distance
                every { handler.trackingStartDate() } returns startTime
                every { handler.trackingEndDate() } returns null
            }.initCurrentTime(currentTime.time)
                .checkViewWithHolder(4, R.string.stats_tracking_speed, numberString(4.29))
        }

        "TrackingStatsListAdapter should display the average speed if not start time is available" {
            val helper = AdapterTestHelper()

            helper.initPreferences { handler ->
                every { handler.trackingStartDate() } returns null
            }.checkView(4, R.string.stats_tracking_speed, "")
        }

        "TrackingStatsListAdapter should display the average speed if tracking time is zero" {
            val startTime = toDate(22, 22, 4)
            val helper = AdapterTestHelper()

            helper.initPreferences { handler ->
                every { handler.trackingStartDate() } returns startTime
                every { handler.trackingEndDate() } returns null
            }.initCurrentTime(startTime.time)
                .checkView(4, R.string.stats_tracking_speed, "")
        }

        "TrackingStatsListAdapter should register a preferences listener when activated" {
            val helper = AdapterTestHelper()

            helper.initPreferences { handler ->
                every { handler.registerListener(any()) } just runs
            }
            helper.adapter.activate()
            helper.verifyPreferencesListenerRegistered()
        }

        "TrackingStatsListAdapter should remove the preferences listener on deactivation" {
            val helper = AdapterTestHelper()

            helper.initPreferences { handler ->
                every { handler.unregisterListener(any()) } just runs
            }
            helper.adapter.deactivate()
            helper.verifyPreferencesListenerRemoved()
        }

        "TrackingStatsListAdapter should send change notifications when relevant properties are changed" {
            val helper = AdapterTestHelper()

            statisticsProperties.withIndex().forEach { idxVal ->
                helper.simulatePreferencesChange(idxVal.value)
                    .verifyUIUpdate(idxVal.index + 1)
            }
        }

        "TrackingStatsListAdapter should ignore change notifications of irrelevant properties" {
            val helper = AdapterTestHelper()

            helper.simulatePreferencesChange("foo")
                .simulatePreferencesChange(null)
                .verifyUIUpdate(0)
        }
    }

    companion object {
        /**
         * A list with the properties that are relevant for the statistics
         * adapter. When one of these properties is changed the UI needs to be
         * updated.
         */
        private val statisticsProperties = listOf(
            PreferencesHandler.propTrackingStart, PreferencesHandler.propTrackingEnd,
            PreferencesHandler.propLastError, PreferencesHandler.propLastUpdate,
            PreferencesHandler.propLastCheck, PreferencesHandler.propLastDistance
        )

        /**
         * Generates a date object with the given time portion that is relative
         * to a reference date.
         * @param hour the hour of the day
         * @param minute the minute
         * @param second the second
         * @param days the delta of days
         */
        private fun toDate(hour: Int, minute: Int, second: Int, days: Int = 0): Date {
            val cal = Calendar.getInstance()
            cal.set(2020, Calendar.JANUARY, 19 + days, hour, minute, second)
            return cal.time
        }

        /**
         * Generates the formatted string to be displayed for a date.
         * @param date the date
         * @return the formatted date string
         */
        private fun dateString(date: Date): String {
            val format = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM)
            return format.format(date)
        }

        /**
         * Generates the formatted string to be displayed for a number.
         * @param number the number
         * @return the formatted number string
         */
        private fun numberString(number: Number): String {
            val format = NumberFormat.getInstance()
            format.minimumFractionDigits = 2
            format.maximumFractionDigits = 2
            return format.format(number)
        }
    }

    /**
     * A test helper class managing a test instance and all its dependencies.
     */
    private class AdapterTestHelper {
        /** A mock for the layout inflater to be used by getView().*/
        private val inflater = mockk<LayoutInflater>()

        /** Mock for the time service.*/
        private val timeService = mockk<TimeService>()

        /** Mock for the Android context.*/
        private val context = createContext(inflater)

        /** Mock for the preferences handler.*/
        private val prefHandler = createPrefHandler()

        /** The adapter to be tested.*/
        val adapter = spyk(TrackingStatsListAdapter.create(context, prefHandler, timeService))

        /**
         * Allows the initialization of the mock for the preferences handler.
         * This can be used to define preferences that are expected to be read
         * when initializing the view.
         * @param block a lambda to initialize the mock preferences handler
         * @return this test helper
         */
        fun initPreferences(block: (PreferencesHandler) -> Unit): AdapterTestHelper {
            block(prefHandler)
            return this
        }

        /**
         * Prepares the mock for the time service to return the given time as
         * the current time.
         * @param time the current time to be returned
         * @return this test helper
         */
        fun initCurrentTime(time: Long): AdapterTestHelper {
            every { timeService.currentTime() } returns TimeData(time)
            return this
        }

        /**
         * Checks whether the view for a position is correctly initialized.
         * @param position the position
         * @param expLabelID the resource ID for the label
         * @param expValue the value to be displayed
         * @return this test helper
         */
        fun checkView(position: Int, expLabelID: Int, expValue: String): AdapterTestHelper {
            val parent = mockk<ViewGroup>()
            val view = mockk<View>()
            val labelView = mockk<TextView>()
            val valueView = mockk<TextView>()
            val viewHolder = TrackingFormViewHolder(labelView, valueView)
            every { inflater.inflate(R.layout.statistics_row_layout, parent, false) } returns view
            every { view.findViewById<TextView>(R.id.stats_label) } returns labelView
            every { view.findViewById<TextView>(R.id.stats_value) } returns valueView
            every { view.tag = viewHolder } just runs
            every { view.tag } returns viewHolder
            every { labelView.setText(expLabelID) } just runs
            every { valueView.text = expValue } just runs

            adapter.getView(position, null, parent) shouldBe view
            verify {
                labelView.setText(expLabelID)
                valueView.text = expValue
                view.tag = viewHolder
            }
            return this
        }

        /**
         * Checks whether the view for a position is correctly initialized if a
         * view holder is in place.
         * @param position the position
         * @param expLabelID the resource ID for the label
         * @param expValue the value to be displayed
         * @return this test helper
         */
        fun checkViewWithHolder(position: Int, expLabelID: Int, expValue: String): AdapterTestHelper {
            val view = mockk<View>()
            val labelView = mockk<TextView>()
            val valueView = mockk<TextView>()
            val viewHolder = TrackingFormViewHolder(labelView, valueView)
            every { view.tag } returns viewHolder
            every { labelView.setText(expLabelID) } just runs
            every { valueView.text = expValue } just runs

            adapter.getView(position, view, mockk()) shouldBe view
            verify {
                labelView.setText(expLabelID)
                valueView.text = expValue
            }
            return this
        }

        /**
         * Verifies that the adapter registered itself as preferences listener.
         * @return this test helper
         */
        fun verifyPreferencesListenerRegistered(): AdapterTestHelper {
            verify { prefHandler.registerListener(adapter) }
            return this
        }

        /**
         * Verifies that the adapter has removed its preferences listener
         * registration
         * @return this test helper
         */
        fun verifyPreferencesListenerRemoved(): AdapterTestHelper {
            verify { prefHandler.unregisterListener(adapter) }
            return this
        }

        /**
         * Verifies that the adapter has triggered the given number of UI
         * updates.
         * @param times the expected number of updates
         * @return this test helper
         */
        fun verifyUIUpdate(times: Int): AdapterTestHelper {
            verify(exactly = times) { adapter.notifyDataSetChanged() }
            return this
        }

        /**
         * Invokes the test adapter's preferences change listener method with
         * the given property.
         * @param property the changed property
         * @return this test helper
         */
        fun simulatePreferencesChange(property: String?): AdapterTestHelper {
            adapter.onSharedPreferenceChanged(null, property)
            return this
        }

        /**
         * Creates a mock for the context that is prepared to return a layout
         * inflater.
         * @param inflater the inflater to be returned
         * @return the mock context
         */
        private fun createContext(inflater: LayoutInflater): Context {
            val context = mockk<Context>()
            every { context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) } returns inflater
            return context
        }

        /**
         * Creates a mock for the preferences handler that is prepared to
         * expect a listener registration.
         */
        private fun createPrefHandler(): PreferencesHandler {
            val handler = mockk<PreferencesHandler>()
            every { handler.registerListener(any()) } just runs
            return handler
        }
    }
}