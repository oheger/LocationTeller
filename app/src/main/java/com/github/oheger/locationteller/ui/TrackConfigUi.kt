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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

import com.github.oheger.locationteller.R
import com.github.oheger.locationteller.config.TrackConfig
import com.github.oheger.locationteller.ui.state.DurationEditorModel
import com.github.oheger.locationteller.ui.state.TrackViewModel
import com.github.oheger.locationteller.ui.state.TrackViewModelImpl

internal const val CONFIG_ITEM_TRACK_MIN_INTERVAL = "config_track_min_interval"
internal const val CONFIG_ITEM_TRACK_MAX_INTERVAL = "config_track_max_interval"
internal const val CONFIG_ITEM_TRACK_IDLE_INCREMENT = "config_track_idle_increment"
internal const val CONFIG_ITEM_TRACK_LOCATION_VALIDITY = "config_track_location_validity"
internal const val CONFIG_ITEM_TRACK_LOCATION_UPDATE_THRESHOLD = "config_track_location_update_threshold"
internal const val CONFIG_ITEM_TRACK_GPS_TIMEOUT = "config_track_gps_timeout"
internal const val CONFIG_ITEM_TRACK_RETRY_ERROR_TIME = "config_track_retry_error_time"
internal const val CONFIG_ITEM_TRACK_AUTO_RESET_STATS = "config_track_auto_reset_stats"

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
        ConfigBooleanItem(
            item = CONFIG_ITEM_TRACK_AUTO_RESET_STATS,
            labelRes = R.string.pref_auto_reset_stats,
            value = model.trackConfig.autoResetStats,
            update = updateConfig { config, reset -> config.copy(autoResetStats = reset) },
            modifier = modifier
        )
    }
}

@Preview(showBackground = true)
@Composable
fun TrackConfigPreview() {
    val model = PreviewTrackViewModel()

    TrackConfigView(model = model)
}
