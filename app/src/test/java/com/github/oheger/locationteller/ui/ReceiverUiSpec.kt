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
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.navigation.Navigation
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4

import com.github.oheger.locationteller.R
import com.github.oheger.locationteller.config.ConfigManager
import com.github.oheger.locationteller.config.PreferencesHandler
import com.github.oheger.locationteller.config.ReceiverConfig
import com.github.oheger.locationteller.ui.state.ReceiverAction

import io.kotest.inspectors.forAll
import io.kotest.matchers.shouldBe

import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

import org.robolectric.annotation.Config

/**
 * A test class that tests the receiver UI as a whole.
 */
@RunWith(AndroidJUnit4::class)
@Config(
    instrumentedPackages = ["androidx.loader.content"]
)
class ReceiverUiSpec {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun initAndNavigateToReceiverView() {
        composeTestRule.activityRule.scenario.onActivity {
            val configManager = ConfigManager.getInstance()
            configManager.updateReceiverConfig(it.application, ReceiverConfig.DEFAULT)

            Navigation.findNavController(it, R.id.nav_host_fragment).navigate(R.id.receiverView)
        }
    }

    @Test
    fun `The receiver config is hidden by default`() {
        listOf(
            TAG_REC_CONF_CENTER_NEW,
            TAG_REC_CONF_FADE,
            TAG_REC_CONF_FADE_FAST,
            TAG_REC_CONF_FADE_STRONG,
            TAG_REC_CONF_UPDATE_INTERVAL
        ).forAll {
            composeTestRule.onNodeWithTag(ConfigItemElement.LABEL.tagForItem(it)).assertDoesNotExist()
        }
    }

    @Test
    fun `The fade mode config settings are disabled if fading is disabled`() {
        toggleConfigView()

        composeTestRule.onNodeWithTag(ConfigItemElement.EDITOR.tagForItem(TAG_REC_CONF_FADE)).assertIsOff()
        composeTestRule.onNodeWithTag(ConfigItemElement.EDITOR.tagForItem(TAG_REC_CONF_FADE_FAST)).assertIsNotEnabled()
        composeTestRule.onNodeWithTag(ConfigItemElement.EDITOR.tagForItem(TAG_REC_CONF_FADE_STRONG))
            .assertIsNotEnabled()
    }

    @Test
    fun `The receiver configuration can be edited`() {
        fun clickSwitch(tag: String) {
            with(composeTestRule.onNodeWithTag(ConfigItemElement.EDITOR.tagForItem(tag))) {
                performScrollTo()
                performClick()
            }
        }

        fun enterText(item: String, index: Int, newText: String) {
            with(composeTestRule.onNodeWithTag(ConfigItemElement.EDITOR.tagForIndexedItem(item, index))) {
                performScrollTo()
                performTextClearance()
                performTextInput(newText)
            }
        }

        toggleConfigView()
        clickSwitch(TAG_REC_CONF_FADE)
        clickSwitch(TAG_REC_CONF_FADE_STRONG)
        clickSwitch(TAG_REC_CONF_FADE_FAST)
        clickSwitch(TAG_REC_CONF_CENTER_NEW)

        with(composeTestRule.onNodeWithTag(ConfigItemElement.LABEL.tagForItem(TAG_REC_CONF_UPDATE_INTERVAL))) {
            performScrollTo()
            performClick()
        }
        enterText(TAG_REC_CONF_UPDATE_INTERVAL, 0, "30")
        enterText(TAG_REC_CONF_UPDATE_INTERVAL, 1, "2")
        with(composeTestRule.onNodeWithTag(ConfigItemElement.COMMIT_BUTTON.tagForItem(TAG_REC_CONF_UPDATE_INTERVAL))) {
            performScrollTo()
            performClick()
        }

        val expectedConfig = ReceiverConfig(
            updateInterval = 150,
            fadeOutEnabled = true,
            fastFadeOut = true,
            strongFadeOut = true,
            centerNewPosition = true
        )
        val currentConfig =
            ReceiverConfig.fromPreferences(PreferencesHandler.getInstance(ApplicationProvider.getApplicationContext()))
        currentConfig shouldBe expectedConfig
    }

    @Test
    fun `The actions view is hidden by default`() {
        ReceiverAction.values().forAll { action ->
            composeTestRule.onNodeWithTag(actionTag(action)).assertDoesNotExist()
        }
    }

    @Test
    fun `The actions view can be displayed`() {
        toggleActionView()

        ReceiverAction.values().forAll { action ->
            composeTestRule.onNodeWithTag(actionTag(action)).assertIsDisplayed()
        }
    }

    /**
     * Toggle the visibility of the receiver configuration view by clicking on the expandable header.
     */
    private fun toggleConfigView() {
        toggleExpandableHeader(TAG_REC_HEADER_SETTINGS)
    }

    /**
     * Toggle the visibility of the receiver actions view by clicking on the expandable header.
     */
    private fun toggleActionView() {
        toggleExpandableHeader(TAG_REC_HEADER_ACTIONS)
    }

    /**
     * Toggle the visibility of the view controlled by the expandable header with the given [tag] by clicking on it.
     */
    private fun toggleExpandableHeader(tag: String) {
        composeTestRule.onNodeWithTag(expandableHeaderTextTag(tag)).performClick()
    }
}
