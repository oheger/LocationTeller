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

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.preference.PreferenceManager
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import com.github.oheger.locationteller.R
import com.github.oheger.locationteller.track.LocationTellerService
import kotlinx.android.synthetic.main.activity_main.*

/**
 * The main activity of this application.
 *
 * This activity does not have a visual representation on its own as the whole
 * UI is provided by different fragments. This class implements some common
 * management tasks.
 */
class MainActivity : AppCompatActivity(), SharedPreferences.OnSharedPreferenceChangeListener {
    /** Constant for the preferences key for the tracking state.*/
    private val keyTrackState = "trackEnabled"

    private val logTag = "MainActivity"

    private lateinit var appBarConfig: AppBarConfiguration

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setSupportActionBar(toolbar)

        val navCtrl = findNavController(R.id.nav_host_fragment)
        appBarConfig = AppBarConfiguration(navCtrl.graph, drawerLayout)
        toolbar.setupWithNavController(navCtrl, appBarConfig)
        nav_view.setupWithNavController(navCtrl)
    }

    override fun onPostResume() {
        super.onPostResume()
        PreferenceManager.getDefaultSharedPreferences(this)
            .registerOnSharedPreferenceChangeListener(this)
    }

    override fun onPause() {
        PreferenceManager.getDefaultSharedPreferences(this)
            .unregisterOnSharedPreferenceChangeListener(this)
        super.onPause()
    }

    /**
     * Handles updates of the shared preferences. If the update affects a
     * configuration key, tracking is stopped. It then has to be enabled
     * explicitly by the user again. If the track state is affected, the
     * service is invoked.
     * @param sharedPreferences the preferences that have been changed
     * @param key the key affected
     */
    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
        Log.d(logTag, "Change of shared properties. Affected key is $key.")
        if (keyTrackState == key) {
            Intent(this, LocationTellerService::class.java).also { startService(it) }
        } else {
            if (sharedPreferences.getBoolean(keyTrackState, false)) {
                sharedPreferences.edit().putBoolean(keyTrackState, false).apply()
            }
        }
    }
}
