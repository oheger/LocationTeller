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

import android.Manifest
import android.app.Application
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule

import com.github.oheger.locationteller.R
import com.github.oheger.locationteller.config.PreferencesHandler
import com.github.oheger.locationteller.track.TrackStorage

import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

import org.robolectric.annotation.Config

import java.util.Calendar
import java.util.Date

/**
 * Test class for the fragment of the Tracking UI. In contrast to [TrackViewSpec], this class tests the fragment as it
 * is integrated in the application.
 */
@RunWith(AndroidJUnit4::class)
@Config(
    instrumentedPackages = ["androidx.loader.content"]
)
class TrackUiSpec {
    @get:Rule
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.ACCESS_FINE_LOCATION)

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun navigateToSenderScreen() {
        composeTestRule.onNodeWithTag(TAG_NAV_TOP_MENU).performClick()
        composeTestRule.onNodeWithTag(TAG_NAV_SENDER).performClick()
    }

    @Test
    fun `The app bar is correctly initialized`() {
        val expectedTitle = ApplicationProvider.getApplicationContext<Application>().getString(R.string.trackView)

        composeTestRule.onNodeWithTag(TAG_NAV_TOP_TITLE).assertTextEquals(expectedTitle)
    }

    @Test
    fun `Changes on preferences update values in the statistics`() {
        composeTestRule.activityRule.scenario.onActivity { activity ->
            val preferencesHandler = PreferencesHandler.getInstance(activity.applicationContext)
            preferencesHandler.update {
                putLong(TrackStorage.PROP_TRACKING_START, createDate(2022, Calendar.JULY, 1).time)
                putInt(TrackStorage.PROP_CHECK_COUNT, 42)
                putInt(TrackStorage.PROP_LAST_DISTANCE, 77)
            }
        }

        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            composeTestRule.onAllNodesWithTag(TAG_TRACK_START).fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithTag(TAG_TRACK_CHECKS).assertTextEquals("42")
        composeTestRule.onNodeWithTag(TAG_TRACK_LAST_DIST).assertTextEquals("77")
        composeTestRule.onNodeWithTag(TAG_TRACK_START).assertTextContains("2022", substring = true)
    }

    @Test
    fun `When tracking is active the settings screens are disabled`() {
        composeTestRule.onNodeWithTag(TAG_TRACK_ENABLED_SWITCH).performClick()
        composeTestRule.onNodeWithTag(TAG_NAV_TOP_MENU).performClick()

        composeTestRule.onNodeWithTag(TAG_NAV_TRACK_SETTINGS).performClick()
        composeTestRule.onNodeWithTag(ConfigItemElement.LABEL.tagForItem(CONFIG_ITEM_TRACK_MIN_INTERVAL))
            .assertDoesNotExist()

        composeTestRule.onNodeWithTag(TAG_NAV_SERVER_SETTINGS).performClick()
        composeTestRule.onNodeWithTag(ConfigItemElement.LABEL.tagForItem(CONFIG_ITEM_SERVER_USER))
            .assertDoesNotExist()
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
