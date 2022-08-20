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
import com.github.oheger.locationteller.config.TrackConfig
import com.github.oheger.locationteller.config.TrackServerConfig
import com.github.oheger.locationteller.ui.state.DurationEditorModel
import com.github.oheger.locationteller.ui.state.TrackStatsFormatter
import com.github.oheger.locationteller.ui.state.TrackViewModel
import com.github.oheger.locationteller.ui.state.TrackViewModelImpl

import java.text.NumberFormat
import java.text.ParsePosition
import java.util.EnumMap

internal const val CONFIG_ITEM_SERVER_URI = "config_server_uri"
internal const val CONFIG_ITEM_SERVER_PATH = "config_server_path"
internal const val CONFIG_ITEM_SERVER_USER = "config_server_username"
internal const val CONFIG_ITEM_SERVER_PASSWORD = "config_server_password"

internal const val CONFIG_ITEM_TRACK_MIN_INTERVAL = "config_track_min_interval"
internal const val CONFIG_ITEM_TRACK_MAX_INTERVAL = "config_track_max_interval"
internal const val CONFIG_ITEM_TRACK_IDLE_INCREMENT = "config_track_idle_increment"
internal const val CONFIG_ITEM_TRACK_LOCATION_VALIDITY = "config_track_location_validity"
internal const val CONFIG_ITEM_TRACK_LOCATION_UPDATE_THRESHOLD = "config_track_location_update_threshold"
internal const val CONFIG_ITEM_TRACK_GPS_TIMEOUT = "config_track_gps_timeout"
internal const val CONFIG_ITEM_TRACK_RETRY_ERROR_TIME = "config_track_retry_error_time"

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
 * Generate the UI for the configuration of the tracking settings. This is the entry point into this configuration UI.
 */
@Composable
fun TrackConfigUi(model: TrackViewModelImpl = viewModel(), modifier: Modifier = Modifier) {
    TrackConfigView(model = model, modifier = modifier)
}

/**
 * Generate the view for displaying and changing tracking-related configuration settings using [model] as view model.
 */
@Composable
fun TrackConfigView(model: TrackViewModel, modifier: Modifier = Modifier) {
    val editItem = rememberSaveable { mutableStateOf<String?>(null) }
    val editFunc: (String?) -> Unit = { editItem.value = it }

    fun <T> updateConfig(updateFunc: (TrackConfig, T) -> TrackConfig): (T) -> Unit = { value ->
        val trackConfig = model.trackConfig
        val updatedConfig = updateFunc(trackConfig, value)
        model.updateTrackConfig(updatedConfig)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(all = 10.dp)
            .verticalScroll(rememberScrollState())
    ) {
        ConfigDurationItem(
            item = CONFIG_ITEM_TRACK_MIN_INTERVAL,
            editItem = editItem.value,
            labelRes = R.string.pref_min_track_interval,
            value = model.trackConfig.minTrackInterval,
            formatter = model.formatter,
            maxComponent = DurationEditorModel.Component.MINUTE,
            update = updateConfig { config, minInterval -> config.copy(minTrackInterval = minInterval) },
            updateEdit = editFunc,
            modifier = modifier
        )
        ConfigDurationItem(
            item = CONFIG_ITEM_TRACK_MAX_INTERVAL,
            editItem = editItem.value,
            labelRes = R.string.pref_max_track_interval,
            value = model.trackConfig.maxTrackInterval,
            formatter = model.formatter,
            maxComponent = DurationEditorModel.Component.MINUTE,
            update = updateConfig { config, maxInterval -> config.copy(maxTrackInterval = maxInterval) },
            updateEdit = editFunc,
            modifier = modifier
        )
        ConfigDurationItem(
            item = CONFIG_ITEM_TRACK_IDLE_INCREMENT,
            editItem = editItem.value,
            labelRes = R.string.pref_interval_idle_increment,
            value = model.trackConfig.intervalIncrementOnIdle,
            formatter = model.formatter,
            maxComponent = DurationEditorModel.Component.MINUTE,
            update = updateConfig { config, increment -> config.copy(intervalIncrementOnIdle = increment) },
            updateEdit = editFunc,
            modifier = modifier
        )
        ConfigDurationItem(
            item = CONFIG_ITEM_TRACK_LOCATION_VALIDITY,
            editItem = editItem.value,
            labelRes = R.string.pref_validity_time,
            value = model.trackConfig.locationValidity,
            formatter = model.formatter,
            maxComponent = DurationEditorModel.Component.DAY,
            update = updateConfig { config, validity -> config.copy(locationValidity = validity) },
            updateEdit = editFunc,
            modifier = modifier
        )
        ConfigIntItem(
            item = CONFIG_ITEM_TRACK_LOCATION_UPDATE_THRESHOLD,
            editItem = editItem.value,
            labelRes = R.string.pref_location_update_threshold,
            value = model.trackConfig.locationUpdateThreshold,
            update = updateConfig { config, threshold -> config.copy(locationUpdateThreshold = threshold) },
            updateEdit = editFunc,
            modifier
        )
        ConfigDurationItem(
            item = CONFIG_ITEM_TRACK_GPS_TIMEOUT,
            editItem = editItem.value,
            labelRes = R.string.pref_gps_timeout,
            value = model.trackConfig.gpsTimeout,
            formatter = model.formatter,
            maxComponent = DurationEditorModel.Component.MINUTE,
            update = updateConfig { config, timeout -> config.copy(gpsTimeout = timeout) },
            updateEdit = editFunc,
            modifier = modifier
        )
        ConfigDurationItem(
            item = CONFIG_ITEM_TRACK_RETRY_ERROR_TIME,
            editItem = editItem.value,
            labelRes = R.string.pref_error_retry_time,
            value = model.trackConfig.retryOnErrorTime,
            formatter = model.formatter,
            maxComponent = DurationEditorModel.Component.MINUTE,
            update = updateConfig { config, time -> config.copy(retryOnErrorTime = time) },
            updateEdit = editFunc,
            modifier = modifier
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
        renderer = visualTransformation::transform,
        modifier = modifier,
        configEditor = ConfigTextFieldEditor(
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
    val configEditor = ConfigIntFieldEditor(tag = ConfigItemElement.EDITOR.tagForItem(item))

    ConfigItem(
        item = item,
        editItem = editItem,
        labelRes = labelRes,
        value = value,
        update = update,
        updateEdit = updateEdit,
        invalidInputHandler = { errorMessage },
        modifier = modifier,
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
    val configEditor = ConfigTextFieldEditor(
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
        invalidInputHandler = { errorMessage },
        modifier = modifier,
        configEditor = configEditor,
        renderer = renderDoubleAnn
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
    maxComponent: DurationEditorModel.Component,
    update: (Int) -> Unit,
    updateEdit: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val renderDuration: ConfigItemRenderer<Int> = { duration ->
        formatter.formatDuration(duration * 1000L).orEmpty().toAnnotatedString()
    }
    val configEditor = DurationEditor(item = item, maxComponent = maxComponent)

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
        invalidInputHandler = invalidInputHandler,
        modifier = modifier,
        configEditor = configEditor,
        renderer = renderDuration
    )
}

/**
 * Generate an editor for the given configuration [item] of type duration. The editor consists of multiple numeric
 * fields for the single components up to [maxComponent].
 */
@Composable
private fun DurationEditor(item: String, maxComponent: DurationEditorModel.Component): ConfigEditor<Int> =
    { duration, durationUpdate, modifier ->
        val durationState by rememberSaveable(stateSaver = DurationEditorModel.SAVER) {
            mutableStateOf(DurationEditorModel.create(duration, maxComponent))
        }
        val errorState by rememberSaveable {
            mutableStateOf(EnumMap<DurationEditorModel.Component, Throwable>(DurationEditorModel.Component::class.java))
        }

        val componentLabels = mapOf(
            DurationEditorModel.Component.SECOND to R.string.time_secs,
            DurationEditorModel.Component.MINUTE to R.string.time_minutes,
            DurationEditorModel.Component.HOUR to R.string.time_hours,
            DurationEditorModel.Component.DAY to R.string.time_days
        ).mapValues { stringResource(id = it.value) }

        fun componentUpdater(component: DurationEditorModel.Component): ConfigUpdater<Int> = { result ->
            result.onSuccess { value ->
                durationState[component] = value
                errorState -= component
            }
            result.onFailure { exception ->
                errorState[component] = exception
            }
            val updateResult = if (errorState.isNotEmpty()) {
                val invalidComponents = errorState.keys.sortedBy { it.ordinal }.map(componentLabels::getValue)
                Result.failure(InvalidDurationException(invalidComponents))
            } else {
                Result.success(durationState.duration())
            }
            durationUpdate(updateResult)
        }

        if (maxComponent == DurationEditorModel.Component.DAY) {
            DurationComponentField(
                item = item,
                labelRes = R.string.time_days,
                index = 3,
                value = durationState[DurationEditorModel.Component.DAY],
                update = componentUpdater(DurationEditorModel.Component.DAY),
                modifier = modifier
            )
        }
        if (maxComponent >= DurationEditorModel.Component.HOUR) {
            DurationComponentField(
                item = item,
                labelRes = R.string.time_hours,
                index = 2,
                value = durationState[DurationEditorModel.Component.HOUR],
                update = componentUpdater(DurationEditorModel.Component.HOUR),
                modifier = modifier
            )
        }
        DurationComponentField(
            item = item,
            labelRes = R.string.time_minutes,
            index = 1,
            value = durationState[DurationEditorModel.Component.MINUTE],
            update = componentUpdater(DurationEditorModel.Component.MINUTE),
            modifier = modifier
        )
        DurationComponentField(
            item = item,
            labelRes = R.string.time_secs,
            index = 0,
            value = durationState[DurationEditorModel.Component.SECOND],
            update = componentUpdater(DurationEditorModel.Component.SECOND),
            modifier = modifier
        )
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
    Row(modifier = modifier.padding(bottom = 8.dp)) {
        val tag = ConfigItemElement.EDITOR.tagForIndexedItem(item, index)
        val editorFunc = ConfigIntFieldEditor(tag = tag)
        editorFunc(value, update, modifier)
        Text(text = stringResource(id = labelRes), modifier = modifier.padding(start = 4.dp, end = 10.dp))
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
    renderer: ConfigItemRenderer<T> = { it.toString().toAnnotatedString() },
    invalidInputHandler: ConfigInvalidInputHandler = ::defaultInvalidInputHandler,
    modifier: Modifier = Modifier,
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

            val updater: ConfigUpdater<T> = { result ->
                result.onSuccess {
                    editorValue = it
                    editorFailure = null
                }
                result.onFailure { editorFailure = it }
            }

            configEditor(editorValue, updater, modifier.padding(start = 10.dp))
            editorFailure?.let { exception ->
                Text(
                    text = invalidInputHandler(exception),
                    color = Color.Red,
                    modifier = modifier
                        .padding(start = 10.dp)
                        .testTag(ConfigItemElement.ERROR_MESSAGE.tagForItem(item))
                )
            }

            Row(modifier = modifier.align(Alignment.CenterHorizontally)) {
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
 * Return a [ConfigEditor] of a specific data type consisting of a single [TextField]. Validate user input using the
 * given [validation function][validate]. Obtain the string to be passed to the [TextField] via the given
 * [renderer function][renderer]. Add the test tag [tag]. Optionally, a [visualTransformation] and [keyboardOptions]
 * can be specified.
 */
@Composable
private fun <T> ConfigTextFieldEditor(
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
        modifier = editModifier.testTag(tag),
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions
    )
}

/**
 * Return a [ConfigEditor] consisting of a single [TextField] that allows entering an integer number. Add the test tag
 * [tag].
 */
@Composable
private fun ConfigIntFieldEditor(tag: String): ConfigEditor<Int> {
    val validateInt: (String) -> Result<Int> = { strValue -> runCatching { strValue.toInt() } }
    return ConfigTextFieldEditor(
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
    Column {
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
            maxComponent = DurationEditorModel.Component.MINUTE,
            update = {},
            updateEdit = {}
        )

        ConfigDurationItem(
            item = "longDurationItem",
            editItem = "longDurationItem",
            labelRes = R.string.pref_validity_time,
            value = 386185,
            formatter = TrackStatsFormatter.create(),
            maxComponent = DurationEditorModel.Component.DAY,
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

@Preview(showBackground = true)
@Composable
fun TrackConfigPreview() {
    val model = PreviewTrackViewModel()

    TrackConfigView(model = model)
}
