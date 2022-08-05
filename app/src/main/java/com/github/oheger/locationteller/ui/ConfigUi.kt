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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

import com.github.oheger.locationteller.R
import com.github.oheger.locationteller.config.TrackServerConfig
import com.github.oheger.locationteller.ui.state.TrackViewModel
import com.github.oheger.locationteller.ui.state.TrackViewModelImpl

internal const val CONFIG_ITEM_SERVER_URI = "config_server_uri"
internal const val CONFIG_ITEM_SERVER_PATH = "config_server_path"
internal const val CONFIG_ITEM_SERVER_USER = "config_server_username"

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
    ) {
        ConfigItem(
            item = CONFIG_ITEM_SERVER_URI,
            editItem = editItem.value,
            labelRes = R.string.pref_server_uri,
            value = model.serverConfig.serverUri,
            update = updateConfig { config, uri -> config.copy(serverUri = uri) },
            edit = editFunc
        )
        ConfigItem(
            item = CONFIG_ITEM_SERVER_PATH,
            editItem = editItem.value,
            labelRes = R.string.pref_server_path,
            value = model.serverConfig.basePath,
            update = updateConfig { config, path -> config.copy(basePath = path) },
            edit = editFunc
        )
        ConfigItem(
            item = CONFIG_ITEM_SERVER_USER,
            editItem = editItem.value,
            labelRes = R.string.pref_user,
            value = model.serverConfig.user,
            update = updateConfig { config, user -> config.copy(user = user) },
            edit = editFunc
        )
    }
}

/**
 * Generate the UI for the configuration setting [item] with the specified [resource ID for the label][labelRes] and
 * [value]. The item that is currently edited is [editItem]; this can be changed via the [edit] function. Changes on
 * the value of the item are reported using the [update] function.
 */
@Composable
fun ConfigItem(
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
    var editorText by rememberSaveable { mutableStateOf<String?>(null) }
    val inEditMode = item == editItem
    val startEdit: () -> Unit = { edit(item) }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(id = labelRes), modifier = modifier
                .testTag(ConfigItemElement.LABEL.tagForItem(item))
                .clickable(onClick = startEdit)
        )
        if (!inEditMode) {
            Text(
                text = visualTransformation.transform(value), modifier = modifier
                    .testTag(ConfigItemElement.VALUE.tagForItem(item))
                    .clickable(onClick = startEdit)
            )
        } else {

            TextField(
                value = editorText ?: value,
                onValueChange = { editorText = it },
                modifier = modifier.testTag(ConfigItemElement.EDITOR.tagForItem(item)),
                visualTransformation = visualTransformation,
                keyboardOptions = keyboardOptions
            )
            Row {
                Button(
                    onClick = {
                        update(editorText.orEmpty())
                        editorText = null
                        edit(null)
                    },
                    modifier = modifier.testTag(ConfigItemElement.COMMIT_BUTTON.tagForItem(item))
                ) {
                    Text(text = stringResource(id = R.string.pref_btn_save))
                }
                Spacer(modifier = modifier.width(4.dp))
                Button(
                    onClick = {
                        edit(null)
                        editorText = null
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
 * Apply this [VisualTransformation] to the given plain [text].
 */
private fun VisualTransformation.transform(text: String): AnnotatedString {
    val annotatedString = buildAnnotatedString { append(text) }
    return filter(annotatedString).text
}

@Preview(showBackground = true)
@Composable
fun ConfigPreview() {
    val model = PreviewTrackViewModel()

    ServerConfigView(model = model)
}
