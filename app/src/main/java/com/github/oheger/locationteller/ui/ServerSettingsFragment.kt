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

import androidx.compose.runtime.Composable

/**
 * A fragment for displaying the settings related to the tracking server.
 *
 * With the settings defined here the server is configured on which location
 * information is stored.
 */
class ServerSettingsFragment : ComposeFragment() {
    override fun getContent(): @Composable () -> Unit = {
        ServerConfigUi(openDrawer = {})
    }
}
