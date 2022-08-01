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
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

import com.github.oheger.locationteller.R

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
    modifier: Modifier = Modifier
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
                text = value, modifier = modifier
                    .testTag(ConfigItemElement.VALUE.tagForItem(item))
                    .clickable(onClick = startEdit)
            )
        } else {

            TextField(
                value = editorText ?: value,
                onValueChange = { editorText = it },
                modifier = modifier.testTag(ConfigItemElement.EDITOR.tagForItem(item))
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

@Preview(showBackground = true)
@Composable
fun ConfigPreview() {
    Column {
        ConfigItem(
            item = "Minimum Interval",
            editItem = null,
            labelRes = R.string.pref_server_uri,
            value = "100",
            update = {},
            edit = {})
        ConfigItem(
            item = "Maximum Interval",
            editItem = "Maximum Interval",
            labelRes = R.string.pref_server_path,
            value = "500",
            update = {},
            edit = {})
    }
}