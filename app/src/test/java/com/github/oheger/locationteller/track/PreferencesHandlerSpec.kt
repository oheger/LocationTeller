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
package com.github.oheger.locationteller.track

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.github.oheger.locationteller.server.ServerConfig
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.longs.shouldBeLessThanOrEqual
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.mockk.Ordering
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import io.mockk.verifyOrder
import java.time.Instant
import java.util.Date
import kotlin.math.abs

/**
 * Test class for [PreferencesHandler].
 */
class PreferencesHandlerSpec : WordSpec() {
    init {
        "getDate" should {
            "return an existing date property" {
                val dateValue = Date.from(Instant.parse("2022-06-20T20:13:42.12Z"))
                val pref = mockk<SharedPreferences>()
                every { pref.contains(PROPERTY) } returns true
                every { pref.getLong(PROPERTY, 0) } returns dateValue.time

                val handler = PreferencesHandler(pref)

                handler.getDate(PROPERTY) shouldBe dateValue
            }

            "return null for a non-existing date property" {
                val pref = mockk<SharedPreferences>()
                every { pref.contains(PROPERTY) } returns false

                val handler = PreferencesHandler(pref)

                handler.getDate(PROPERTY) should beNull()
            }

            "return null for an undefined date value" {
                val pref = mockk<SharedPreferences>()
                every { pref.contains(PROPERTY) } returns true
                every { pref.getLong(PROPERTY, 0) } returns 99_999L

                val handler = PreferencesHandler(pref)

                handler.getDate(PROPERTY) should beNull()
            }
        }

        "getNumeric" should {
            "return the scaled valued of an existing property" {
                val value = 42
                val factor = 3
                val pref = mockk<SharedPreferences>()
                every { pref.getString(PROPERTY, "-1") } returns value.toString()

                val handler = PreferencesHandler(pref)

                handler.getNumeric(PROPERTY, factor = factor) shouldBe value * factor
            }

            "return the default value for a missing property" {
                val defaultValue = 11
                val factor = 2
                val pref = mockk<SharedPreferences>()
                every { pref.getString(PROPERTY, "-1") } returns null

                val handler = PreferencesHandler(pref)

                handler.getNumeric(PROPERTY, factor = factor, defaultValue) shouldBe defaultValue
            }

            "return the default value for a property with an undefined value" {
                val defaultValue = 100
                val factor = 99
                val pref = mockk<SharedPreferences>()
                every { pref.getString(PROPERTY, "-1") } returns "-1"

                val handler = PreferencesHandler(pref)

                handler.getNumeric(PROPERTY, factor = factor, defaultValue) shouldBe defaultValue
            }
        }

        "getDouble" should {
            "return the scaled value of an existing property" {
                val value = 3.1415
                val factor = 2.5
                val pref = mockk<SharedPreferences>()
                every { pref.getString(PROPERTY, "-1") } returns value.toString()

                val handler = PreferencesHandler(pref)

                handler.getDouble(PROPERTY, factor = factor, defaultValue = 1.0) shouldBe value * factor
            }

            "return the default value for a missing property" {
                val defaultValue = 11.123
                val factor = 2.22
                val pref = mockk<SharedPreferences>()
                every { pref.getString(PROPERTY, "-1") } returns null

                val handler = PreferencesHandler(pref)

                handler.getDouble(PROPERTY, factor = factor, defaultValue = defaultValue) shouldBe defaultValue
            }

            "return the default value for a property with an undefined value" {
                val defaultValue = 100.01
                val factor = 99.99
                val pref = mockk<SharedPreferences>()
                every { pref.getString(PROPERTY, "-1") } returns "-1"

                val handler = PreferencesHandler(pref)

                handler.getDouble(PROPERTY, factor = factor, defaultValue = defaultValue) shouldBe defaultValue
            }
        }

        "PreferencesHandler" should {
            "support updates on preferences" {
                val pref = mockk<SharedPreferences>()
                val editor = mockk<SharedPreferences.Editor>()
                every { pref.edit() } returns editor
                every { editor.putString("foo", "bar") } returns editor
                every { editor.apply() } just runs
                val handler = PreferencesHandler(pref)

                handler.update { putString("foo", "bar") }
                verifyOrder {
                    pref.edit()
                    editor.putString("foo", "bar")
                    editor.apply()
                }
            }

            "return the current tracking state" {
                val pref = mockk<SharedPreferences>()
                every {
                    pref.getBoolean(
                        PreferencesHandler.PROP_TRACK_STATE,
                        false
                    )
                } returnsMany listOf(true, false)
                val handler = PreferencesHandler(pref)

                handler.isTrackingEnabled() shouldBe true
                handler.isTrackingEnabled() shouldBe false
            }

            "allow setting the tracking state to true" {
                val pref = mockk<SharedPreferences>()
                val editor = mockk<SharedPreferences.Editor>()
                val slotStartTime = slot<Long>()
                every { pref.edit() } returns editor
                every { editor.putBoolean(PreferencesHandler.PROP_TRACK_STATE, true) } returns editor
                every { editor.putLong(PreferencesHandler.PROP_TRACKING_START, any()) } returns editor
                every { editor.remove(PreferencesHandler.PROP_TRACKING_END) } returns editor
                every { editor.apply() } just runs
                val handler = PreferencesHandler(pref)

                handler.setTrackingEnabled(true)
                verify {
                    editor.putBoolean(PreferencesHandler.PROP_TRACK_STATE, true)
                    editor.putLong(PreferencesHandler.PROP_TRACKING_START, capture(slotStartTime))
                    editor.remove(PreferencesHandler.PROP_TRACKING_END)
                    editor.apply()
                }
                assertCurrentTime(slotStartTime.captured)
            }

            "allow setting the tracking state to false" {
                val pref = mockk<SharedPreferences>()
                val editor = mockk<SharedPreferences.Editor>()
                val slotEndTime = slot<Long>()
                every { pref.edit() } returns editor
                every { editor.putBoolean(PreferencesHandler.PROP_TRACK_STATE, false) } returns editor
                every { editor.putLong(PreferencesHandler.PROP_TRACKING_END, any()) } returns editor
                every { editor.apply() } just runs
                val handler = PreferencesHandler(pref)

                handler.setTrackingEnabled(false)
                verify {
                    editor.putBoolean(PreferencesHandler.PROP_TRACK_STATE, false)
                    editor.putLong(PreferencesHandler.PROP_TRACKING_END, capture(slotEndTime))
                    editor.apply()
                }
                assertCurrentTime(slotEndTime.captured)
            }

            "return the correct fading mode" {
                val pref = mockk<SharedPreferences>()
                every { pref.getInt(PreferencesHandler.PROP_FADING_MODE, 0) } returnsMany listOf(1, 2)
                val handler = PreferencesHandler(pref)

                handler.getFadingMode() shouldBe 1
                handler.getFadingMode() shouldBe 2
            }

            "allow updating the fading mode" {
                val newMode = 42
                val pref = mockk<SharedPreferences>()
                val editor = mockk<SharedPreferences.Editor>()
                every { pref.edit() } returns editor
                every { editor.putInt(PreferencesHandler.PROP_FADING_MODE, newMode) } returns editor
                every { editor.apply() } just runs
                val handler = PreferencesHandler(pref)

                handler.setFadingMode(newMode)
                verify(ordering = Ordering.ORDERED) {
                    editor.putInt(PreferencesHandler.PROP_FADING_MODE, newMode)
                    editor.apply()
                }
            }

            "identify configuration properties" {
                val configProps = listOf(
                    PreferencesHandler.PROP_BASE_PATH,
                    PreferencesHandler.PROP_PASSWORD,
                    PreferencesHandler.PROP_USER,
                    PreferencesHandler.PROP_SERVER_URI,
                )

                configProps.forEach { prop -> PreferencesHandler.isConfigProperty(prop) shouldBe true }
            }

            "identify non-configuration properties" {
                val nonConfigProps = listOf(PreferencesHandler.PROP_TRACK_STATE, "foo", "bar")

                nonConfigProps.forEach { prop -> PreferencesHandler.isConfigProperty(prop) shouldBe false }
            }

            "record an error" {
                val errorTime = 20190704213348L
                val errorCount = 12
                val pref = mockk<SharedPreferences>()
                val editor = mockk<SharedPreferences.Editor>()
                every { pref.edit() } returns editor
                every { editor.putLong(PreferencesHandler.PROP_LAST_ERROR, errorTime) } returns editor
                every { editor.putInt(PreferencesHandler.PROP_ERROR_COUNT, errorCount) } returns editor
                every { editor.apply() } just runs
                val handler = PreferencesHandler(pref)

                handler.recordError(errorTime, errorCount)
                verify {
                    editor.putLong(PreferencesHandler.PROP_LAST_ERROR, errorTime)
                    editor.putInt(PreferencesHandler.PROP_ERROR_COUNT, errorCount)
                    editor.apply()
                }
            }

            "record an update" {
                val updateTime = 20190704213752L
                val updateCount = 47
                val distance = 1111
                val totalDistance = 20200118161249L
                val pref = mockk<SharedPreferences>()
                val editor = mockk<SharedPreferences.Editor>()
                every { pref.edit() } returns editor
                every { editor.putLong(PreferencesHandler.PROP_LAST_UPDATE, updateTime) } returns editor
                every { editor.putInt(PreferencesHandler.PROP_UPDATE_COUNT, updateCount) } returns editor
                every { editor.putInt(PreferencesHandler.PROP_LAST_DISTANCE, distance) } returns editor
                every { editor.putLong(PreferencesHandler.PROP_TOTAL_DISTANCE, totalDistance) } returns editor
                every { editor.apply() } just runs
                val handler = PreferencesHandler(pref)

                handler.recordUpdate(updateTime, updateCount, distance, totalDistance)
                verify {
                    editor.putLong(PreferencesHandler.PROP_LAST_UPDATE, updateTime)
                    editor.putInt(PreferencesHandler.PROP_UPDATE_COUNT, updateCount)
                    editor.putInt(PreferencesHandler.PROP_LAST_DISTANCE, distance)
                    editor.putLong(PreferencesHandler.PROP_TOTAL_DISTANCE, totalDistance)
                    editor.apply()
                }
            }

            "record a check" {
                val checkTime = 20190711222122L
                val checkCount = 128
                val pref = mockk<SharedPreferences>()
                val editor = mockk<SharedPreferences.Editor>()
                every { pref.edit() } returns editor
                every { editor.putLong(PreferencesHandler.PROP_LAST_CHECK, checkTime) } returns editor
                every { editor.putInt(PreferencesHandler.PROP_CHECK_COUNT, checkCount) } returns editor
                every { editor.apply() } just runs
                val handler = PreferencesHandler(pref)

                handler.recordCheck(checkTime, checkCount)
                verify {
                    editor.putLong(PreferencesHandler.PROP_LAST_CHECK, checkTime)
                    editor.putInt(PreferencesHandler.PROP_CHECK_COUNT, checkCount)
                    editor.apply()
                }
            }

            "return the last error time" {
                val errorTime = 20190705180422L
                val pref = mockk<SharedPreferences>()
                expectDatePropertyAccess(pref, PreferencesHandler.PROP_LAST_ERROR, errorTime)
                val handler = PreferencesHandler(pref)

                val errorDate = handler.lastError()
                errorDate!!.time shouldBe errorTime
            }

            "return null for the last error if undefined" {
                val pref = mockk<SharedPreferences>()
                every { pref.contains(PreferencesHandler.PROP_LAST_ERROR) } returns false
                val handler = PreferencesHandler(pref)

                handler.lastError() shouldBe null
            }

            "return the last update time" {
                val updateTime = 20190705181104L
                val pref = mockk<SharedPreferences>()
                expectDatePropertyAccess(pref, PreferencesHandler.PROP_LAST_UPDATE, updateTime)
                val handler = PreferencesHandler(pref)

                val updateDate = handler.lastUpdate()
                updateDate!!.time shouldBe updateTime
            }

            "return null for the last update if undefined" {
                val pref = mockk<SharedPreferences>()
                every { pref.contains(PreferencesHandler.PROP_LAST_UPDATE) } returns false
                val handler = PreferencesHandler(pref)

                handler.lastUpdate() shouldBe null
            }

            "return the last check time" {
                val checkTime = 20190711222611L
                val pref = mockk<SharedPreferences>()
                expectDatePropertyAccess(pref, PreferencesHandler.PROP_LAST_CHECK, checkTime)
                val handler = PreferencesHandler(pref)

                val checkDate = handler.lastCheck()
                checkDate!!.time shouldBe checkTime
            }

            "handle a date in the long past" {
                val pref = mockk<SharedPreferences>()
                expectDatePropertyAccess(pref, PreferencesHandler.PROP_LAST_CHECK, 1000)
                val handler = PreferencesHandler(pref)

                val checkDate = handler.lastCheck()
                checkDate shouldBe null
            }

            "return null for the last check if undefined" {
                val pref = mockk<SharedPreferences>()
                every { pref.contains(PreferencesHandler.PROP_LAST_CHECK) } returns false
                val handler = PreferencesHandler(pref)

                handler.lastCheck() shouldBe null
            }

            "return the distance of the last location update" {
                val distance = 157
                val pref = mockk<SharedPreferences>()
                every { pref.getInt(PreferencesHandler.PROP_LAST_DISTANCE, 0) } returns distance
                val handler = PreferencesHandler(pref)

                handler.lastDistance() shouldBe distance
            }

            "return the tracking start time" {
                val startTime = 20200117215143L
                val pref = mockk<SharedPreferences>()
                expectDatePropertyAccess(pref, PreferencesHandler.PROP_TRACKING_START, startTime)
                val handler = PreferencesHandler(pref)

                val startDate = handler.trackingStartDate()
                startDate!!.time shouldBe startTime
            }

            "return the tracking stop time" {
                val endTime = 20200117220450L
                val pref = mockk<SharedPreferences>()
                expectDatePropertyAccess(pref, PreferencesHandler.PROP_TRACKING_END, endTime)
                val handler = PreferencesHandler(pref)

                val endDate = handler.trackingEndDate()
                endDate!!.time shouldBe endTime
            }

            "return the total distance" {
                val distance = 20200118160148L
                val pref = mockk<SharedPreferences>()
                every { pref.getLong(PreferencesHandler.PROP_TOTAL_DISTANCE, 0) } returns distance
                val handler = PreferencesHandler(pref)

                val totalDistance = handler.totalDistance()
                totalDistance shouldBe distance
            }

            "return the error count" {
                val count = 61
                val pref = mockk<SharedPreferences>()
                every { pref.getInt(PreferencesHandler.PROP_ERROR_COUNT, 0) } returns count
                val handler = PreferencesHandler(pref)

                val errorCount = handler.errorCount()
                errorCount shouldBe count
            }

            "return the check count" {
                val count = 77
                val pref = mockk<SharedPreferences>()
                every { pref.getInt(PreferencesHandler.PROP_CHECK_COUNT, 0) } returns count
                val handler = PreferencesHandler(pref)

                val checkCount = handler.checkCount()
                checkCount shouldBe count
            }

            "return the update count" {
                val count = 99
                val pref = mockk<SharedPreferences>()
                every { pref.getInt(PreferencesHandler.PROP_UPDATE_COUNT, 0) } returns count
                val handler = PreferencesHandler(pref)

                val updateCount = handler.updateCount()
                updateCount shouldBe count
            }

            "allow resetting statistics data" {
                val pref = mockk<SharedPreferences>()
                val editor = mockk<SharedPreferences.Editor>()
                every { pref.edit() } returns editor
                every { editor.remove(PreferencesHandler.PROP_ERROR_COUNT) } returns editor
                every { editor.remove(PreferencesHandler.PROP_TOTAL_DISTANCE) } returns editor
                every { editor.remove(PreferencesHandler.PROP_LAST_DISTANCE) } returns editor
                every { editor.remove(PreferencesHandler.PROP_LAST_CHECK) } returns editor
                every { editor.remove(PreferencesHandler.PROP_LAST_ERROR) } returns editor
                every { editor.remove(PreferencesHandler.PROP_LAST_UPDATE) } returns editor
                every { editor.remove(PreferencesHandler.PROP_CHECK_COUNT) } returns editor
                every { editor.remove(PreferencesHandler.PROP_UPDATE_COUNT) } returns editor
                every { editor.apply() } just runs
                val handler = PreferencesHandler(pref)

                handler.resetStatistics()
                verify {
                    editor.remove(PreferencesHandler.PROP_ERROR_COUNT)
                    editor.remove(PreferencesHandler.PROP_TOTAL_DISTANCE)
                    editor.remove(PreferencesHandler.PROP_LAST_DISTANCE)
                    editor.remove(PreferencesHandler.PROP_LAST_ERROR)
                    editor.remove(PreferencesHandler.PROP_LAST_CHECK)
                    editor.remove(PreferencesHandler.PROP_LAST_UPDATE)
                    editor.remove(PreferencesHandler.PROP_CHECK_COUNT)
                    editor.remove(PreferencesHandler.PROP_UPDATE_COUNT)
                }
            }

            "support the registration of change listeners" {
                mockkStatic(PreferenceManager::class)
                val pref = mockk<SharedPreferences>()
                val context = mockk<Context>()
                val listener = mockk<SharedPreferences.OnSharedPreferenceChangeListener>()
                every { PreferenceManager.getDefaultSharedPreferences(context) } returns pref
                every { pref.registerOnSharedPreferenceChangeListener(listener) } just runs

                PreferencesHandler.registerListener(context, listener)
                verify { pref.registerOnSharedPreferenceChangeListener(listener) }
            }

            "support removing of change listeners" {
                mockkStatic(PreferenceManager::class)
                val pref = mockk<SharedPreferences>()
                val context = mockk<Context>()
                val listener = mockk<SharedPreferences.OnSharedPreferenceChangeListener>()
                every { PreferenceManager.getDefaultSharedPreferences(context) } returns pref
                every { pref.unregisterOnSharedPreferenceChangeListener(listener) } just runs

                PreferencesHandler.unregisterListener(context, listener)
                verify { pref.unregisterOnSharedPreferenceChangeListener(listener) }
            }

            "create a correct server configuration" {
                val handler = PreferencesHandler(preferencesFromServerConfig(defServerConfig))

                handler.createServerConfig() shouldBe defServerConfig
            }

            "return a null server config if the URI is missing" {
                checkUndefinedServerConfig(defServerConfig.copy(serverUri = ""))
            }

            "return a null server config if the base path is missing" {
                checkUndefinedServerConfig(defServerConfig.copy(basePath = ""))
            }

            "return a null server config if the user name is missing" {
                checkUndefinedServerConfig(defServerConfig.copy(user = ""))
            }

            "return a null server config if the password is missing" {
                checkUndefinedServerConfig(defServerConfig.copy(password = ""))
            }
        }
    }

    companion object {
        /** A test property key. */
        private const val PROPERTY = "someKey"

        /** A default server configuration.*/
        private val defServerConfig = ServerConfig(
            "https://track.tst", "/myTracks", "scott",
            "tiger"
        )

        /**
         * Prepares the given mock for a _SharedPreferences_ object to return a
         * value for a specific string property. Empty strings are mapped to
         * *null* values.
         * @param pref the _SharedPreferences_
         * @param property the name of the property
         * @param value the value to be returned for this property
         */
        private fun initProperty(pref: SharedPreferences, property: String, value: String) {
            every { pref.getString(property, null) } returns value.ifEmpty { null }
        }

        /**
         * Creates a mock for a _SharedPreferences_ object that is prepared to
         * return the properties from the given server configuration. If a
         * string property of the configuration is an empty string, the
         * property is not set.
         */
        private fun preferencesFromServerConfig(serverConfig: ServerConfig): SharedPreferences {
            val pref = mockk<SharedPreferences>()
            initProperty(pref, PreferencesHandler.PROP_SERVER_URI, serverConfig.serverUri)
            initProperty(pref, PreferencesHandler.PROP_BASE_PATH, serverConfig.basePath)
            initProperty(pref, PreferencesHandler.PROP_USER, serverConfig.user)
            initProperty(pref, PreferencesHandler.PROP_PASSWORD, serverConfig.password)
            return pref
        }

        /**
         * Tests whether a *null* server config is returned in case of missing
         * properties. Shared preferences are created based on the passed in
         * configuration. Then it is tested whether based on the preferences a
         * *null* configuration is created.
         * @param config the configuration
         */
        private fun checkUndefinedServerConfig(config: ServerConfig) {
            val handler = PreferencesHandler(preferencesFromServerConfig(config))

            handler.createServerConfig() shouldBe null
        }

        /**
         * Prepares the given mock for a preferences object to return the
         * given value when asked for a data property.
         * @param prefs the preferences mock
         * @param key the key of the property
         * @param value the value to be returned
         */
        private fun expectDatePropertyAccess(prefs: SharedPreferences, key: String, value: Long) {
            every { prefs.contains(key) } returns true
            every { prefs.getLong(key, 0) } returns value
        }

        /**
         * Helper function that checks whether a time value is close to the
         * current system time.
         * @param time the time value to be checked
         */
        private fun assertCurrentTime(time: Long) {
            abs(System.currentTimeMillis() - time) shouldBeLessThanOrEqual 3000
        }
    }
}
