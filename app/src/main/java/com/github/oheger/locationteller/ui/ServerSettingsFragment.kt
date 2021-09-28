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

import android.os.Bundle
import android.text.InputType
import android.text.TextUtils
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.github.oheger.locationteller.R

/**
 * A fragment for displaying the settings related to the tracking server.
 *
 * With the settings defined here the server is configured on which location
 * information is stored.
 */
class ServerSettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.server_preferences, rootKey)

        val passwordPref: EditTextPreference? = findPreference("password")
        passwordPref?.summaryProvider = Preference.SummaryProvider<EditTextPreference> { preference ->
            if (TextUtils.isEmpty(preference.text)) ""
            else "****"
        }
        passwordPref?.setOnBindEditTextListener { editText ->
            editText.inputType = (InputType.TYPE_CLASS_TEXT + InputType.TYPE_TEXT_VARIATION_PASSWORD)
        }
    }
}
