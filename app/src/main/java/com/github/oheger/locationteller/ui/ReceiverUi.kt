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

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

import com.github.oheger.locationteller.R
import com.github.oheger.locationteller.config.ReceiverConfig
import com.github.oheger.locationteller.duration.DurationModel
import com.github.oheger.locationteller.map.LocationFileState
import com.github.oheger.locationteller.map.MarkerFactory
import com.github.oheger.locationteller.ui.state.ReceiverAction
import com.github.oheger.locationteller.ui.state.ReceiverViewModel
import com.github.oheger.locationteller.ui.state.ReceiverViewModelImpl
import com.github.oheger.locationteller.ui.state.TrackStatsFormatter

import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MarkerInfoWindow
import com.google.maps.android.compose.MarkerState

internal const val TAG_REC_MAP_VIEW = "rec_map_view"
internal const val TAG_REC_UPDATE_INDICATOR = "rec_update_indicator"
internal const val TAG_REC_UPDATE_STATUS_TEXT = "rec_update_status_text"
internal const val TAG_REC_LOCATION_STATUS_TEXT = "rec_location_status_text"
internal const val TAG_REC_CONF_UPDATE_INTERVAL = "rec_conf_update_interval"
internal const val TAG_REC_CONF_FADE = "rec_conf_fade"
internal const val TAG_REC_CONF_FADE_FAST = "rec_conf_fade_fast"
internal const val TAG_REC_CONF_FADE_STRONG = "rec_conf_fade_strong"
internal const val TAG_REC_CONF_CENTER_NEW = "rec_conf_center_new"

internal const val TAG_REC_HEADER_ACTIONS = "actions"
internal const val TAG_REC_HEADER_SETTINGS = "settings"

/** Prefix used for tags generated for the elements of an expandable header. */
private const val TAG_REC_EXPANDABLE_HEADER_PREFIX = "rec_expandable_header_"

/**
 * Generate a help tag for the icon of an expandable header with the given [tag].
 */
internal fun expandableHeaderIconTag(tag: String): String = "$TAG_REC_EXPANDABLE_HEADER_PREFIX${tag}_icon"

/**
 * Generate a help tag for the text of an expandable header with the given [tag].
 */
internal fun expandableHeaderTextTag(tag: String): String = "$TAG_REC_EXPANDABLE_HEADER_PREFIX${tag}_caption"

/**
 * Generate a test tag for the action button that triggers the given [action].
 */
internal fun actionTag(action: ReceiverAction): String = "rec_action_$action"

/**
 * Generate the whole receiver UI. This is the entry point into this UI.
 */
@Composable
fun ReceiverUi(modifier: Modifier = Modifier, model: ReceiverViewModelImpl = viewModel()) {
    ReceiverView(model = model, modifier = modifier)
}

/**
 * Generate the view for the receiver part of the application based on the given [model].
 */
@Composable
fun ReceiverView(model: ReceiverViewModel, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Box(modifier = modifier.weight(1.0f)) {
            MapView(
                locationFileState = model.locationFileState,
                markerFactory = model.markerFactory,
                cameraState = model.cameraPositionState,
                modifier = modifier.testTag(TAG_REC_MAP_VIEW)
            )
        }
        Box(modifier = modifier) {
            ControlView(
                updateInProgress = model.isUpdating(),
                countDown = model.secondsToNextUpdateString,
                numberOfLocations = model.locationFileState.files.size,
                recentLocationTime = model.recentLocationTime(),
                config = model.receiverConfig,
                updateConfig = model::updateReceiverConfig,
                onAction = model::onAction,
                modifier
            )
        }
    }
}

/**
 * Render the map view as part of the receiver UI. Use the given [markerFactory] to display markers on the map based on
 * the given [locationFileState]. Set the position and zoom level of the map as defined by the given [cameraState].
 */
@Composable
fun MapView(
    locationFileState: LocationFileState,
    markerFactory: MarkerFactory,
    cameraState: CameraPositionState,
    modifier: Modifier = Modifier
) {
    GoogleMap(
        modifier = modifier.fillMaxSize(),
        cameraPositionState = cameraState
    ) {
        val recentMarker = locationFileState.recentMarker()
        val markerData = locationFileState.files.mapNotNull { locationFileState.markerData[it] }

        markerData.withIndex()
            .forEach { (index, data) ->
                val options = markerFactory.createMarker(
                    data,
                    recentMarker = data == recentMarker,
                    zIndex = index.toFloat()
                )
                MarkerInfoWindow(
                    state = MarkerState(position = options.position),
                    alpha = options.alpha,
                    icon = options.icon,
                    zIndex = options.zIndex,
                    title = options.title,
                    snippet = options.snippet
                )
            }
    }
}

/**
 * Generate the part of the receiver UI that allows controlling the map view. Here some status information is
 * displayed, and the user can manipulate the map. Pass [updateInProgress], [countDown], [numberOfLocations], and
 * [recentLocationTime] to the [StatusLine] function. Pass [config], and [updateConfig] to the function to edit the
 * receiver configuration.
 */
@Composable
internal fun ControlView(
    updateInProgress: Boolean,
    countDown: String,
    numberOfLocations: Int,
    recentLocationTime: String?,
    config: ReceiverConfig,
    updateConfig: (ReceiverConfig) -> Unit,
    onAction: (ReceiverAction) -> Unit,
    modifier: Modifier = Modifier
) {
    var actionsExpanded by rememberSaveable { mutableStateOf(false) }
    var settingsExpanded by rememberSaveable { mutableStateOf(false) }

    Column(modifier = modifier) {
        ExpandableHeader(
            headerRes = R.string.rec_header_actions,
            tag = TAG_REC_HEADER_ACTIONS,
            expanded = actionsExpanded,
            onChanged = { actionsExpanded = it }
        )
        if (actionsExpanded) {
            ReceiverActionView(onAction = onAction, modifier = modifier)
        }

        ExpandableHeader(
            headerRes = R.string.rec_header_settings,
            tag = TAG_REC_HEADER_SETTINGS,
            expanded = settingsExpanded,
            onChanged = { settingsExpanded = it }
        )
        if (settingsExpanded) {
            ReceiverConfigView(config = config, update = updateConfig, modifier = modifier)
        }

        StatusLine(
            updateInProgress = updateInProgress,
            countDown = countDown,
            numberOfLocations = numberOfLocations,
            recentLocationTime = recentLocationTime,
            modifier
        )
    }
}

/**
 * Generate a view with buttons corresponding to actions the user can perform on the receiver view. Report the actions
 * triggered by the user via the [onAction] function.
 */
@Composable
internal fun ReceiverActionView(onAction: (ReceiverAction) -> Unit, modifier: Modifier) {
    Row(modifier = modifier.fillMaxWidth()) {
        Spacer(modifier = modifier.weight(1f))
        Button(
            onClick = { onAction(ReceiverAction.UPDATE) },
            modifier = modifier.testTag(actionTag(ReceiverAction.UPDATE))
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_action_refresh),
                contentDescription = stringResource(id = R.string.item_update_map)
            )
        }
        Spacer(modifier = modifier.weight(1f))
        Button(
            onClick = { onAction(ReceiverAction.CENTER_RECENT_POSITION) },
            modifier = modifier.testTag(actionTag(ReceiverAction.CENTER_RECENT_POSITION))
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_action_center_last),
                contentDescription = stringResource(id = R.string.item_center_to_recent)
            )
        }
        Spacer(modifier = modifier.weight(1f))
    }
}

/**
 * Generate a view in which the user can edit the current [receiver configuration][config]. Report changes on the
 * configuration through the given [update] function.
 */
@Composable
internal fun ReceiverConfigView(config: ReceiverConfig, update: (ReceiverConfig) -> Unit, modifier: Modifier) {
    val editItem = rememberSaveable { mutableStateOf<String?>(null) }
    val editFunc: (String?) -> Unit = { editItem.value = it }

    fun <T> updateConfig(updateFunc: (ReceiverConfig, T) -> ReceiverConfig): (T) -> Unit = { value ->
        update(updateFunc(config, value))
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            //.padding(start = 10.dp, end = 10.dp, top = 0.dp, bottom = 5.dp)
            .verticalScroll(rememberScrollState())
    ) {
        ConfigDurationItem(
            item = TAG_REC_CONF_UPDATE_INTERVAL,
            editItem = editItem.value,
            labelRes = R.string.pref_rec_update_interval,
            value = config.updateInterval,
            formatter = TrackStatsFormatter.INSTANCE,
            maxComponent = DurationModel.Component.MINUTE,
            update = updateConfig { config, interval -> config.copy(updateInterval = interval) },
            updateEdit = editFunc,
            modifier = modifier
        )
        ConfigBooleanItem(
            item = TAG_REC_CONF_FADE,
            labelRes = R.string.pref_rec_fade,
            value = config.fadeOutEnabled,
            update = updateConfig { config, fade -> config.copy(fadeOutEnabled = fade) },
            modifier = modifier
        )
        ConfigBooleanItem(
            item = TAG_REC_CONF_FADE_FAST,
            labelRes = R.string.pref_rec_fade_fast,
            value = config.fastFadeOut,
            update = updateConfig { config, fade -> config.copy(fastFadeOut = fade) },
            enabled = config.fadeOutEnabled,
            modifier = modifier.padding(start = 10.dp)
        )
        ConfigBooleanItem(
            item = TAG_REC_CONF_FADE_STRONG,
            labelRes = R.string.pref_rec_fade_strong,
            value = config.strongFadeOut,
            update = updateConfig { config, fade -> config.copy(strongFadeOut = fade) },
            enabled = config.fadeOutEnabled,
            modifier = modifier.padding(start = 10.dp)
        )
        ConfigBooleanItem(
            item = TAG_REC_CONF_CENTER_NEW,
            labelRes = R.string.pref_rec_center_new,
            value = config.centerNewPosition,
            update = updateConfig { config, center -> config.copy(centerNewPosition = center) },
            modifier = modifier
        )
    }
}

/**
 * Generate the whole status line, consisting of information about an ongoing or scheduled update (defined by
 * [updateInProgress] and [countDown]), and about the locations retrieved from the server (defined by
 * [numberOfLocations] and [recentLocationTime]).
 */
@Composable
internal fun StatusLine(
    updateInProgress: Boolean,
    countDown: String,
    numberOfLocations: Int,
    recentLocationTime: String?,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(5.dp)
    ) {
        Box(modifier = modifier) {
            UpdateStatus(updateInProgress = updateInProgress, countDown = countDown, modifier = modifier)
        }
        Spacer(modifier = modifier.weight(1.0f))
        Box(modifier = modifier) {
            LocationStatus(
                numberOfLocations = numberOfLocations,
                recentLocationTime = recentLocationTime,
                modifier = modifier
            )
        }
    }
}

/**
 * Generate the part of the status line that displays the update status. If [updateInProgress] is *true*, a progress
 * indicator is displayed. Otherwise, show the time to the next update based on [countDown].
 */
@Composable
internal fun UpdateStatus(updateInProgress: Boolean, countDown: String, modifier: Modifier = Modifier) {
    val statusText = if (updateInProgress) stringResource(id = R.string.map_status_updating)
    else stringResource(id = R.string.map_status_update_scheduled, countDown)

    Row(modifier = modifier) {
        if (updateInProgress) {
            val infiniteTransition = rememberInfiniteTransition()
            val progressAnimationValue by infiniteTransition.animateFloat(
                initialValue = 0.0f,
                targetValue = 1.0f,
                animationSpec = infiniteRepeatable(animation = tween(900))
            )
            CircularProgressIndicator(
                progress = progressAnimationValue,
                modifier = modifier
                    .size(16.dp)
                    .testTag(TAG_REC_UPDATE_INDICATOR)
            )
        }

        Text(
            text = statusText, modifier = modifier.testTag(TAG_REC_UPDATE_STATUS_TEXT)
        )
    }
}

/**
 * Generate the part of the status line that displays information about the number and age of the locations obtained
 * from the server based on the passed in [numberOfLocations] and [recentLocationTime]. If the latter is *null*,
 * assume that no locations are available and generate a corresponding message.
 */
@Composable
internal fun LocationStatus(numberOfLocations: Int, recentLocationTime: String?, modifier: Modifier = Modifier) {
    val statusText = recentLocationTime?.let { time ->
        stringResource(id = R.string.map_status, numberOfLocations, time)
    } ?: stringResource(id = R.string.map_status_empty)

    Text(
        text = statusText,
        modifier = modifier.testTag(TAG_REC_LOCATION_STATUS_TEXT)
    )
}

/**
 * Generate the header of a UI fragment that can be expanded or folded. If [expanded] is *true*, the fragment should be
 * displayed; otherwise, only the header is visible. Use [headerRes] for the text of the header and [tag] to generate
 * unique tags for the generated elements. Report changes on the [expanded] status via the [onChanged] function.
 */
@Composable
internal fun ExpandableHeader(
    headerRes: Int,
    tag: String,
    expanded: Boolean,
    onChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val (iconRes, contentRes) = if (expanded) android.R.drawable.arrow_down_float to R.string.exp_header_hide
    else android.R.drawable.arrow_up_float to R.string.exp_header_expand

    val headerText = stringResource(id = headerRes)
    val contentDesc = stringResource(contentRes, headerText)
    val onClick: () -> Unit = { onChanged(!expanded) }

    Row(modifier = modifier) {
        Icon(
            contentDescription = contentDesc,
            painter = painterResource(id = iconRes),
            modifier = modifier
                .clickable(onClick = onClick)
                .align(Alignment.CenterVertically)
                .testTag(
                    expandableHeaderIconTag(tag)
                )
        )
        Spacer(modifier = modifier.width(5.dp))
        Text(
            text = headerText, modifier = modifier
                .clickable(onClick = onClick)
                .testTag(expandableHeaderTextTag(tag))
        )
    }
}

// Note: This preview cannot be displayed in Android Studio.
@Preview(showBackground = true)
@Composable
fun ReceiverPreview() {
    val model = PreviewReceiverViewModel()

    ReceiverView(model = model)
}

@Preview(showBackground = true)
@Composable
fun ReceiverConfigPreview() {
    val config = ReceiverConfig(
        updateInterval = 150,
        fadeOutEnabled = true,
        fastFadeOut = true,
        strongFadeOut = false,
        centerNewPosition = true
    )

    ReceiverConfigView(config = config, update = {}, modifier = Modifier)
}

@Preview(showBackground = true)
@Composable
fun ActionPreview() {
    ReceiverActionView(onAction = {}, modifier = Modifier)
}
