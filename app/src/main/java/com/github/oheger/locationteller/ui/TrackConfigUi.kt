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
import androidx.compose.material.Tab
import androidx.compose.material.TabRow
import androidx.compose.material.Text
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
import androidx.lifecycle.viewmodel.compose.viewModel

import com.github.oheger.locationteller.R
import com.github.oheger.locationteller.config.TrackConfig
import com.github.oheger.locationteller.duration.DurationModel
import com.github.oheger.locationteller.ui.state.TrackStatsFormatter
import com.github.oheger.locationteller.ui.state.TrackViewModel
import com.github.oheger.locationteller.ui.state.TrackViewModelImpl

internal const val CONFIG_ITEM_TRACK_TAB_BASIC = "config_track_tab_basic"
internal const val CONFIG_ITEM_TRACK_TAB_ADVANCED = "config_track_tab_advanced"
internal const val CONFIG_ITEM_TRACK_MIN_INTERVAL = "config_track_min_interval"
internal const val CONFIG_ITEM_TRACK_MAX_INTERVAL = "config_track_max_interval"
internal const val CONFIG_ITEM_TRACK_IDLE_INCREMENT = "config_track_idle_increment"
internal const val CONFIG_ITEM_TRACK_LOCATION_VALIDITY = "config_track_location_validity"
internal const val CONFIG_ITEM_TRACK_LOCATION_UPDATE_THRESHOLD = "config_track_location_update_threshold"
internal const val CONFIG_ITEM_TRACK_GPS_TIMEOUT = "config_track_gps_timeout"
internal const val CONFIG_ITEM_TRACK_RETRY_ERROR_TIME = "config_track_retry_error_time"
internal const val CONFIG_ITEM_TRACK_AUTO_RESET_STATS = "config_track_auto_reset_stats"
internal const val CONFIG_ITEM_TRACK_OFFLINE_STORAGE_SIZE = "config_track_offline_storage_size"
internal const val CONFIG_ITEM_TRACK_OFFLINE_SYNC_TIME = "config_track_offline_sync_time"
internal const val CONFIG_ITEM_TRACK_UPLOAD_CHUNK_SIZE = "config_track_upload_chunk_size"
internal const val CONFIG_ITEM_TRACK_MAX_SPEED_INCREASE = "config_track_max_speed_increase"
internal const val CONFIG_ITEM_TRACK_WALKING_SPEED = "config_track_walking_speed"

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
    var tabIndex by rememberSaveable { mutableStateOf(0) }
    val tabData = listOf(
        R.string.pref_track_basic to CONFIG_ITEM_TRACK_TAB_BASIC,
        R.string.pref_track_advanced to CONFIG_ITEM_TRACK_TAB_ADVANCED
    )

    Column(modifier = modifier) {
        TabRow(selectedTabIndex = tabIndex) {
            tabData.forEachIndexed { index, (label, tag) ->
                Tab(
                    selected = tabIndex == index,
                    onClick = { tabIndex = index },
                    text = { Text(text = stringResource(id = label)) },
                    modifier = modifier.testTag(tag)
                )
            }
        }

        if (tabIndex == 1) {
            AdvancedTrackConfig(
                trackConfig = model.trackConfig,
                update = { model.updateTrackConfig(it) },
                formatter = model.formatter,
                modifier = modifier
            )
        } else {
            BasicTrackConfig(
                trackConfig = model.trackConfig,
                update = { model.updateTrackConfig(it) },
                formatter = model.formatter,
                modifier = modifier
            )
        }
    }
}

/**
 * Generate the UI for the basic tracking configuration settings based on [trackConfig]. Report changes on the
 * configuration via the given [update] function. Use [formatter] to format numbers and durations.
 */
@Composable
private fun BasicTrackConfig(
    trackConfig: TrackConfig,
    update: (TrackConfig) -> Unit,
    formatter: TrackStatsFormatter,
    modifier: Modifier
) {
    val editItem = rememberSaveable { mutableStateOf<String?>(null) }
    val editFunc: (String?) -> Unit = { editItem.value = it }

    fun <T> updateConfig(updateFunc: (TrackConfig, T) -> TrackConfig): (T) -> Unit = { value ->
        update(updateFunc(trackConfig, value))
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(all = 10.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(text = stringResource(id = R.string.pref_track_basic_intro), modifier = modifier.padding(bottom = 2.dp))
        ConfigDurationItem(
            item = CONFIG_ITEM_TRACK_MIN_INTERVAL,
            editItem = editItem.value,
            labelRes = R.string.pref_min_track_interval,
            value = trackConfig.minTrackInterval,
            formatter = formatter,
            maxComponent = DurationModel.Component.MINUTE,
            update = updateConfig { config, minInterval -> config.copy(minTrackInterval = minInterval) },
            updateEdit = editFunc,
            modifier = modifier
        )
        ConfigDurationItem(
            item = CONFIG_ITEM_TRACK_MAX_INTERVAL,
            editItem = editItem.value,
            labelRes = R.string.pref_max_track_interval,
            value = trackConfig.maxTrackInterval,
            formatter = formatter,
            maxComponent = DurationModel.Component.MINUTE,
            update = updateConfig { config, maxInterval -> config.copy(maxTrackInterval = maxInterval) },
            updateEdit = editFunc,
            modifier = modifier
        )
        ConfigDurationItem(
            item = CONFIG_ITEM_TRACK_IDLE_INCREMENT,
            editItem = editItem.value,
            labelRes = R.string.pref_interval_idle_increment,
            value = trackConfig.intervalIncrementOnIdle,
            formatter = formatter,
            maxComponent = DurationModel.Component.MINUTE,
            update = updateConfig { config, increment -> config.copy(intervalIncrementOnIdle = increment) },
            updateEdit = editFunc,
            modifier = modifier
        )
        ConfigDurationItem(
            item = CONFIG_ITEM_TRACK_LOCATION_VALIDITY,
            editItem = editItem.value,
            labelRes = R.string.pref_validity_time,
            value = trackConfig.locationValidity,
            formatter = formatter,
            maxComponent = DurationModel.Component.DAY,
            update = updateConfig { config, validity -> config.copy(locationValidity = validity) },
            updateEdit = editFunc,
            modifier = modifier
        )
        ConfigBooleanItem(
            item = CONFIG_ITEM_TRACK_AUTO_RESET_STATS,
            labelRes = R.string.pref_auto_reset_stats,
            value = trackConfig.autoResetStats,
            update = updateConfig { config, reset -> config.copy(autoResetStats = reset) },
            modifier = modifier
        )
    }
}

/**
 * Generate the UI for the advanced tracking configuration settings based on [trackConfig]. Report changes on the
 * configuration via the given [update] function. Use [formatter] to format numbers and durations.
 */
@Composable
private fun AdvancedTrackConfig(
    trackConfig: TrackConfig,
    update: (TrackConfig) -> Unit,
    formatter: TrackStatsFormatter,
    modifier: Modifier
) {
    val editItem = rememberSaveable { mutableStateOf<String?>(null) }
    val editFunc: (String?) -> Unit = { editItem.value = it }

    fun <T> updateConfig(updateFunc: (TrackConfig, T) -> TrackConfig): (T) -> Unit = { value ->
        update(updateFunc(trackConfig, value))
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(all = 10.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(text = stringResource(id = R.string.pref_track_advanced_intro), modifier = modifier.padding(bottom = 2.dp))
        ConfigDurationItem(
            item = CONFIG_ITEM_TRACK_RETRY_ERROR_TIME,
            editItem = editItem.value,
            labelRes = R.string.pref_error_retry_time,
            value = trackConfig.retryOnErrorTime,
            formatter = formatter,
            maxComponent = DurationModel.Component.MINUTE,
            update = updateConfig { config, time -> config.copy(retryOnErrorTime = time) },
            updateEdit = editFunc,
            modifier = modifier
        )
        ConfigDurationItem(
            item = CONFIG_ITEM_TRACK_GPS_TIMEOUT,
            editItem = editItem.value,
            labelRes = R.string.pref_gps_timeout,
            value = trackConfig.gpsTimeout,
            formatter = formatter,
            maxComponent = DurationModel.Component.MINUTE,
            update = updateConfig { config, timeout -> config.copy(gpsTimeout = timeout) },
            updateEdit = editFunc,
            modifier = modifier
        )
        ConfigIntItem(
            item = CONFIG_ITEM_TRACK_LOCATION_UPDATE_THRESHOLD,
            editItem = editItem.value,
            labelRes = R.string.pref_location_update_threshold,
            value = trackConfig.locationUpdateThreshold,
            update = updateConfig { config, threshold -> config.copy(locationUpdateThreshold = threshold) },
            updateEdit = editFunc,
            modifier
        )
        ConfigIntItem(
            item = CONFIG_ITEM_TRACK_OFFLINE_STORAGE_SIZE,
            editItem = editItem.value,
            labelRes = R.string.pref_offline_storage_size,
            value = trackConfig.offlineStorageSize,
            update = updateConfig { config, size -> config.copy(offlineStorageSize = size) },
            updateEdit = editFunc,
            modifier
        )
        ConfigDurationItem(
            item = CONFIG_ITEM_TRACK_OFFLINE_SYNC_TIME,
            editItem = editItem.value,
            labelRes = R.string.pref_offline_sync_time,
            value = trackConfig.maxOfflineStorageSyncTime,
            formatter = formatter,
            maxComponent = DurationModel.Component.MINUTE,
            update = updateConfig { config, syncTime -> config.copy(maxOfflineStorageSyncTime = syncTime) },
            updateEdit = editFunc,
            modifier = modifier
        )
        ConfigIntItem(
            item = CONFIG_ITEM_TRACK_UPLOAD_CHUNK_SIZE,
            editItem = editItem.value,
            labelRes = R.string.pref_multi_upload_chunk_size,
            value = trackConfig.multiUploadChunkSize,
            update = updateConfig { config, size -> config.copy(multiUploadChunkSize = size) },
            updateEdit = editFunc,
            modifier
        )
        ConfigDoubleItem(
            item = CONFIG_ITEM_TRACK_MAX_SPEED_INCREASE,
            editItem = editItem.value,
            labelRes = R.string.pref_max_speed_increase,
            value = trackConfig.maxSpeedIncrease,
            update = updateConfig { config, factor -> config.copy(maxSpeedIncrease = factor) },
            updateEdit = editFunc,
            formatter = formatter.numberFormat,
            modifier = modifier
        )
        ConfigDoubleItem(
            item = CONFIG_ITEM_TRACK_WALKING_SPEED,
            editItem = editItem.value,
            labelRes = R.string.pref_walking_speed,
            value = trackConfig.walkingSpeedKmH,
            update = updateConfig { config, speed -> config.updateWalkingSpeedKmH(speed) },
            updateEdit = editFunc,
            formatter = formatter.numberFormat,
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

@Preview(showBackground = true)
@Composable
fun TrackConfigAdvancedPreview() {
    val model = PreviewTrackViewModel()

    AdvancedTrackConfig(trackConfig = model.trackConfig, update = {}, formatter = model.formatter, modifier = Modifier)
}
