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

import androidx.compose.material.DrawerValue
import androidx.compose.material.MaterialTheme
import androidx.compose.material.ModalDrawer
import androidx.compose.material.Surface
import androidx.compose.material.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

import com.google.android.gms.maps.model.MapStyleOptions

import kotlinx.coroutines.launch

/**
 * The main entry point into this application. Use the given [mapStyleOptions] to style the map view.
 */
@Composable
fun LocationTellerMainScreen(mapStyleOptions: MapStyleOptions?) {
    val navController = rememberNavController()
    var trackingEnabled by rememberSaveable { mutableStateOf(false) }

    Surface(color = MaterialTheme.colors.background) {
        val drawerState = rememberDrawerState(DrawerValue.Closed)
        val scope = rememberCoroutineScope()
        val openDrawer = {
            scope.launch {
                drawerState.open()
            }
        }
        ModalDrawer(
            drawerState = drawerState,
            gesturesEnabled = drawerState.isOpen,
            drawerContent = {
                Drawer(
                    trackingActive = trackingEnabled,
                    onRouteSelected = { route ->
                        scope.launch {
                            drawerState.close()
                        }
                        navController.navigate(route) {
                            popUpTo(navController.graph.startDestinationId)
                            launchSingleTop = true
                        }
                    }
                )
            }
        ) {
            NavHost(
                navController = navController,
                startDestination = NAV_ROUTE_SENDER
            ) {
                composable(NAV_ROUTE_SENDER) {
                    TrackUi(openDrawer = { openDrawer() }, updateTrackState = { trackingEnabled = it })
                }
                composable(NAV_ROUTE_RECEIVER) {
                    ReceiverUi(openDrawer = { openDrawer() }, mapStyleOptions = mapStyleOptions)
                }
                composable(NAV_ROUTER_TRACK_SETTINGS) {
                    TrackConfigUi(openDrawer = { openDrawer() })
                }
                composable(NAV_ROUTER_SERVER_SETTINGS) {
                    ServerConfigUi(openDrawer = { openDrawer() })
                }
            }
        }
    }
}
