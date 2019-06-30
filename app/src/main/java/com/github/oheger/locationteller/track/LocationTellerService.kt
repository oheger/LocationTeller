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
package com.github.oheger.locationteller.track

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.preference.PreferenceManager
import android.util.Log
import com.github.oheger.locationteller.server.ServerConfig
import com.github.oheger.locationteller.server.TrackService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.channels.SendChannel

/**
 * A factory class for creating the actor to update location data.
 *
 * This factory is used internally by [LocationTellerService]. By providing a
 * mock implementation, a mock actor can be injected for testing purposes.
 */
class UpdaterActorFactory {
    /**
     * Creates the actor for updating location data. Result may be *null* if
     * mandatory configuration options are not set.
     * @param context the context
     * @param crScope the coroutine scope
     * @return the new actor
     */
    @ObsoleteCoroutinesApi
    fun createActor(context: Context, crScope: CoroutineScope): SendChannel<LocationUpdate>? {
        val pref = PreferenceManager.getDefaultSharedPreferences(context)
        val serverConfig = createServerConfig(pref)
        val trackConfig = createTrackConfig(pref)
        return if (serverConfig != null && trackConfig != null) {
            val trackService = TrackService.create(serverConfig)
            locationUpdaterActor(trackService, trackConfig, crScope)
        } else null
    }

    companion object {
        /** Shared preferences property for the track server URI.*/
        private const val propServerUri = "trackServerUri"

        /** Shared preferences property for the base path on the server.*/
        private const val propBasePath = "trackRelativePath"

        /** Shared preferences property for the user name.*/
        private const val propUser = "userName"

        /** Shared preferences property for the password.*/
        private const val propPassword = "password"

        /** Shared preferences property for the minimum track interval.*/
        private const val propMinTrackInterval = "minTrackInterval"

        /** Shared preferences property for the maximum track interval.*/
        private const val propMaxTrackInterval = "maxTrackInterval"

        /** Shared preferences property for the increment interval.*/
        private const val propIdleIncrement = "intervalIncrementOnIdle"

        /** Shared preferences property for the increment interval.*/
        private const val propLocationValidity = "locationValidity"

        /**
         * Creates a _ServerConfig_ object from the given preferences. If
         * mandatory properties are missing, result is *null*.
         * @param pref the preferences
         * @return the server configuration or *null*
         */
        private fun createServerConfig(pref: SharedPreferences): ServerConfig? {
            val serverUri = pref.getString(propServerUri, null)
            val basePath = pref.getString(propBasePath, null)
            val user = pref.getString(propUser, null)
            val password = pref.getString(propPassword, null)
            return if (serverUri == null || basePath == null || user == null || password == null) {
                return null
            } else ServerConfig(serverUri, basePath, user, password)
        }

        /**
         * Creates a _TrackConfig_ object from the given preferences. If
         * mandatory properties are missing, result is *null*.
         * @param pref the preferences
         * @return the track configuration or *null*
         */
        private fun createTrackConfig(pref: SharedPreferences): TrackConfig? {
            val minTrackInterval = pref.getInt(propMinTrackInterval, -1)
            val maxTrackInterval = pref.getInt(propMaxTrackInterval, -1)
            val intervalIncrementOnIdle = pref.getInt(propIdleIncrement, -1)
            val locationValidity = pref.getInt(propLocationValidity, -1)
            return if (minTrackInterval < 0 || maxTrackInterval < 0 || intervalIncrementOnIdle < 0 ||
                locationValidity < 0
            ) {
                return null
            } else TrackConfig(
                minTrackInterval, maxTrackInterval, intervalIncrementOnIdle,
                locationValidity
            )
        }
    }
}

class LocationTellerService : Service() {
    private val tag = "LocationTellerService"

    private lateinit var pendingIntent: PendingIntent

    override fun onCreate() {
        super.onCreate()
        Log.i(tag, "onCreate()")
        pendingIntent = PendingIntent.getService(
            this, 0,
            Intent(this, LocationTellerService::class.java), 0
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(tag, "onStartCommand($intent, $flags, $startId)")
        if (startId < 10) {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    SystemClock.elapsedRealtime() + 30 * 1000, pendingIntent
                )
                Log.i(tag, "Scheduled allow while idle :-)")
            } else {
                alarmManager.set(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    SystemClock.elapsedRealtime() + 30 * 1000, pendingIntent
                )
            }
        } else {
            stopSelf()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        Log.i(tag, "onDestroy")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
