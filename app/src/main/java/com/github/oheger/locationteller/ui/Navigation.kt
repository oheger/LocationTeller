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

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

import com.github.oheger.locationteller.R

internal const val TAG_NAV_SENDER = "nav_sender"
internal const val TAG_NAV_RECEIVER = "nav_receiver"
internal const val TAG_NAV_TRACK_SETTINGS = "nav_settings_track"
internal const val TAG_NAV_SERVER_SETTINGS = "nav_settings_server"

internal const val TAG_NAV_TOP_TITLE = "nav_top_title"
internal const val TAG_NAV_TOP_MENU = "nav_top_menu"

internal const val NAV_ROUTE_SENDER = "sender"

/**
 * Generate the drawer with the menu items to navigate to the different screens. Call the [onRouteSelected]
 * function when the user clicks on a menu item representing a screen.
 */
@Composable
fun Drawer(
    onRouteSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(start = 24.dp, top = 60.dp)
    ) {
        DrawerItem(
            iconRes = R.drawable.ic_item_sender,
            textRes = R.string.trackView,
            tag = TAG_NAV_SENDER,
            modifier = modifier,
            onClick = routeClicked(NAV_ROUTE_SENDER, onRouteSelected)
        )
        DrawerItem(
            iconRes = R.drawable.ic_item_receiver,
            textRes = R.string.receiverView,
            tag = TAG_NAV_RECEIVER,
            modifier = modifier,
            onClick = {}
        )
        DrawerItem(
            iconRes = R.drawable.ic_item_settings,
            textRes = R.string.settings_header,
            tag = "",
            modifier = modifier,
            onClick = {}
        )
        DrawerItem(
            iconRes = R.drawable.ic_item_settings_track,
            textRes = R.string.trackSettingsView,
            style = MaterialTheme.typography.h6,
            tag = TAG_NAV_TRACK_SETTINGS,
            modifier = modifier.padding(start = 12.dp),
            onClick = {}
        )
        DrawerItem(
            iconRes = R.drawable.ic_item_settings_server,
            textRes = R.string.serverSettingsView,
            style = MaterialTheme.typography.h6,
            tag = TAG_NAV_SERVER_SETTINGS,
            modifier = modifier.padding(start = 12.dp),
            onClick = {}
        )
    }
}

/**
 * Generate the top bar for all application screens. Display the given [title]. Call the given [onMenuClicked]
 * function when the menu icon is clicked.
 */
@Composable
fun TopBar(title: String, onMenuClicked: () -> Unit) {
    TopAppBar(
        title = {
            Text(text = title, modifier = Modifier.testTag(TAG_NAV_TOP_TITLE))
        },
        navigationIcon = {
            IconButton(onClick = onMenuClicked, modifier = Modifier.testTag(TAG_NAV_TOP_MENU)) {
                Icon(Icons.Filled.Menu, contentDescription = "menu")
            }
        }
    )
}

/**
 * Generate an item to be displayed in the drawer with the given [icon][iconRes], [text][textRes], [style], and [tag].
 * Invoke the given [onClick] function when the user clicks on this item.
 */
@Composable
private fun DrawerItem(
    iconRes: Int,
    @StringRes textRes: Int,
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier,
    style: TextStyle = MaterialTheme.typography.h5
) {
    val itemText = stringResource(id = textRes)
    Row(modifier = modifier.padding(top = 12.dp)) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = itemText,
            modifier = modifier.align(Alignment.CenterVertically)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = itemText, style = style, modifier = Modifier
                .testTag(tag)
                .clickable(onClick = onClick)
        )
    }
}

/**
 * Create a parameter-less callback function for clicks that invokes the given [routeFunc] with the specified
 * [route] identifier.
 */
private fun routeClicked(route: String, routeFunc: (String) -> Unit): () -> Unit = { routeFunc(route) }

@Preview
@Composable
fun DrawerPreview() {
    Drawer(onRouteSelected = {})
}
