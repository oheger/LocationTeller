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

import android.content.SharedPreferences
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.launchActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.oheger.locationteller.config.PreferencesHandler
import com.github.oheger.locationteller.config.TrackConfig
import io.mockk.*
import kotlinx.coroutines.ObsoleteCoroutinesApi
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test class for [MainActivity].
 */
@ObsoleteCoroutinesApi
@RunWith(AndroidJUnit4::class)
class MainActivitySpec {
    @Test
    fun testTrackingIsDisabledOnAConfigurationChange() {
        val pref = mockk<SharedPreferences>()
        val editor = mockk<SharedPreferences.Editor>()
        every { pref.getBoolean(PreferencesHandler.PROP_TRACK_STATE, false) } returns true
        every { pref.edit() } returns editor
        every { editor.putBoolean(PreferencesHandler.PROP_TRACK_STATE, false) } returns editor
        every { editor.putLong(any(), any()) } returns editor
        every { editor.apply() } just runs
        val activity = MainActivity()

        activity.onSharedPreferenceChanged(pref, PreferencesHandler.PROP_SERVER_URI)
        activity.onSharedPreferenceChanged(pref, TrackConfig.PROP_WALKING_SPEED)

        verify(exactly = 2) {
            editor.putBoolean(PreferencesHandler.PROP_TRACK_STATE, false)
        }
    }

    @Test
    fun testTrackingIsDisabledOnlyIfActive() {
        val pref = mockk<SharedPreferences>()
        every { pref.getBoolean(PreferencesHandler.PROP_TRACK_STATE, false) } returns false
        val activity = MainActivity()

        activity.onSharedPreferenceChanged(pref, PreferencesHandler.PROP_BASE_PATH)
    }

    @Test
    fun testTrackServiceIsTriggeredOnAStateChange() {
        val pref = mockk<SharedPreferences>()
        val scenario = launchActivity<MainActivity>()
        scenario.moveToState(Lifecycle.State.CREATED)

        scenario.onActivity { activity ->
            val activitySpy = spyk(activity)
            every { activitySpy.startService(any()) } returns null

            activitySpy.onSharedPreferenceChanged(pref, PreferencesHandler.PROP_TRACK_STATE)
            verify {
                activitySpy.startService(any())
            }
        }
    }

    @Test
    fun `default track configuration settings are initialized on startup`() {
        mockkObject(TrackConfig)
        val scenario = launchActivity<MainActivity>()
        scenario.moveToState(Lifecycle.State.CREATED)

        scenario.onActivity {
            verify {
                TrackConfig.initDefaults(any())
            }
        }
    }

    @Test
    fun testPreferencesUpdatesForOtherPropertiesAreIgnored() {
        val pref = mockk<SharedPreferences>()
        val activity = MainActivity()

        activity.onSharedPreferenceChanged(pref, "otherProperty")
    }
}