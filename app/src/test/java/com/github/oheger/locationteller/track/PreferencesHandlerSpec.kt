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

import android.content.SharedPreferences
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
            every { pref.getBoolean(PreferencesHandler.propTrackState,
                false) } returnsMany listOf(true, false)
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
            val configProps = listOf(PreferencesHandler.propBasePath, PreferencesHandler.propIdleIncrement,
                PreferencesHandler.propLocationValidity, PreferencesHandler.propMaxTrackInterval,
                PreferencesHandler.propMinTrackInterval, PreferencesHandler.propPassword,
                PreferencesHandler.propUser, PreferencesHandler.propServerUri)

            configProps.forEach{prop -> PreferencesHandler.isConfigProperty(prop) shouldBe true }
        }

        "PreferencesHandler should identify non-configuration properties" {
            val nonConfigProps = listOf(PreferencesHandler.propTrackState, "foo", "bar")

            nonConfigProps.forEach { prop -> PreferencesHandler.isConfigProperty(prop) shouldBe false }
        }
    }
}