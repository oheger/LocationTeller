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

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Log

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

import com.github.oheger.locationteller.R
import com.github.oheger.locationteller.track.LocationTellerService
import com.github.oheger.locationteller.ui.theme.LocationTellerTheme

import com.google.android.gms.maps.MapsInitializer
import com.google.android.gms.maps.OnMapsSdkInitializedCallback

/**
 * The main activity of this application.
 *
 * This class does some initialization and installs the main composable screen.
 */
class MainActivity : ComponentActivity(), OnMapsSdkInitializedCallback {
    companion object {
        private const val LOG_TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        MapsInitializer.initialize(applicationContext, MapsInitializer.Renderer.LATEST, this)

        setContent {
            LocationTellerTheme {
                LocationTellerMainScreen()
            }
        }

        createTrackNotificationChannel()
    }

    override fun onMapsSdkInitialized(renderer: MapsInitializer.Renderer) {
        when (renderer) {
            MapsInitializer.Renderer.LATEST ->
                Log.i(LOG_TAG, "The latest version of the map renderer is used.")
            MapsInitializer.Renderer.LEGACY ->
                Log.i(LOG_TAG, "The legacy version of the map renderer is used.")
        }
    }

    /**
     * Create the notification channel that is mandatory for the location teller service in newer Android versions.
     */
    private fun createTrackNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.track_channel_name)
            val desc = getString(R.string.track_channel_desc)
            val channel = NotificationChannel(
                LocationTellerService.trackChannelId, name,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = desc
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            Log.i(LOG_TAG, "Created notification channel.")
        }
    }
}
