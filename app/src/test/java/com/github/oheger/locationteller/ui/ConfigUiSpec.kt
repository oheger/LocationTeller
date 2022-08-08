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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4

import io.kotest.inspectors.forAll
import io.kotest.matchers.shouldBe

import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

import org.robolectric.annotation.Config

import com.github.oheger.locationteller.R

/**
 * Test class for composable functions related to the config UI.
 */
@RunWith(AndroidJUnit4::class)
@Config(
    instrumentedPackages = ["androidx.loader.content"]
)
class ConfigUiSpec {
    @get:Rule
    val composableTestRule = createComposeRule()

    @Test
    fun `Only label and value are displayed if not in edit mode`() {
        val expectedElements = setOf(ConfigItemElement.LABEL, ConfigItemElement.VALUE)

        composableTestRule.setContent {
            ConfigStringItem(
                item = CONFIG_ITEM,
                editItem = "anotherItem",
                labelRes = R.string.pref_server_uri,
                value = "value",
                update = {},
                updateEdit = {})
        }

        ConfigItemElement.values().forAll { element ->
            val interaction = composableTestRule.onNodeWithTag(element.tagForItem(CONFIG_ITEM))
            if (element in expectedElements) {
                interaction.assertExists()
            } else {
                interaction.assertDoesNotExist()
            }
        }

        val expectedLabel =
            ApplicationProvider.getApplicationContext<Application>().getString(R.string.pref_server_uri)
        composableTestRule.onNodeWithTag(ConfigItemElement.LABEL.tagForItem(CONFIG_ITEM))
            .assertTextEquals(expectedLabel)
    }

    @Test
    fun `The elements for the edit mode are correctly displayed`() {
        val nonEditModeElements = setOf(ConfigItemElement.VALUE, ConfigItemElement.ERROR_MESSAGE)

        composableTestRule.setContent {
            ConfigStringItem(
                item = CONFIG_ITEM,
                editItem = CONFIG_ITEM,
                labelRes = R.string.pref_server_uri,
                value = "value",
                update = {},
                updateEdit = {})
        }

        ConfigItemElement.values().forAll { element ->
            val interaction = composableTestRule.onNodeWithTag(element.tagForItem(CONFIG_ITEM))
            if (element in nonEditModeElements) {
                interaction.assertDoesNotExist()
            } else {
                interaction.assertExists()
            }
        }
    }

    @Test
    fun `Edit mode can be started by clicking on the label`() {
        val state = mutableStateOf<String?>("someValue")

        composableTestRule.setContent {
            ConfigStringItem(
                item = CONFIG_ITEM,
                editItem = state.value,
                labelRes = R.string.pref_server_uri,
                value = "configValue",
                update = {},
                updateEdit = { value -> state.value = value }
            )
        }
        composableTestRule.onNodeWithTag(ConfigItemElement.LABEL.tagForItem(CONFIG_ITEM)).performClick()

        state.value shouldBe CONFIG_ITEM
    }

    @Test
    fun `Edit mode can be started by clicking on the value`() {
        val state = mutableStateOf<String?>("aValue")

        composableTestRule.setContent {
            ConfigStringItem(
                item = CONFIG_ITEM,
                editItem = "someItem",
                labelRes = R.string.pref_server_uri,
                value = "configValue",
                update = {},
                updateEdit = { value -> state.value = value }
            )
        }
        composableTestRule.onNodeWithTag(ConfigItemElement.VALUE.tagForItem(CONFIG_ITEM)).performClick()

        state.value shouldBe CONFIG_ITEM
    }

    @Test
    fun `A new value can be entered and saved`() {
        val oldValue = "oldConfigValue"
        val newValue = "newConfigValue"
        val valueState = mutableStateOf("")
        composableTestRule.setContent {
            ConfigUiTestWrapper(value = oldValue, update = { value -> valueState.value = value })
        }

        composableTestRule.onNodeWithTag(ConfigItemElement.LABEL.tagForItem(CONFIG_ITEM)).performClick()
        with(composableTestRule.onNodeWithTag(ConfigItemElement.EDITOR.tagForItem(CONFIG_ITEM))) {
            assertTextEquals(oldValue)
            performTextClearance()
            performTextInput(newValue)
        }
        composableTestRule.onNodeWithTag(ConfigItemElement.COMMIT_BUTTON.tagForItem(CONFIG_ITEM)).performClick()

        valueState.value shouldBe newValue
        composableTestRule.onNodeWithTag(ConfigItemElement.EDITOR.tagForItem(CONFIG_ITEM)).assertDoesNotExist()
    }

    @Test
    fun `An edit operation can be canceled`() {
        val orgValue = "original config value"
        composableTestRule.setContent {
            ConfigUiTestWrapper(value = orgValue, update = { throw AssertionError("Unexpected invocation") })
        }

        composableTestRule.onNodeWithTag(ConfigItemElement.LABEL.tagForItem(CONFIG_ITEM)).performClick()
        with(composableTestRule.onNodeWithTag(ConfigItemElement.EDITOR.tagForItem(CONFIG_ITEM))) {
            performTextClearance()
            performTextInput("some other value")
        }
        composableTestRule.onNodeWithTag(ConfigItemElement.CANCEL_BUTTON.tagForItem(CONFIG_ITEM)).performClick()
        composableTestRule.onNodeWithTag(ConfigItemElement.EDITOR.tagForItem(CONFIG_ITEM)).assertDoesNotExist()
    }

    @Test
    fun `The editor state is updated after editing is canceled`() {
        val orgValue = "original config value before edit"
        composableTestRule.setContent {
            ConfigUiTestWrapper(value = orgValue, update = { throw AssertionError("Unexpected invocation") })
        }

        composableTestRule.onNodeWithTag(ConfigItemElement.LABEL.tagForItem(CONFIG_ITEM)).performClick()
        composableTestRule.onNodeWithTag(ConfigItemElement.EDITOR.tagForItem(CONFIG_ITEM))
            .performTextInput("someChanges")
        composableTestRule.onNodeWithTag(ConfigItemElement.CANCEL_BUTTON.tagForItem(CONFIG_ITEM)).performClick()

        composableTestRule.onNodeWithTag(ConfigItemElement.VALUE.tagForItem(CONFIG_ITEM)).performClick()
        composableTestRule.onNodeWithTag(ConfigItemElement.EDITOR.tagForItem(CONFIG_ITEM)).assertTextEquals(orgValue)
    }

    fun `Clicking the commit button without changing the text works`() {
        val orgValue = "the value"
        val valueState = mutableStateOf(orgValue)
        composableTestRule.setContent {
            ConfigUiTestWrapper(value = valueState.value, update = { valueState.value = it })
        }

        composableTestRule.onNodeWithTag(ConfigItemElement.LABEL.tagForItem(CONFIG_ITEM)).performClick()
        composableTestRule.onNodeWithTag(ConfigItemElement.COMMIT_BUTTON.tagForItem(CONFIG_ITEM)).performClick()

        valueState.value shouldBe orgValue
    }

    @Test
    fun `A visual transformation can be specified`() {
        val valueState = mutableStateOf("top-secret")
        composableTestRule.setContent {
            ConfigUiTestWrapper(
                value = valueState.value,
                update = { valueState.value = it },
                visualTransformation = PasswordVisualTransformation()
            )
        }

        composableTestRule.onNodeWithTag(ConfigItemElement.VALUE.tagForItem(CONFIG_ITEM))
            .assertTextEquals("••••••••••")
        composableTestRule.onNodeWithTag(ConfigItemElement.LABEL.tagForItem(CONFIG_ITEM)).performClick()
        with(composableTestRule.onNodeWithTag(ConfigItemElement.EDITOR.tagForItem(CONFIG_ITEM))) {
            performTextClearance()
            performTextInput("more-secret")
            assertTextEquals("•••••••••••")
        }
        composableTestRule.onNodeWithTag(ConfigItemElement.COMMIT_BUTTON.tagForItem(CONFIG_ITEM)).performClick()

        valueState.value shouldBe "more-secret"
        composableTestRule.onNodeWithTag(ConfigItemElement.VALUE.tagForItem(CONFIG_ITEM))
            .assertTextEquals("•••••••••••")
    }

    @Test
    fun `An Int configuration item is correctly displayed`() {
        composableTestRule.setContent {
            ConfigIntItem(
                item = CONFIG_ITEM,
                editItem = null,
                labelRes = R.string.pref_multi_upload_chunk_size,
                value = 42,
                update = {},
                updateEdit = {}
            )
        }

        composableTestRule.onNodeWithTag(ConfigItemElement.VALUE.tagForItem(CONFIG_ITEM)).assertTextEquals("42")
    }

    @Test
    fun `An Int configuration item can be edited`() {
        val valueState = mutableStateOf(42)
        composableTestRule.setContent {
            ConfigIntItem(
                item = CONFIG_ITEM,
                editItem = CONFIG_ITEM,
                labelRes = R.string.pref_multi_upload_chunk_size,
                value = valueState.value,
                update = { valueState.value = it },
                updateEdit = {}
            )
        }

        with(composableTestRule.onNodeWithTag(ConfigItemElement.EDITOR.tagForItem(CONFIG_ITEM))) {
            assertTextEquals("42")
            performTextClearance()
            performTextInput("21")
        }
        composableTestRule.onNodeWithTag(ConfigItemElement.COMMIT_BUTTON.tagForItem(CONFIG_ITEM)).performClick()

        valueState.value shouldBe 21
    }

    @Test
    fun `An invalid Int configuration value is handled`() {
        composableTestRule.setContent {
            ConfigIntItem(
                item = CONFIG_ITEM,
                editItem = CONFIG_ITEM,
                labelRes = R.string.pref_multi_upload_chunk_size,
                value = 42,
                update = {},
                updateEdit = {}
            )
        }

        composableTestRule.onNodeWithTag(ConfigItemElement.EDITOR.tagForItem(CONFIG_ITEM)).performTextInput("abc")

        composableTestRule.onNodeWithTag(ConfigItemElement.COMMIT_BUTTON.tagForItem(CONFIG_ITEM))
            .assertIsNotEnabled()

        val expectedErrorMessage =
            ApplicationProvider.getApplicationContext<Application>().getString(R.string.pref_err_no_number)
        composableTestRule.onNodeWithTag(ConfigItemElement.ERROR_MESSAGE.tagForItem(CONFIG_ITEM)).assertExists()
        composableTestRule.onNodeWithTag(ConfigItemElement.ERROR_MESSAGE.tagForItem(CONFIG_ITEM))
            .assertTextEquals(expectedErrorMessage)
    }
}

/** The name of the config item used by tests. */
private const val CONFIG_ITEM = "test"

/**
 * A test composable function that wraps a [ConfigStringItem] and manages the selected state. Set the current value to
 * [value] and propagate changes to [update]. Optionally apply a [visualTransformation].
 */
@Composable
fun ConfigUiTestWrapper(
    value: String,
    update: (String) -> Unit,
    visualTransformation: VisualTransformation = VisualTransformation.None
) {
    val selectedState = remember { mutableStateOf<String?>(null) }

    ConfigStringItem(
        item = CONFIG_ITEM,
        editItem = selectedState.value,
        labelRes = R.string.pref_server_uri,
        value = value,
        update = update,
        updateEdit = { selectedState.value = it },
        visualTransformation = visualTransformation
    )
}
