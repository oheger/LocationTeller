/*
 * Copyright 2019-2020 The Developers.
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

import android.os.Bundle
import android.text.InputType
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceFragmentCompat
import com.github.oheger.locationteller.R

/**
 * A fragment for displaying settings related to the tracking functionality.
 *
 * Here it can be configured how often the current location is uploaded to the
 * server and how long it remains there.
 */
class TrackSettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.track_preferences, rootKey)

        fun inputTypeBindListener(inputType: Int): EditTextPreference.OnBindEditTextListener =
            EditTextPreference.OnBindEditTextListener { it.inputType = inputType }

        fun setBindListener(key: String, listener: EditTextPreference.OnBindEditTextListener) {
            findPreference<EditTextPreference>(key)?.setOnBindEditTextListener(listener)
        }

        val intBindListener = inputTypeBindListener(InputType.TYPE_CLASS_NUMBER)
        val floatBindListener =
            inputTypeBindListener(InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)

        listOf(
            "minTrackInterval", "maxTrackInterval", "intervalIncrementOnIdle", "locationValidity",
            "locationUpdateThreshold", "retryOnErrorTime", "gpsTimeout", "offlineStorageSize",
            "offlineStorageSyncTime", "multiUploadChunkSize"
        )
            .forEach { setBindListener(it, intBindListener) }

        listOf("maxSpeedIncrease", "walkingSpeed")
            .forEach { setBindListener(it, floatBindListener) }
    }
}
