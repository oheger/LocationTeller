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
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.github.oheger.locationteller.duration.DurationModel
import com.github.oheger.locationteller.ui.state.TrackStatsFormatter
import com.github.oheger.locationteller.ui.state.TrackViewModel
import com.github.oheger.locationteller.ui.state.TrackViewModelImpl
import com.github.oheger.locationteller.ui.state.rememberDuration

import java.text.NumberFormat
import java.text.ParsePosition

/** The indent of an editor relative to the label of a configuration item. */
internal const val EDITOR_INDENT = 10

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
    CANCEL_BUTTON,
    ERROR_MESSAGE;

    /**
     * Generate a tag for this input element of the given configuration [item].
     */
    fun tagForItem(item: String): String = tagForIndexedItem(item, 0)

    /**
     * Generate a tag for this input element which can occur multiple times of the given configuration [item]. Use
     * [index] to generate a unique tag.
     */
    fun tagForIndexedItem(item: String, index: Int): String {
        val indexStr = index.takeIf { it > 0 }?.toString().orEmpty()
        return "tag_${item}_$name$indexStr"
    }
}

/**
 * Type definition of a function that updates the value of a configuration item. Since the value entered by the user
 * may be invalid, a [Result] is passed to the function. The receiver can then decide how to handle such failures.
 */
typealias ConfigUpdater<T> = (Result<T>) -> Unit

/**
 * Type definition for a function that provides an editor for [ConfigItem] of a specific type. The current value of
 * this is editor is hoisted. It is passed as argument to the function as well as the update function. When updating
 * the value the conversion to the target type may fail; therefore, the update function expects a [Result] object. In
 * case of a failure, the UI displays an error message.
 */
typealias ConfigEditor<T> = @Composable (T, ConfigUpdater<T>, Modifier) -> Unit

/**
 * Type definition for a function that generates a string representation of a configuration item. This function is
 * used by [ConfigItem] to generate the display text based on the current value of the item
 */
typealias ConfigItemRenderer<T> = (T) -> AnnotatedString

/**
 * Type definition for a function that is called by [ConfigItem] when the value entered by the user cannot be
 * converted to the target type. The function can then produce a corresponding error message to be displayed.
 */
typealias ConfigInvalidInputHandler = (Throwable) -> AnnotatedString

/**
 * Type definition for a list that stores invalid components of a duration together with a corresponding exception.
 * This is used by the duration editor to generate error messages for fields that are not filled correctly.
 */
typealias InvalidDurationComponents = List<Pair<DurationModel.Component, Throwable>>

/**
 * Generate the UI for the configuration of the track server settings. This is the entry point into this configuration
 * UI.
 */
@Composable
fun ServerConfigUi(modifier: Modifier = Modifier, model: TrackViewModelImpl = viewModel()) {
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
            updateEdit = editFunc,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
        )
        ConfigStringItem(
            item = CONFIG_ITEM_SERVER_PATH,
            editItem = editItem.value,
            labelRes = R.string.pref_server_path,
            value = model.serverConfig.basePath,
            update = updateConfig { config, path -> config.copy(basePath = path) },
            updateEdit = editFunc
        )
        ConfigStringItem(
            item = CONFIG_ITEM_SERVER_USER,
            editItem = editItem.value,
            labelRes = R.string.pref_user,
            value = model.serverConfig.user,
            update = updateConfig { config, user -> config.copy(user = user) },
            updateEdit = editFunc
        )
        ConfigStringItem(
            item = CONFIG_ITEM_SERVER_PASSWORD,
            editItem = editItem.value,
            labelRes = R.string.pref_password,
            value = model.serverConfig.password,
            update = updateConfig { config, pass -> config.copy(password = pass) },
            updateEdit = editFunc,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
        )
    }
}

/**
 * Generate the UI for the configuration setting [item] of type [String] with the specified
 * [resource ID for the label][labelRes] and [value]. The item that is currently edited is [editItem]; this can be
 * changed via the [updateEdit] function. Changes on the value of the item are reported using the [update] function.
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
    updateEdit: (String?) -> Unit,
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
        updateEdit = updateEdit,
        modifier = modifier,
        renderer = visualTransformation::transform,
        configEditor = configTextFieldEditor(
            tag = ConfigItemElement.EDITOR.tagForItem(item),
            validate = { Result.success(it) },
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions
        )
    )
}

/**
 * Generate the UI for the configuration setting [item] of type [Int] with the specified
 * [resource ID for the label][labelRes] and [value]. The item that is currently edited is [editItem]; this can be
 * changed via the [updateEdit] function. Changes on the value of the item are reported using the [update] function.
 */
@Composable
fun ConfigIntItem(
    item: String,
    editItem: String?,
    labelRes: Int,
    value: Int,
    update: (Int) -> Unit,
    updateEdit: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val errorMessage = stringResource(id = R.string.pref_err_no_number).toAnnotatedString()
    val configEditor = configIntFieldEditor(tag = ConfigItemElement.EDITOR.tagForItem(item))

    ConfigItem(
        item = item,
        editItem = editItem,
        labelRes = labelRes,
        value = value,
        update = update,
        updateEdit = updateEdit,
        modifier = modifier,
        invalidInputHandler = { errorMessage },
        configEditor = configEditor
    )
}

/**
 * Generate the UI for the configuration setting [item] of type [Double] with the specified
 * [resource ID for the label][labelRes] and [value]. The item that is currently edited is [editItem]; this can be
 * changed via the [updateEdit] function. Changes on the value of the item are reported using the [update] function.
 * Use [formatter] to format the number and parse user input.
 */
@Composable
fun ConfigDoubleItem(
    item: String,
    editItem: String?,
    labelRes: Int,
    value: Double,
    update: (Double) -> Unit,
    updateEdit: (String?) -> Unit,
    formatter: NumberFormat,
    modifier: Modifier = Modifier
) {
    val errorMessage = stringResource(id = R.string.pref_err_no_number).toAnnotatedString()
    val rendererDouble: (Double) -> String = { formatter.format(it) }
    val renderDoubleAnn: ConfigItemRenderer<Double> = { rendererDouble(it).toAnnotatedString() }
    val configEditor = configTextFieldEditor(
        tag = ConfigItemElement.EDITOR.tagForItem(item),
        renderer = rendererDouble,
        validate = formatter::validateDouble,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    )

    ConfigItem(
        item = item,
        editItem = editItem,
        labelRes = labelRes,
        value = value,
        update = update,
        updateEdit = updateEdit,
        modifier = modifier,
        renderer = renderDoubleAnn,
        invalidInputHandler = { errorMessage },
        configEditor = configEditor
    )
}

/**
 * Generate the UI for the configuration setting [item] of type duration (in seconds) with the specified
 * [resource ID for the label][labelRes] and [value]. In edit mode, display editor fields for the duration components
 * up to [maxComponent]; otherwise, show a duration formatted using [formatter]. The item that is currently edited is
 * [editItem]; this can be changed via the [updateEdit] function. Changes on the value of the item are reported using
 * the [update] function.
 */
@Composable
fun ConfigDurationItem(
    item: String,
    editItem: String?,
    labelRes: Int,
    value: Int,
    formatter: TrackStatsFormatter,
    maxComponent: DurationModel.Component,
    update: (Int) -> Unit,
    updateEdit: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val renderDuration: ConfigItemRenderer<Int> = { duration ->
        formatter.formatDuration(duration * 1000L).orEmpty().toAnnotatedString()
    }
    val configEditor = durationEditor(item = item, maxComponent = maxComponent)

    val errorMessageSingle = stringResource(id = R.string.pref_err_invalid_duration_component)
    val errorMessageMulti = stringResource(id = R.string.pref_err_invalid_duration_components)
    val invalidInputHandler: ConfigInvalidInputHandler = { exception ->
        when (exception) {
            is InvalidDurationException ->
                exception.errorMessage(errorMessageSingle, errorMessageMulti).toAnnotatedString()
            else -> "".toAnnotatedString()
        }
    }

    ConfigItem(
        item = item,
        editItem = editItem,
        labelRes = labelRes,
        value = value,
        update = update,
        updateEdit = updateEdit,
        modifier = modifier,
        renderer = renderDuration,
        invalidInputHandler = invalidInputHandler,
        configEditor = configEditor
    )
}

/**
 * Generate the UI for the configuration setting [item] of type boolean with the specified
 * [resource ID for the label][labelRes] and [value]. In contrast to other functions for configuration settings, for
 * booleans, no separate editor is used; the boolean value is represented by a switch, which can be updated
 * directly. Changes on the switch state are propagated to the [update] function.
 */
@Composable
fun ConfigBooleanItem(
    item: String,
    labelRes: Int,
    value: Boolean,
    update: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier) {
        ConfigItemLabel(item = item, labelRes = labelRes, onClick = {}, modifier = modifier.padding(top = 15.dp))
        Spacer(modifier = modifier.weight(1f))
        Switch(
            checked = value,
            onCheckedChange = update,
            modifier = modifier.testTag(ConfigItemElement.EDITOR.tagForItem(item))
        )
    }
}

/**
 * Generate an editor for the given configuration [item] of type duration. The editor consists of multiple numeric
 * fields for the single components up to [maxComponent].
 */
@Composable
private fun durationEditor(item: String, maxComponent: DurationModel.Component): ConfigEditor<Int> =
    { duration, durationUpdate, modifier ->
        val durationState by rememberDuration(duration, maxComponent)
        val errorState = rememberSaveable { mutableStateOf<InvalidDurationComponents>(emptyList()) }

        val componentLabels = mapOf(
            DurationModel.Component.SECOND to R.string.time_secs,
            DurationModel.Component.MINUTE to R.string.time_minutes,
            DurationModel.Component.HOUR to R.string.time_hours,
            DurationModel.Component.DAY to R.string.time_days
        ).mapValues { stringResource(id = it.value) }

        fun componentUpdater(component: DurationModel.Component): ConfigUpdater<Int> = { result ->
            result.onSuccess { value ->
                durationState[component] = value
                errorState.value = errorState.value - component
            }
            result.onFailure { exception ->
                errorState.value = (errorState.value - component) + (component to exception)
            }
            val updateResult = if (errorState.value.isNotEmpty()) {
                val invalidComponents = errorState.value.map { it.first }.sortedBy { it.ordinal }
                    .map(componentLabels::getValue)
                Result.failure(InvalidDurationException(invalidComponents))
            } else {
                Result.success(durationState.duration())
            }
            durationUpdate(updateResult)
        }

        Column(modifier = modifier.padding(top = 8.dp)) {
            if (maxComponent == DurationModel.Component.DAY) {
                DurationComponentField(
                    item = item,
                    labelRes = R.string.time_days,
                    index = 3,
                    value = durationState[DurationModel.Component.DAY],
                    update = componentUpdater(DurationModel.Component.DAY),
                    modifier = modifier
                )
            }
            if (maxComponent >= DurationModel.Component.HOUR) {
                DurationComponentField(
                    item = item,
                    labelRes = R.string.time_hours,
                    index = 2,
                    value = durationState[DurationModel.Component.HOUR],
                    update = componentUpdater(DurationModel.Component.HOUR),
                    modifier = modifier
                )
            }
            DurationComponentField(
                item = item,
                labelRes = R.string.time_minutes,
                index = 1,
                value = durationState[DurationModel.Component.MINUTE],
                update = componentUpdater(DurationModel.Component.MINUTE),
                modifier = modifier
            )
            DurationComponentField(
                item = item,
                labelRes = R.string.time_secs,
                index = 0,
                value = durationState[DurationModel.Component.SECOND],
                update = componentUpdater(DurationModel.Component.SECOND),
                modifier = modifier
            )
        }
    }

/**
 * Generate an editor for a single component of a duration for the setting [item]. The editor consists of an integer
 * input field to display [value] and report changes to [update]. The text field is followed by a label with a text
 * defined by [labelRes]. Use [index] to generate the help tag for the edit field.
 */
@Composable
private fun DurationComponentField(
    item: String,
    labelRes: Int,
    index: Int,
    value: Int,
    update: ConfigUpdater<Int>,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier.padding(top = 8.dp)) {
        val tag = ConfigItemElement.EDITOR.tagForIndexedItem(item, index)
        val editorFunc = configIntFieldEditor(tag = tag)
        editorFunc(value, update, modifier)
        Text(
            text = stringResource(id = labelRes),
            modifier = modifier
                .padding(start = 4.dp, end = 10.dp)
                .align(Alignment.CenterVertically)
        )
    }
}

/**
 * Generate the UI for the configuration setting [item] of a generic type. This UI consists of a label whose content is
 * defined by [labelRes]. It either shows the current [value] of the item (or a string representation of it generated
 * by the [renderer] function) or a type-specific editor produced by [configEditor]. Whether the editor is displayed
 * or not depends on [editItem]: if this is the current [item], the editor is active. With the [updateEdit] function,
 * the currently edited item can be changed, e.g. when the user clicks on the cancel button. Changes on the edited
 * value are reported upwards using the [update] function. If the current value is invalid, an error message generated
 * by the given [invalidInputHandler] is displayed.
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
    modifier: Modifier = Modifier,
    renderer: ConfigItemRenderer<T> = { it.toString().toAnnotatedString() },
    invalidInputHandler: ConfigInvalidInputHandler = ::defaultInvalidInputHandler,
    configEditor: ConfigEditor<T>
) {
    var editorValue by rememberSaveable { mutableStateOf(value) }
    var editorFailure by rememberSaveable { mutableStateOf<Throwable?>(null) }
    val inEditMode = item == editItem
    val startEdit: () -> Unit = { updateEdit(item) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 15.dp)
    ) {
        ConfigItemLabel(item = item, labelRes = labelRes, onClick = startEdit)
        if (!inEditMode) {
            Text(
                text = renderer(value),
                modifier = modifier
                    .testTag(ConfigItemElement.VALUE.tagForItem(item))
                    .clickable(onClick = startEdit)
                    .padding(start = EDITOR_INDENT.dp)
            )
        } else {

            val updater: ConfigUpdater<T> = { result ->
                result.onSuccess {
                    editorValue = it
                    editorFailure = null
                }
                result.onFailure { editorFailure = it }
            }

            configEditor(editorValue, updater, modifier.padding(start = EDITOR_INDENT.dp))
            editorFailure?.let { exception ->
                Text(
                    text = invalidInputHandler(exception),
                    color = Color.Red,
                    modifier = modifier
                        .padding(start = 10.dp)
                        .testTag(ConfigItemElement.ERROR_MESSAGE.tagForItem(item))
                )
            }

            Row(modifier = modifier.padding(EDITOR_INDENT.dp)) {
                Button(
                    onClick = {
                        update(editorValue)
                        updateEdit(null)
                    },
                    enabled = editorFailure == null,
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
 * Generate the label for the given configuration [item], using the given [resource ID][labelRes]. The label can be
 * clicked, then the [onClick] callback is invoked.
 */
@Composable
private fun ConfigItemLabel(item: String, labelRes: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(id = labelRes),
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        modifier = modifier
            .testTag(ConfigItemElement.LABEL.tagForItem(item))
            .clickable(onClick = onClick)
    )
}

/**
 * Return a [ConfigEditor] of a specific data type consisting of a single [TextField]. Validate user input using the
 * given [validation function][validate]. Obtain the string to be passed to the [TextField] via the given
 * [renderer function][renderer]. Add the test tag [tag]. Optionally, a [visualTransformation] and [keyboardOptions]
 * can be specified.
 */
@Composable
private fun <T> configTextFieldEditor(
    validate: (String) -> Result<T>,
    renderer: (T) -> String = { it.toString() },
    tag: String,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default
): ConfigEditor<T> = { editValue, editUpdate, editModifier ->
    var editorText by rememberSaveable { mutableStateOf(renderer(editValue)) }

    TextField(
        value = editorText,
        onValueChange = { value ->
            editorText = value
            editUpdate(validate(value))
        },
        modifier = editModifier
            .fieldWidth(keyboardOptions)
            .testTag(tag),
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions
    )
}

/**
 * Return a [ConfigEditor] consisting of a single [TextField] that allows entering an integer number. Add the test tag
 * [tag].
 */
@Composable
private fun configIntFieldEditor(tag: String): ConfigEditor<Int> {
    val validateInt: (String) -> Result<Int> = { strValue -> runCatching { strValue.toInt() } }
    return configTextFieldEditor(
        tag = tag,
        validate = validateInt,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )
}

/**
 * Generate an [AnnotatedString] from the message of the given [exception]. This is used as the default generator
 * for error messages if invalid user input is detected. Note: If users can enter invalid data, more meaningful
 * error messages should be provided.
 */
private fun defaultInvalidInputHandler(exception: Throwable): AnnotatedString =
    exception.message.orEmpty().toAnnotatedString()

/**
 * Convert this string to an [AnnotatedString].
 */
private fun String.toAnnotatedString(): AnnotatedString = buildAnnotatedString { append(this@toAnnotatedString) }

/**
 * Apply this [VisualTransformation] to the given plain [text].
 */
private fun VisualTransformation.transform(text: String): AnnotatedString = filter(text.toAnnotatedString()).text

/**
 * Check whether [strValue] can be successfully parsed using this [NumberFormat]. If so, return a success [Result]
 * with the parsed [Double] value; otherwise, return a failure [Result].
 */
private fun NumberFormat.validateDouble(strValue: String): Result<Double> {
    val parsePosition = ParsePosition(0)
    return parse(strValue, parsePosition)
        ?.takeIf { parsePosition.index >= strValue.length }
        ?.let { Result.success(it.toDouble()) }
        ?: Result.failure(NumberFormatException("'$strValue' could not be parsed to a decimal number."))
}

/** A set with the keyboard types that cause text fields to be rendered with smaller width. */
private val SMALL_FIELD_OPTIONS = setOf(KeyboardType.Decimal, KeyboardType.Number)

/**
 * Apply a width modification to this [Modifier] based on [keyboardOptions]. Numeric fields are typically smaller than
 * others. This is implemented by this function.
 */
private fun Modifier.fieldWidth(keyboardOptions: KeyboardOptions): Modifier =
    if (keyboardOptions.keyboardType in SMALL_FIELD_OPTIONS)
        width(75.dp)
    else this

/**
 * Remove all entries referencing [component] from this list.
 */
private operator fun InvalidDurationComponents.minus(
    component: DurationModel.Component
): List<Pair<DurationModel.Component, Throwable>> =
    filterNot { it.first == component }

/**
 * An exception class to report the invalid components of a duration.
 */
private class InvalidDurationException(val invalidComponents: List<String>) : Exception() {
    /**
     * Generate an error message based on the invalid components using the correct template for a
     * [single invalid field][messageSingle] or [multiple invalid fields][messageMulti].
     */
    fun errorMessage(messageSingle: String, messageMulti: String): String {
        val template = if (invalidComponents.size > 1) messageMulti else messageSingle
        return template.replace("\$field", invalidComponents.joinToString())
    }
}

@Preview(showBackground = true)
@Composable
fun ItemsPreview() {
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        ConfigBooleanItem(item = "booleanItem", labelRes = R.string.pref_auto_reset_stats, value = true, update = {})

        ConfigIntItem(
            item = "intItem",
            editItem = "intItem",
            labelRes = R.string.pref_multi_upload_chunk_size,
            value = 20,
            update = {},
            updateEdit = {}
        )

        ConfigDoubleItem(
            item = "doubleItem",
            editItem = "doubleItem",
            labelRes = R.string.pref_walking_speed,
            value = 3.5,
            update = {},
            updateEdit = {},
            formatter = NumberFormat.getNumberInstance()
        )

        ConfigDurationItem(
            item = "shortDurationItem",
            editItem = "shortDurationItem",
            labelRes = R.string.pref_min_track_interval,
            value = 30,
            formatter = TrackStatsFormatter.create(),
            maxComponent = DurationModel.Component.MINUTE,
            update = {},
            updateEdit = {}
        )

        ConfigDurationItem(
            item = "longDurationItem",
            editItem = "longDurationItem",
            labelRes = R.string.pref_validity_time,
            value = 386185,
            formatter = TrackStatsFormatter.create(),
            maxComponent = DurationModel.Component.DAY,
            update = {},
            updateEdit = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun ServerConfigPreview() {
    val model = PreviewTrackViewModel()

    ServerConfigView(model = model)
}
