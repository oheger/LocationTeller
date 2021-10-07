/*
 * Copyright 2019-2021 The Developers.
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
import android.app.AlertDialog
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.github.oheger.locationteller.R

/**
 * A helper class that wraps an action that requires permission to query the
 * current location.
 *
 * This UI helper class implements the workflow to check for the required
 * permission and - if necessary - ask the user whether it can be granted.
 * The class is configured with an action that gets directly executed if the
 * permission to query the location is already granted. Otherwise, the user
 * is prompted for this permission, and, depending on his or her reaction, the
 * action is executed or skipped. If required by the system, a dialog with
 * additional background information is shown.
 *
 * As the location permission is currently the only one required by this app,
 * this class only handles this one; it would, however, be possible to make it
 * generic to support other permissions as well.
 */
internal class LocationPermAction private constructor(
    /** The [Fragment] that owns this action. */
    val fragment: Fragment,

    /** The action to execute when the permission is available. */
    val action: () -> Unit,

    /** The action to execute when the permission was rejected by the user. */
    val rejectAction: () -> Unit
) {
    companion object {
        /**
         * Create a [LocationPermAction] for the given [fragment]. On calling
         * [execute], [action] is called if the permission is granted. If the
         * user does not grant the permission, [rejectAction] is invoked
         * instead.
         */
        fun create(fragment: Fragment, action: () -> Unit, rejectAction: () -> Unit = {}): LocationPermAction =
            LocationPermAction(fragment, action, rejectAction)
    }

    /** An object to launch the permission request dialog. */
    private val requestPermissionLauncher: ActivityResultLauncher<String>

    init {
        requestPermissionLauncher = fragment.registerForActivityResult(
            ActivityResultContracts.RequestPermission(),
            this::permissionRequestCallback
        )
    }

    /**
     * Execute this action. Check for the location permission and do the
     * necessary interaction with the user to request the permission to be
     * granted.
     */
    fun execute() {
        when {
            ContextCompat.checkSelfPermission(
                fragment.requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED ->
                action()
            fragment.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) ->
                showRationale()
            else ->
                launchPermissionDialog()
        }
    }

    /**
     * Return a builder to construct a dialog that displays a rationale why the
     * permission is needed by this app. Use the provided [context].
     */
    internal fun createDialogBuilder(context: Context) = AlertDialog.Builder(context)

    /**
     * Show a dialog with a rationale why the permission to query the location
     * is required for this app.
     */
    private fun showRationale() {
        createDialogBuilder(fragment.requireContext()).apply {
            setTitle(R.string.perm_location_title)
            setMessage(R.string.perm_location_rationale)

            setPositiveButton(R.string.perm_button_continue) { _, _ ->
                launchPermissionDialog()
            }
            setNegativeButton(R.string.perm_button_cancel) { _, _ ->
                rejectAction()
            }

            show()
        }
    }

    /**
     * Use the [ActivityResultLauncher] to launch the dialog to request the
     * managed permission.
     */
    private fun launchPermissionDialog() {
        requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    /**
     * Handle the result from the request permission dialog. Depending on the
     * [granted] flag, invoke the correct action.
     */
    private fun permissionRequestCallback(granted: Boolean) {
        if (granted) action()
        else rejectAction()
    }
}
