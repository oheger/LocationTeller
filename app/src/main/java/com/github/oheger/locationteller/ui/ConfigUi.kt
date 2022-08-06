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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

import com.github.oheger.locationteller.R
import com.github.oheger.locationteller.config.TrackServerConfig
import com.github.oheger.locationteller.ui.state.TrackViewModel
import com.github.oheger.locationteller.ui.state.TrackViewModelImpl

internal const val CONFIG_ITEM_SERVER_URI = "config_server_uri"
internal const val CONFIG_ITEM_SERVER_PATH = "config_server_path"
internal const val CONFIG_ITEM_SERVER_USER = "config_server_username"
internal const val CONFIG_ITEM_SERVER_PASSWORD = "config_server_password"

/**
 * An enum class with constants for the single elements of the UI of a configuration item. This is mainly used by
 * tests to access specific elements by tags.
 */
internal enum class ConfigItemElement {
    LABEL,
    VALUE,
    EDITOR,
    COMMIT_BUTTON,
    CANCEL_BUTTON;

    /**
     * Generate a tag for this input element of the given configuration [item].
     */
    fun tagForItem(item: String): String = "tag_${item}_$name"
}

/**
 * Type definition for a function that provides an editor for [ConfigItem] of a specific type. The current value of
 * this is editor is hoisted. It is passed as argument to the function as well as the update function.
 */
typealias ConfigEditor<T> = @Composable (T, (T) -> Unit, Modifier) -> Unit

/**
 * Type definition for a function that generates a string representation of a configuration item. This function is
 * used by [ConfigItem] to generate the display text based on the current value of the item
 */
typealias ConfigItemRenderer<T> = (T) -> AnnotatedString

/**
 * Generate the UI for the configuration of the track server settings. This is the entry point into this configuration
 * UI.
 */
@Composable
fun ServerConfigUi(model: TrackViewModelImpl = viewModel(), modifier: Modifier = Modifier) {
    ServerConfigView(model = model, modifier = modifier)
}

/**
 * Generate the view for displaying and changing configuration settings related to the tracking server using [model]
 * as view model.
 */
@Composable
fun ServerConfigView(model: TrackViewModel, modifier: Modifier = Modifier) {
    val editItem = rememberSaveable { mutableStateOf<String?>(null) }
    val editFunc: (String?) -> Unit = { editItem.value = it }

    fun updateConfig(updateFunc: (TrackServerConfig, String) -> TrackServerConfig): (String) -> Unit = { value ->
        val serverConfig = model.serverConfig
        val updatedConfig = updateFunc(serverConfig, value)
        model.updateServerConfig(updatedConfig)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(all = 10.dp)
            .verticalScroll(rememberScrollState())
    ) {
        ConfigStringItem(
            item = CONFIG_ITEM_SERVER_URI,
            editItem = editItem.value,
            labelRes = R.string.pref_server_uri,
            value = model.serverConfig.serverUri,
            update = updateConfig { config, uri -> config.copy(serverUri = uri) },
            edit = editFunc,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
        )
        ConfigStringItem(
            item = CONFIG_ITEM_SERVER_PATH,
            editItem = editItem.value,
            labelRes = R.string.pref_server_path,
            value = model.serverConfig.basePath,
            update = updateConfig { config, path -> config.copy(basePath = path) },
            edit = editFunc
        )
        ConfigStringItem(
            item = CONFIG_ITEM_SERVER_USER,
            editItem = editItem.value,
            labelRes = R.string.pref_user,
            value = model.serverConfig.user,
            update = updateConfig { config, user -> config.copy(user = user) },
            edit = editFunc
        )
        ConfigStringItem(
            item = CONFIG_ITEM_SERVER_PASSWORD,
            editItem = editItem.value,
            labelRes = R.string.pref_password,
            value = model.serverConfig.password,
            update = updateConfig { config, pass -> config.copy(password = pass) },
            edit = editFunc,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
        )
    }
}

/**
 * Generate the UI for the configuration setting [item] of type [String] with the specified
 * [resource ID for the label][labelRes] and [value]. The item that is currently edited is [editItem]; this can be
 * changed via the [edit] function. Changes on the value of the item are reported using the [update] function.
 * Apply the given [visualTransformation] to the edited text and use the given [keyboardOptions] to define the
 * keyboard of the text field.
 */
@Composable
fun ConfigStringItem(
    item: String,
    editItem: String?,
    labelRes: Int,
    value: String,
    update: (String) -> Unit,
    edit: (String?) -> Unit,
    modifier: Modifier = Modifier,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default
) {
    ConfigItem(
        item = item,
        editItem = editItem,
        labelRes = labelRes,
        value = value,
        update = update,
        updateEdit = edit,
        renderer = visualTransformation::transform,
        modifier = modifier
    ) { editValue, editUpdate, editModifier ->
        TextField(
            value = editValue,
            onValueChange = editUpdate,
            modifier = editModifier
                .testTag(ConfigItemElement.EDITOR.tagForItem(item)),
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions
        )
    }
}

/**
 * Generate the UI for the configuration setting [item] of a generic type. This UI consists of a label whose content is
 * defined by [labelRes]. It either shows the current [value] of the item (or a string representation of it generated
 * by the [renderer] function) or a type-specific editor produced by [configEditor]. Whether the editor is displayed
 * or not depends on [editItem]: if this is the current [item], the editor is active. With the [updateEdit] function,
 * the currently edited item can be changed, e.g. when the user clicks on the cancel button. Changes on the edited
 * value are reported upwards using the [update] function.
 * This function takes care about managing the whole edit state and providing the buttons to commit the new value or
 * cancel the edit operation. Via the [configEditor] function, arbitrary editor controls can be injected.
 */
@Composable
fun <T> ConfigItem(
    item: String,
    editItem: String?,
    labelRes: Int,
    value: T,
    update: (T) -> Unit,
    updateEdit: (String?) -> Unit,
    renderer: ConfigItemRenderer<T> = { it.toString().toAnnotatedString() },
    modifier: Modifier = Modifier,
    configEditor: ConfigEditor<T>
) {
    var editorValue by rememberSaveable { mutableStateOf(value) }
    val inEditMode = item == editItem
    val startEdit: () -> Unit = { updateEdit(item) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 15.dp)
    ) {
        Text(
            text = stringResource(id = labelRes),
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            modifier = modifier
                .testTag(ConfigItemElement.LABEL.tagForItem(item))
                .clickable(onClick = startEdit)
        )
        if (!inEditMode) {
            Text(
                text = renderer(value),
                modifier = modifier
                    .testTag(ConfigItemElement.VALUE.tagForItem(item))
                    .clickable(onClick = startEdit)
                    .padding(start = 10.dp)
            )
        } else {

            configEditor(editorValue, { editorValue = it }, modifier.padding(start = 10.dp))
            Row(modifier = modifier.align(Alignment.CenterHorizontally)) {
                Button(
                    onClick = {
                        update(editorValue)
                        updateEdit(null)
                    },
                    modifier = modifier.testTag(ConfigItemElement.COMMIT_BUTTON.tagForItem(item))
                ) {
                    Text(text = stringResource(id = R.string.pref_btn_save))
                }
                Spacer(modifier = modifier.width(4.dp))
                Button(
                    onClick = {
                        updateEdit(null)
                        editorValue = value
                    },
                    modifier = modifier.testTag(ConfigItemElement.CANCEL_BUTTON.tagForItem(item))
                ) {
                    Text(text = stringResource(id = R.string.pref_btn_cancel))
                }
            }
        }
    }
}

/**
 * Convert this string to an [AnnotatedString].
 */
private fun String.toAnnotatedString(): AnnotatedString = buildAnnotatedString { append(this@toAnnotatedString) }

/**
 * Apply this [VisualTransformation] to the given plain [text].
 */
private fun VisualTransformation.transform(text: String): AnnotatedString = filter(text.toAnnotatedString()).text

@Preview(showBackground = true)
@Composable
fun ConfigPreview() {
    val model = PreviewTrackViewModel()

    ServerConfigView(model = model)
}
