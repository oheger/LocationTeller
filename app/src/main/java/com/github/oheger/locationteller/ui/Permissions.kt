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

import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewParameterProvider

import com.github.oheger.locationteller.R

import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.PermissionStatus

/** Test tag used to identify the button for requesting missing permissions. */
internal const val TAG_PERM_REQUEST_BUTTON = "tag_perm_request_button"

/**
 * A data class allowing direct access to the properties of a [PermissionState] that are relevant to decide, which
 * elements of a UI should be rendered.
 */
data class PermissionFlags(
    /** Flag whether the permission is granted or not. */
    val hasPermission: Boolean,

    /**
     * Flag whether a rationale why a permission is needed should be displayed. This flag is relevant only if
     * [hasPermission] is *false*.
     */
    val shouldShowRationale: Boolean = false
)

/**
 * Extract a [PermissionFlags] object from this [PermissionState]. Based on the flags, it is easier to determine,
 * which parts of the UI need to be rendered.
 */
fun PermissionState.toFlags(): PermissionFlags =
    when (val currentStatus = status) {
        PermissionStatus.Granted -> PermissionFlags(hasPermission = true)
        is PermissionStatus.Denied ->
            PermissionFlags(hasPermission = false, shouldShowRationale = currentStatus.shouldShowRationale)
    }

/**
 * Render the button to request the permissions represented by [permissionState].
 */
@Composable
fun RequestPermissionButton(
    permissionState: PermissionState,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = { permissionState.launchPermissionRequest() },
        modifier = modifier
            .testTag(TAG_PERM_REQUEST_BUTTON)

    ) {
        Text(text = stringResource(id = R.string.perm_location_request))
    }
}

/**
 * A [PreviewParameterProvider] implementation that provides all possible permission states for the location
 * permission. So the effect of the permission state on the UI can be seen.
 */
class PermissionStateProvider : PreviewParameterProvider<PermissionState> {
    override val values: Sequence<PermissionState> = sequenceOf(
        PermissionStatus.Granted,
        PermissionStatus.Denied(shouldShowRationale = false),
        PermissionStatus.Denied(shouldShowRationale = true)
    ).map(this::createLocationPermissionState)

    /**
     * Create a [PermissionState] stub object that reports the given [status].
     */
    private fun createLocationPermissionState(status: PermissionStatus): PermissionState =
        object : PermissionState {
            override val permission = Manifest.permission.ACCESS_FINE_LOCATION

            override val status = status

            override fun launchPermissionRequest() {}
        }
}
