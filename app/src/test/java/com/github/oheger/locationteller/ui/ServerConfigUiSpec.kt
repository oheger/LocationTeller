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

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.navigation.Navigation.findNavController
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4

import com.github.oheger.locationteller.R
import com.github.oheger.locationteller.config.PreferencesHandler
import com.github.oheger.locationteller.config.TrackServerConfig
import io.kotest.matchers.shouldBe

import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

import org.robolectric.annotation.Config

/**
 * Test class for the UI for the server configuration.
 */
@RunWith(AndroidJUnit4::class)
@Config(
    instrumentedPackages = ["androidx.loader.content"]
)
class ServerConfigUiSpec {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun navigateToServerConfig() {
        composeTestRule.activityRule.scenario.onActivity {
            findNavController(it, R.id.nav_host_fragment).navigate(R.id.serverSettingsFragment)
        }
    }

    @Test
    fun `Server configuration can be edited`() {
        fun editValue(item: String, value: String) {
            composeTestRule.onNodeWithTag(ConfigItemElement.LABEL.tagForItem(item)).performClick()
            with(composeTestRule.onNodeWithTag(ConfigItemElement.EDITOR.tagForItem(item))) {
                performTextClearance()
                performTextInput(value)
            }
            composeTestRule.onNodeWithTag(ConfigItemElement.COMMIT_BUTTON.tagForItem(item)).performClick()
        }

        val testConfig = TrackServerConfig("https://my-track.example.org", "/tests", "u1", "pass")
        editValue(CONFIG_ITEM_SERVER_URI, testConfig.serverUri)
        editValue(CONFIG_ITEM_SERVER_PATH, testConfig.basePath)
        editValue(CONFIG_ITEM_SERVER_USER, testConfig.user)
        editValue(CONFIG_ITEM_SERVER_PASSWORD, testConfig.password)

        val currentConfig =
            TrackServerConfig.fromPreferences(PreferencesHandler.getInstance(ApplicationProvider.getApplicationContext()))
        currentConfig shouldBe testConfig

        composeTestRule.onNodeWithTag(ConfigItemElement.VALUE.tagForItem(CONFIG_ITEM_SERVER_PASSWORD))
            .assertTextEquals("••••")
    }
}
