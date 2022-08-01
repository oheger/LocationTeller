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
package com.github.oheger.locationteller.config

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.longs.shouldBeLessThanOrEqual
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.beTheSameInstanceAs
import io.mockk.*
import java.time.Instant
import java.util.*
import kotlin.math.abs

/**
 * Test class for [PreferencesHandler].
 */
class PreferencesHandlerSpec : WordSpec() {
    init {
        "create" should {
            "obtain an instance from PreferenceManager" {
                val context = mockk<Context>()
                val sharedPreferences = mockk<SharedPreferences>()
                mockkStatic(PreferenceManager::class)
                every { PreferenceManager.getDefaultSharedPreferences(context) } returns sharedPreferences

                val handler = PreferencesHandler.getInstance(context)

                handler.preferences shouldBe sharedPreferences
            }

            "return a singleton instance of PreferencesHandler" {
                val context = mockk<Context>()
                val sharedPreferences = mockk<SharedPreferences>()
                mockkStatic(PreferenceManager::class)
                every { PreferenceManager.getDefaultSharedPreferences(context) } returns sharedPreferences

                val handler1 = PreferencesHandler.getInstance(context)
                val handler2 = PreferencesHandler.getInstance(mockk())

                handler2 should beTheSameInstanceAs(handler1)
            }
        }

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

            "support the registration of change listeners" {
                val pref = mockk<SharedPreferences>()
                val listener = mockk<SharedPreferences.OnSharedPreferenceChangeListener>()
                every { pref.registerOnSharedPreferenceChangeListener(listener) } just runs
                val handler = PreferencesHandler(pref)

                handler.registerListener(listener)
                verify { pref.registerOnSharedPreferenceChangeListener(listener) }
            }

            "support removing of change listeners" {
                val pref = mockk<SharedPreferences>()
                val listener = mockk<SharedPreferences.OnSharedPreferenceChangeListener>()
                every { pref.unregisterOnSharedPreferenceChangeListener(listener) } just runs
                val handler = PreferencesHandler(pref)

                handler.unregisterListener(listener)
                verify { pref.unregisterOnSharedPreferenceChangeListener(listener) }
            }
        }
    }

    companion object {
        /** A test property key. */
        private const val PROPERTY = "someKey"

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
