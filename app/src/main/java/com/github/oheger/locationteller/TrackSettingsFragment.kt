/*
 * Copyright 2019 The Developers.
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
package com.github.oheger.locationteller

import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceFragmentCompat

/**
 * A fragment for displaying settings related to the tracking functionality.
 *
 * Here it can be configured how often the current location is uploaded to the
 * server and how long it remains there.
 */
class TrackSettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.track_preferences, rootKey)

        val numberBindListener = { editText: EditText ->
            editText.inputType = InputType.TYPE_CLASS_NUMBER
        }

        fun makeNumericSetting(key: String) {
            val pref: EditTextPreference? = findPreference(key)
            pref?.setOnBindEditTextListener(numberBindListener)
        }

        listOf("minTrackInterval", "maxTrackInterval", "intervalIncrementOnIdle", "locationValidity")
            .forEach { makeNumericSetting(it) }
    }
}
