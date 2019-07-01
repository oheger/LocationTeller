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
package com.github.oheger.locationteller.ui

import android.content.SharedPreferences
import android.os.Bundle
import android.preference.PreferenceManager
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioGroup
import com.github.oheger.locationteller.R
import kotlinx.android.synthetic.main.fragment_track.*

/**
 * A fragment that allows enabling or disabling the tracking functionality.
 */
class TrackFragment : androidx.fragment.app.Fragment() {
    private val logTag = "TrackFragment"

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        Log.i(logTag, "Creating TrackFragment")
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_track, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        switchTrackEnabled.isChecked = sharedPreferences().getBoolean("trackEnabled", false)
        switchTrackEnabled.setOnCheckedChangeListener { _, checked ->
            Log.i(logTag, "Set track enabled state to $checked.")
            with(sharedPreferences().edit()) {
                putBoolean("trackEnabled", checked)
                apply()
            }
        }
    }

    /**
     * Returns the shared preferences object.
     * @return the shared preferences
     */
    private fun sharedPreferences(): SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
}
