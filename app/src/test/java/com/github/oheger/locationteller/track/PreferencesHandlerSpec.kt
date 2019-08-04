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
package com.github.oheger.locationteller.track

import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import io.mockk.*

/**
 * Test class for [PreferencesHandler].
 */
class PreferencesHandlerSpec : StringSpec() {
    init {
        "PreferencesHandler should support updates on preferences" {
            val pref = mockk<SharedPreferences>()
            val editor = mockk<SharedPreferences.Editor>()
            every { pref.edit() } returns editor
            every { editor.putString("foo", "bar") } returns editor
            every { editor.apply() } just runs
            val handler = PreferencesHandler(pref)

            handler.update { it.putString("foo", "bar") }
            verifyOrder {
                pref.edit()
                editor.putString("foo", "bar")
                editor.apply()
            }
        }

        "PreferencesHandler should return the current tracking state" {
            val pref = mockk<SharedPreferences>()
            every {
                pref.getBoolean(
                    PreferencesHandler.propTrackState,
                    false
                )
            } returnsMany listOf(true, false)
            val handler = PreferencesHandler(pref)

            handler.isTrackingEnabled() shouldBe true
            handler.isTrackingEnabled() shouldBe false
        }

        "PreferencesHandler should allow updating the tracking state" {
            val pref = mockk<SharedPreferences>()
            val editor = mockk<SharedPreferences.Editor>()
            every { pref.edit() } returns editor
            every { editor.putBoolean(PreferencesHandler.propTrackState, true) } returns editor
            every { editor.apply() } just runs
            val handler = PreferencesHandler(pref)

            handler.setTrackingEnabled(true)
            verify {
                editor.putBoolean(PreferencesHandler.propTrackState, true)
                editor.apply()
            }
        }

        "PreferencesHandler should identify configuration properties" {
            val configProps = listOf(
                PreferencesHandler.propBasePath, PreferencesHandler.propIdleIncrement,
                PreferencesHandler.propLocationValidity, PreferencesHandler.propMaxTrackInterval,
                PreferencesHandler.propMinTrackInterval, PreferencesHandler.propPassword,
                PreferencesHandler.propUser, PreferencesHandler.propServerUri,
                PreferencesHandler.propLocationUpdateThreshold
            )

            configProps.forEach { prop -> PreferencesHandler.isConfigProperty(prop) shouldBe true }
        }

        "PreferencesHandler should identify non-configuration properties" {
            val nonConfigProps = listOf(PreferencesHandler.propTrackState, "foo", "bar")

            nonConfigProps.forEach { prop -> PreferencesHandler.isConfigProperty(prop) shouldBe false }
        }

        "PreferencesHandler should record an error" {
            val errorTime = 20190704213348L
            val pref = mockk<SharedPreferences>()
            val editor = mockk<SharedPreferences.Editor>()
            every { pref.edit() } returns editor
            every { editor.putLong(PreferencesHandler.propLastError, errorTime) } returns editor
            every { editor.apply() } just runs
            val handler = PreferencesHandler(pref)

            handler.recordError(errorTime)
            verify {
                editor.putLong(PreferencesHandler.propLastError, errorTime)
                editor.apply()
            }
        }

        "PreferencesHandler should record an update" {
            val updateTime = 20190704213752L
            val distance = 1111
            val pref = mockk<SharedPreferences>()
            val editor = mockk<SharedPreferences.Editor>()
            every { pref.edit() } returns editor
            every { editor.putLong(PreferencesHandler.propLastUpdate, updateTime) } returns editor
            every { editor.putInt(PreferencesHandler.propLastDistance, distance) } returns editor
            every { editor.remove(PreferencesHandler.propLastError) } returns editor
            every { editor.apply() } just runs
            val handler = PreferencesHandler(pref)

            handler.recordUpdate(updateTime, distance)
            verify {
                editor.putLong(PreferencesHandler.propLastUpdate, updateTime)
                editor.putInt(PreferencesHandler.propLastDistance, distance)
                editor.remove(PreferencesHandler.propLastError)
                editor.apply()
            }
        }

        "PreferencesHandler should record a check" {
            val checkTime = 20190711222122L
            val pref = mockk<SharedPreferences>()
            val editor = mockk<SharedPreferences.Editor>()
            every { pref.edit() } returns editor
            every { editor.putLong(PreferencesHandler.propLastCheck, checkTime) } returns editor
            every { editor.apply() } just runs
            val handler = PreferencesHandler(pref)

            handler.recordCheck(checkTime)
            verify {
                editor.putLong(PreferencesHandler.propLastCheck, checkTime)
                editor.apply()
            }
        }

        "PreferencesHandler should return the last error time" {
            val errorTime = 20190705180422L
            val pref = mockk<SharedPreferences>()
            every { pref.contains(PreferencesHandler.propLastError) } returns true
            every { pref.getLong(PreferencesHandler.propLastError, 0) } returns errorTime
            val handler = PreferencesHandler(pref)

            val errorDate = handler.lastError()
            errorDate!!.time shouldBe errorTime
        }

        "PreferencesHandler should return null for the last error if undefined" {
            val pref = mockk<SharedPreferences>()
            every { pref.contains(PreferencesHandler.propLastError) } returns false
            val handler = PreferencesHandler(pref)

            handler.lastError() shouldBe null
        }

        "PreferencesHandler should return the last update time" {
            val updateTime = 20190705181104L
            val pref = mockk<SharedPreferences>()
            every { pref.contains(PreferencesHandler.propLastUpdate) } returns true
            every { pref.getLong(PreferencesHandler.propLastUpdate, 0) } returns updateTime
            val handler = PreferencesHandler(pref)

            val updateDate = handler.lastUpdate()
            updateDate!!.time shouldBe updateTime
        }

        "PreferencesHandler should return null for the last update if undefined" {
            val pref = mockk<SharedPreferences>()
            every { pref.contains(PreferencesHandler.propLastUpdate) } returns false
            val handler = PreferencesHandler(pref)

            handler.lastUpdate() shouldBe null
        }

        "PreferencesHandler should return the last check time" {
            val checkTime = 20190711222611L
            val pref = mockk<SharedPreferences>()
            every { pref.contains(PreferencesHandler.propLastCheck) } returns true
            every { pref.getLong(PreferencesHandler.propLastCheck, 0) } returns checkTime
            val handler = PreferencesHandler(pref)

            val checkDate = handler.lastCheck()
            checkDate!!.time shouldBe checkTime
        }

        "PreferencesHandler should return null for the last check if undefined" {
            val pref = mockk<SharedPreferences>()
            every { pref.contains(PreferencesHandler.propLastCheck) } returns false
            val handler = PreferencesHandler(pref)

            handler.lastCheck() shouldBe null
        }

        "PreferencesHandler should return the distance of the last location update" {
            val distance = 157
            val pref = mockk<SharedPreferences>()
            every { pref.getInt(PreferencesHandler.propLastDistance, 0) } returns distance
            val handler = PreferencesHandler(pref)

            handler.lastDistance() shouldBe distance
        }

        "PreferencesHandler should support the registration of change listeners" {
            mockkStatic(PreferenceManager::class)
            val pref = mockk<SharedPreferences>()
            val context = mockk<Context>()
            val listener = mockk<SharedPreferences.OnSharedPreferenceChangeListener>()
            every { PreferenceManager.getDefaultSharedPreferences(context) } returns pref
            every { pref.registerOnSharedPreferenceChangeListener(listener) } just runs

            PreferencesHandler.registerListener(context, listener)
            verify { pref.registerOnSharedPreferenceChangeListener(listener) }
        }

        "PreferencesHandler should support removing of change listeners" {
            mockkStatic(PreferenceManager::class)
            val pref = mockk<SharedPreferences>()
            val context = mockk<Context>()
            val listener = mockk<SharedPreferences.OnSharedPreferenceChangeListener>()
            every { PreferenceManager.getDefaultSharedPreferences(context) } returns pref
            every { pref.unregisterOnSharedPreferenceChangeListener(listener) } just runs

            PreferencesHandler.unregisterListener(context, listener)
            verify { pref.unregisterOnSharedPreferenceChangeListener(listener) }
        }

        "PreferencesHandler should create a track configuration" {
            val trackConfig = TrackConfig(60, 600, 120, 3600, 15, 0, 0)
            val pref = preferencesFromConfig(trackConfig)
            val handler = PreferencesHandler(pref)

            handler.createTrackConfig() shouldBe trackConfig
        }

        "PreferencesHandler should return null for a TrackConfig if properties are undefined" {
            val pref = mockk<SharedPreferences>()
            listOf(
                PreferencesHandler.propMaxTrackInterval, PreferencesHandler.propMinTrackInterval,
                PreferencesHandler.propIdleIncrement, PreferencesHandler.propLocationValidity,
                PreferencesHandler.propLocationUpdateThreshold
            ).forEach {
                initProperty(pref, it, -1)
            }
            val handler = PreferencesHandler(pref)

            handler.createTrackConfig() shouldBe null
        }

        "PreferencesHandler should set a default value for the updateLocationThreshold property" {
            val trackConfig = TrackConfig(
                3, 10, 2, 20, -1, 0, 0
            )
            val pref = preferencesFromConfig(trackConfig)
            val handler = PreferencesHandler(pref)

            val config = handler.createTrackConfig()
            config?.locationUpdateThreshold shouldBe PreferencesHandler.defaultLocationUpdateThreshold
        }
    }

    companion object {
        private fun initProperty(pref: SharedPreferences, property: String, value: Int) {
            every {
                pref.getString(property, "-1")
            } returns value.toString()
        }

        private fun preferencesFromConfig(trackConfig: TrackConfig): SharedPreferences {
            val pref = mockk<SharedPreferences>()
            initProperty(pref, PreferencesHandler.propMinTrackInterval, trackConfig.minTrackInterval / 60)
            initProperty(pref, PreferencesHandler.propMaxTrackInterval, trackConfig.maxTrackInterval / 60)
            initProperty(pref, PreferencesHandler.propIdleIncrement, trackConfig.intervalIncrementOnIdle / 60)
            initProperty(pref, PreferencesHandler.propLocationValidity, trackConfig.locationValidity / 60)
            initProperty(pref, PreferencesHandler.propLocationUpdateThreshold, trackConfig.locationUpdateThreshold)
            return pref
        }
    }
}
