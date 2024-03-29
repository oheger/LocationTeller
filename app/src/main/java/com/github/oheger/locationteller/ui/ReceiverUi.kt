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

import android.Manifest
import android.content.res.Configuration

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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

import com.github.oheger.locationteller.R
import com.github.oheger.locationteller.config.ReceiverConfig
import com.github.oheger.locationteller.duration.DurationModel
import com.github.oheger.locationteller.ui.state.ReceiverAction
import com.github.oheger.locationteller.ui.state.ReceiverViewModel
import com.github.oheger.locationteller.ui.state.ReceiverViewModelImpl
import com.github.oheger.locationteller.ui.state.TrackStatsFormatter
import com.github.oheger.locationteller.ui.theme.LocationTellerTheme

import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.rememberPermissionState
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.MarkerOptions
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
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
internal const val TAG_REC_PERM_RATIONALE = "rec_perm_rationale"

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
 * Generate the whole receiver UI. This is the entry point into this UI. Use the given [mapStyleOptions] to style the
 * map.
 */
@Composable
fun ReceiverUi(
    openDrawer: () -> Unit,
    mapStyleOptions: MapStyleOptions?,
    modifier: Modifier = Modifier,
    model: ReceiverViewModelImpl = viewModel()
) {
    val locationPermissionState = rememberPermissionState(permission = Manifest.permission.ACCESS_FINE_LOCATION)

    ReceiverView(
        model = model,
        locationPermissionState = locationPermissionState,
        openDrawer = openDrawer,
        mapStyleOptions = mapStyleOptions,
        modifier = modifier
    )
}

/**
 * Generate the view for the receiver part of the application based on the given [model] and the
 * [locationPermissionState]. Call the [openDrawer] function if the menu icon is clicked. Use the given
 * [mapStyleOptions] to style the map.
 */
@Composable
fun ReceiverView(
    model: ReceiverViewModel,
    locationPermissionState: PermissionState,
    openDrawer: () -> Unit,
    mapStyleOptions: MapStyleOptions?,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        TopBar(title = stringResource(id = R.string.receiverView), onMenuClicked = openDrawer)
        Box(modifier = modifier.weight(1.0f)) {
            MapView(
                markers = model.markers,
                cameraState = model.cameraPositionState,
                mapStyleOptions = mapStyleOptions,
                modifier = modifier.testTag(TAG_REC_MAP_VIEW)
            )
        }
        Box(modifier = modifier) {
            ControlView(
                updateInProgress = model.isUpdating(),
                countDown = model.secondsToNextUpdateString,
                numberOfLocations = model.locationFileState.files.size,
                recentLocationTime = model.recentLocationTime(),
                ownLocation = model.ownLocation,
                ownLocationRetrieving = model.locationRetrieving,
                config = model.receiverConfig,
                updateConfig = model::updateReceiverConfig,
                onAction = model::onAction,
                locationPermissionState = locationPermissionState,
                modifier
            )
        }
    }
}

/**
 * Render the map view as part of the receiver UI. Place the given [markers] on the map. Set the position and zoom
 * level of the map as defined by the given [cameraState]. Use the given [mapStyleOptions].
 */
@Composable
fun MapView(
    markers: List<MarkerOptions>,
    cameraState: CameraPositionState,
    mapStyleOptions: MapStyleOptions?,
    modifier: Modifier = Modifier
) {
    val properties by remember {
        mutableStateOf(MapProperties(mapStyleOptions = mapStyleOptions))
    }

    GoogleMap(
        modifier = modifier.fillMaxSize(),
        cameraPositionState = cameraState,
        properties = properties
    ) {
        markers.forEach { options ->
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
 * receiver configuration. Pass [ownLocation], [ownLocationRetrieving] and [locationPermissionState] to the function
 * that renders the action buttons.
 */
@Composable
internal fun ControlView(
    updateInProgress: Boolean,
    countDown: String,
    numberOfLocations: Int,
    recentLocationTime: String?,
    ownLocation: MarkerOptions?,
    ownLocationRetrieving: Boolean,
    config: ReceiverConfig,
    updateConfig: (ReceiverConfig) -> Unit,
    onAction: (ReceiverAction) -> Unit,
    locationPermissionState: PermissionState,
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
            ReceiverActionView(
                onAction = onAction,
                numberOfLocations = numberOfLocations,
                ownLocation = ownLocation,
                ownLocationRetrieving = ownLocationRetrieving,
                locationPermissionState = locationPermissionState,
                modifier = modifier
            )
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
 * triggered by the user via the [onAction] function. Use [numberOfLocations] to disable some actions that depend on
 * the availability of positions, [ownLocation] to determine whether the own position is available, and
 * [ownLocationRetrieving] to determine whether the own location is currently retrieved. The actions related to the
 * own location require the permission to obtain this location. Use [locationPermissionState] to adapt this view to
 * the current state of this permission.
 */
@Composable
internal fun ReceiverActionView(
    onAction: (ReceiverAction) -> Unit,
    numberOfLocations: Int,
    ownLocation: MarkerOptions?,
    ownLocationRetrieving: Boolean,
    locationPermissionState: PermissionState,
    modifier: Modifier
) {
    val permissionFlags = locationPermissionState.toFlags()

    Column(modifier = modifier) {
        Row(modifier = modifier.fillMaxWidth()) {
            Spacer(modifier = modifier.weight(1f))
            ActionButton(
                action = ReceiverAction.UPDATE,
                iconId = R.drawable.ic_action_refresh,
                contentDescId = R.string.item_update_map,
                onAction = onAction,
                modifier = modifier
            )
            Spacer(modifier = modifier.weight(1f))
            ActionButton(
                action = ReceiverAction.CENTER_RECENT_POSITION,
                iconId = R.drawable.ic_action_center_last,
                contentDescId = R.string.item_center_to_recent,
                onAction = onAction,
                modifier = modifier,
                enabled = numberOfLocations > 0
            )
            Spacer(modifier = modifier.weight(1f))
            ActionButton(
                action = ReceiverAction.ZOOM_TRACKED_AREA,
                iconId = R.drawable.ic_action_zoom_tracked,
                contentDescId = R.string.item_zoom_tracked_area,
                onAction = onAction,
                modifier = modifier,
                enabled = numberOfLocations > 0
            )
            Spacer(modifier = modifier.weight(1f))

            if (permissionFlags.hasPermission) {
                ActionButton(
                    action = ReceiverAction.UPDATE_OWN_POSITION,
                    iconId = R.drawable.ic_action_own_position,
                    contentDescId = R.string.item_update_my_location,
                    onAction = onAction,
                    modifier = modifier,
                    enabled = !ownLocationRetrieving
                )
                Spacer(modifier = modifier.weight(1f))
                ActionButton(
                    action = ReceiverAction.CENTER_OWN_POSITION,
                    iconId = R.drawable.ic_action_center_my_position,
                    contentDescId = R.string.item_center_to_my_location,
                    onAction = onAction,
                    modifier = modifier,
                    enabled = ownLocation != null
                )
            } else {
                RequestPermissionButton(permissionState = locationPermissionState, modifier = modifier)
            }
            Spacer(modifier = modifier.weight(1f))
        }

        if (permissionFlags.shouldShowRationale) {
            Row(modifier = modifier) {
                Text(
                    text = stringResource(id = R.string.perm_location_rationale_rec),
                    modifier = modifier.testTag(TAG_REC_PERM_RATIONALE)
                )
            }
        }
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
            text = headerText,
            fontSize = 20.sp,
            modifier = modifier
                .clickable(onClick = onClick)
                .testTag(expandableHeaderTextTag(tag))
        )
    }
}

/**
 * Generate a button that represents the given [action] with the given [iconId],
 * [content description][contentDescId], and [enabled] state. When the button is clicked, invoke the given
 * [onAction] function.
 */
@Composable
private fun ActionButton(
    action: ReceiverAction,
    iconId: Int,
    contentDescId: Int,
    onAction: (ReceiverAction) -> Unit,
    modifier: Modifier,
    enabled: Boolean = true
) {
    Button(
        onClick = { onAction(action) },
        enabled = enabled,
        modifier = modifier.testTag(actionTag(action))
    ) {
        Icon(
            painter = painterResource(id = iconId),
            contentDescription = stringResource(id = contentDescId)
        )
    }
}

// Note: This preview cannot be displayed in Android Studio.
@Preview(showBackground = true)
@Composable
fun ReceiverPreview() {
    val model = PreviewReceiverViewModel()
    val permissionStateProvider = PermissionStateProvider()

    ReceiverView(
        model = model,
        locationPermissionState = permissionStateProvider.values.first(),
        openDrawer = {},
        mapStyleOptions = null
    )
}

@Preview(name = "Light mode")
@Preview(
    name = "Dark mode",
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    showBackground = true
)
@Composable
fun ReceiverConfigPreview() {
    val config = ReceiverConfig(
        updateInterval = 150,
        fadeOutEnabled = true,
        fastFadeOut = true,
        strongFadeOut = false,
        centerNewPosition = true
    )

    LocationTellerTheme {
        ReceiverConfigView(config = config, update = {}, modifier = Modifier)
    }
}

@Preview(name = "Light mode")
@Preview(
    name = "Dark mode",
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    showBackground = true
)
@Composable
fun ActionPreview(
    @PreviewParameter(PermissionStateProvider::class)
    permissionState: PermissionState
) {
    LocationTellerTheme {
        ReceiverActionView(
            onAction = {},
            numberOfLocations = 5,
            modifier = Modifier,
            ownLocation = null,
            ownLocationRetrieving = false,
            locationPermissionState = permissionState
        )
    }
}
