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

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import androidx.test.ext.junit.runners.AndroidJUnit4

import com.github.oheger.locationteller.track.TrackStorage

import io.kotest.inspectors.forAll

import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

import org.robolectric.annotation.Config

import java.util.Calendar
import java.util.Date

/**
 * Test class for the Tracking UI.
 */
@RunWith(AndroidJUnit4::class)
@Config(
    instrumentedPackages = ["androidx.loader.content"]
)
class TrackUiSpec {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `Tracking statistics is displayed`() {
        val statisticTags = listOf(
            TAG_TRACK_START,
            TAG_TRACK_END,
            TAG_TRACK_SPEED,
            TAG_TRACK_DIST,
            TAG_TRACK_CHECKS,
            TAG_TRACK_LAST_CHECK
        )

        statisticTags.forAll {
            composeTestRule.onNodeWithTag(it).assertIsDisplayed()
        }
    }

    @Test
    fun `Changes on preferences update values in the statistics`() {
        composeTestRule.activityRule.scenario.onActivity {
            val preferences = PreferenceManager.getDefaultSharedPreferences(it.applicationContext)
            preferences.edit(commit = true) {
                putLong(TrackStorage.PROP_TRACKING_START, createDate(2022, Calendar.JULY, 1).time)
                putInt(TrackStorage.PROP_CHECK_COUNT, 42)
                putInt(TrackStorage.PROP_LAST_DISTANCE, 77)
            }
        }

        composeTestRule.onNodeWithTag(TAG_TRACK_CHECKS).assertTextEquals("42")
        composeTestRule.onNodeWithTag(TAG_TRACK_LAST_DIST).assertTextEquals("77")
        composeTestRule.onNodeWithTag(TAG_TRACK_START).assertTextContains("2022", substring = true)
    }
}

/**
 * Generate a [Date] object from the given parameters.
 */
private fun createDate(year: Int, month: Int, day: Int, hour: Int = 0, minute: Int = 0, second: Int = 0): Date {
    val cal = Calendar.getInstance()
    cal.set(year, month, day, hour, minute, second)
    return cal.time
}
