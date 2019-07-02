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
package com.github.oheger.locationteller.ui

import android.content.SharedPreferences
import io.kotlintest.specs.StringSpec
import io.mockk.*

/**
 * Test class for [MainActivity].
 */
class MainActivitySpec : StringSpec() {
    init {
        "MainActivity should disable tracking when there is a configuration change and tracking is active" {
            val pref = mockk<SharedPreferences>()
            val editor = mockk<SharedPreferences.Editor>()
            every { pref.getBoolean("trackEnabled", false) } returns true
            every { pref.edit() } returns editor
            every { editor.putBoolean("trackEnabled", false) } returns editor
            every { editor.apply() } just runs
            val activity = MainActivity()

            activity.onSharedPreferenceChanged(pref, "someKey")
            verifyOrder {
                editor.putBoolean("trackEnabled", false)
                editor.apply()
            }
        }

        "MainActivity should disable tracking only if it is currently active" {
            val pref = mockk<SharedPreferences>()
            every { pref.getBoolean("trackEnabled", false) } returns false
            val activity = MainActivity()

            activity.onSharedPreferenceChanged(pref, "someKey")
        }

        "MainActivity should trigger the track service if the track state changes" {
            val pref = mockk<SharedPreferences>()
            val activity = spyk(MainActivity())
            every { activity.startService(any()) } returns null

            activity.onSharedPreferenceChanged(pref, "trackEnabled")
            verify {
                activity.startService(any())
            }
        }
    }
}