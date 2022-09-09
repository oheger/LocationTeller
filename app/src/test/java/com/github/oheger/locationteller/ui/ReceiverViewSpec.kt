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

import android.app.Application

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4

import com.github.oheger.locationteller.R

import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

import org.robolectric.annotation.Config

/**
 * Test class for Composable functions related to the receiver UI.
 */
@RunWith(AndroidJUnit4::class)
@Config(
    instrumentedPackages = ["androidx.loader.content"]
)
class ReceiverViewSpec {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `UpdateStatus displays an ongoing update`() {
        composeTestRule.setContent {
            UpdateStatus(updateInProgress = true, countDown = "ignore")
        }

        composeTestRule.onNodeWithTag(TAG_REC_UPDATE_INDICATOR).assertIsDisplayed()
        composeTestRule.onNodeWithTag(TAG_REC_UPDATE_STATUS_TEXT)
            .assertTextEquals(stringResource(R.string.map_status_updating))
    }

    @Test
    fun `UpdateStatus displays the time to the next update`() {
        val countDown = "42 sec"
        composeTestRule.setContent {
            UpdateStatus(updateInProgress = false, countDown = countDown)
        }

        composeTestRule.onNodeWithTag(TAG_REC_UPDATE_INDICATOR).assertDoesNotExist()
        composeTestRule.onNodeWithTag(TAG_REC_UPDATE_STATUS_TEXT)
            .assertTextEquals(stringResource(R.string.map_status_update_scheduled, countDown))
    }

    @Test
    fun `LocationStatus displays a message if no locations are available`() {
        composeTestRule.setContent {
            LocationStatus(numberOfLocations = 42, recentLocationTime = null)
        }

        composeTestRule.onNodeWithTag(TAG_REC_LOCATION_STATUS_TEXT)
            .assertTextEquals(stringResource(R.string.map_status_empty))
    }

    @Test
    fun `LocationStatus displays the number and age of locations`() {
        val numberOfLocations = 16
        val recentLocationTime = "20 m"
        composeTestRule.setContent {
            LocationStatus(numberOfLocations = numberOfLocations, recentLocationTime = recentLocationTime)
        }

        composeTestRule.onNodeWithTag(TAG_REC_LOCATION_STATUS_TEXT)
            .assertTextEquals(stringResource(R.string.map_status, numberOfLocations, recentLocationTime))
    }

    @Test
    fun `ReceiverView displays the status line`() {
        val model = PreviewReceiverViewModel()
        composeTestRule.setContent {
            ReceiverView(model = model)
        }

        composeTestRule.onNodeWithTag(TAG_REC_UPDATE_STATUS_TEXT)
            .assertTextContains(model.secondsToNextUpdateString, substring = true)
        composeTestRule.onNodeWithTag(TAG_REC_LOCATION_STATUS_TEXT)
            .assertTextContains(model.recentLocationTime(), substring = true)
    }
}

/**
 * Convenience function to obtain the string resource with the given [id] and additional [args].
 */
private fun stringResource(id: Int, vararg args: Any): String =
    ApplicationProvider.getApplicationContext<Application>().getString(id, *args)
