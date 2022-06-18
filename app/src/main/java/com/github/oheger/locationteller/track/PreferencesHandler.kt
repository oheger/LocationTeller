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
package com.github.oheger.locationteller.track

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.github.oheger.locationteller.server.ServerConfig
import java.util.Date

/**
 * A class managing access to the preferences for the location teller
 * application.
 *
 * This class defines constants for all the properties supported and offers
 * some helper functions for accessing specific settings.
 *
 * @param preferences the managed _SharedPreferences_ instance
 */
class PreferencesHandler(val preferences: SharedPreferences) {
    /**
     * Return the value of the [Date] property with the given [key] from the managed preferences object. The actual
     * type of the property is [Long]. If it is defined, it is converted to a [Date]. Otherwise, result is *null*.
     */
    fun getDate(key: String): Date? =
        if (key in preferences) {
            val time = preferences.getLong(key, 0)
            time.takeIf { it >= MIN_DATE_VALUE }?.let { Date(it) }
        } else null

    /**
     * Return the numeric value of the property with the given [key] from the managed preferences object. From the
     * settings screen, the properties are stored as strings. Therefore, a conversion has to be done. Sometimes the
     * config UI uses a different unit than the logic. This is handled by allowing a [factor] to be specified. A
     * [defaultValue] can be provided to deal with undefined properties. (Note that the [factor] is not applied to the
     * [defaultValue].
     */
    fun getNumeric(key: String, factor: Int = 1, defaultValue: Int = UNDEFINED_NUMBER): Int {
        val value = preferences.getString(key, UNDEFINED_NUMBER_STR)?.toInt() ?: UNDEFINED_NUMBER
        return if (value == UNDEFINED_NUMBER) defaultValue else value * factor
    }

    /**
     * Return the [Double] value of the property with the given [key] from the managed preference object. This is
     * analogous to [getNumeric], but for [Double] properties.
     */
    fun getDouble(key: String, factor: Double = 1.0, defaultValue: Double): Double {
        val value = preferences.getString(key, UNDEFINED_NUMBER_STR) ?: UNDEFINED_NUMBER_STR
        return if (value == UNDEFINED_NUMBER_STR) defaultValue else value.toDouble() * factor
    }

    /**
     * Creates a _ServerConfig_ object from the managed preferences. If
     * mandatory properties are missing, result is *null*.
     * @return the server configuration or *null*
     */
    fun createServerConfig(): ServerConfig? {
        val serverUri = preferences.getString(PROP_SERVER_URI, null)
        val basePath = preferences.getString(PROP_BASE_PATH, null)
        val user = preferences.getString(PROP_USER, null)
        val password = preferences.getString(PROP_PASSWORD, null)
        return if (serverUri == null || basePath == null || user == null || password == null) {
            return null
        } else ServerConfig(serverUri, basePath, user, password)
    }

    /**
     * Creates a _TrackConfig_ object from the managed preferences. For missing
     * properties in the underlying shared preferences default values are set.
     * @return the track configuration
     */
    fun createTrackConfig(): TrackConfig {
        val minTrackInterval = getNumeric(
            PROP_MIN_TRACK_INTERVAL, factor = MINUTE,
            defaultValue = DEFAULT_MIN_TRACK_INTERVAL
        )
        val maxTrackInterval = getNumeric(
            PROP_MAX_TRACK_INTERVAL, factor = MINUTE,
            defaultValue = DEFAULT_MAX_TRACK_INTERVAL
        )
        val intervalIncrementOnIdle = getNumeric(
            PROP_IDLE_INCREMENT, factor = MINUTE,
            defaultValue = DEFAULT_IDLE_INCREMENT
        )
        val locationValidity = getNumeric(
            PROP_LOCATION_VALIDITY, factor = MINUTE,
            defaultValue = DEFAULT_LOCATION_VALIDITY
        )
        val locationUpdateThreshold = getNumeric(
            PROP_LOCATION_UPDATE_THRESHOLD,
            defaultValue = DEFAULT_LOCATION_UPDATE_THRESHOLD
        )
        val retryOnErrorTime =
            getNumeric(PROP_RETRY_ON_ERROR_TIME, defaultValue = DEFAULT_RETRY_ON_ERROR_TIME)
        val gpsTimeout = getNumeric(PROP_GPS_TIMEOUT, defaultValue = DEFAULT_GPS_TIMEOUT)
        val offlineStorageSize = getNumeric(
            PROP_OFFLINE_STORAGE_SIZE,
            defaultValue = DEFAULT_OFFLINE_STORAGE_SIZE
        )
        val offlineStorageSyncTime = getNumeric(
            PROP_OFFLINE_STORAGE_SYNC_TIME,
            defaultValue = DEFAULT_OFFLINE_STORAGE_SYNC_TIME
        )
        val multiUploadChunkSize = getNumeric(
            PROP_MULTI_UPLOAD_CHUNK_SIZE,
            defaultValue = DEFAULT_MULTI_UPLOAD_CHUNK_SIZE
        )
        val maxSpeedIncrease = getDouble(PROP_MAX_SPEED_INCREASE, defaultValue = DEFAULT_MAX_SPEED_INCREASE)
        val walkingSpeed =
            getDouble(PROP_WALKING_SPEED, factor = METER_PER_SECOND, defaultValue = DEFAULT_WALKING_SPEED)
        return TrackConfig(
            minTrackInterval = minTrackInterval, maxTrackInterval = maxTrackInterval,
            intervalIncrementOnIdle = intervalIncrementOnIdle, locationValidity = locationValidity,
            locationUpdateThreshold = locationUpdateThreshold, retryOnErrorTime = retryOnErrorTime,
            gpsTimeout = gpsTimeout, autoResetStats = isAutoResetStats(), offlineStorageSize = offlineStorageSize,
            maxOfflineStorageSyncTime = offlineStorageSyncTime, multiUploadChunkSize = multiUploadChunkSize,
            maxSpeedIncrease = maxSpeedIncrease, walkingSpeed = walkingSpeed
        )
    }

    /**
     * Updates a _SharedPreferences_ object. This function obtains an editor
     * from the preferences, invokes the block on it and finally applies the
     * changes.
     * @param block the lambda to update the preferences
     */
    fun update(block: SharedPreferences.Editor.() -> Unit) {
        val editor = preferences.edit()
        editor.block()
        editor.apply()
    }

    /**
     * Checks whether tracking is currently active. This is determined by a
     * special property.
     * @return a flag whether tracking is active
     */
    fun isTrackingEnabled(): Boolean =
        preferences.getBoolean(PROP_TRACK_STATE, false)

    /**
     * Updates the tracking enabled state in the managed preferences. This
     * causes some related properties to be updated as well.
     * @param flag the new tracking state
     */
    fun setTrackingEnabled(flag: Boolean) {
        update {
            val currentTime = System.currentTimeMillis()
            putBoolean(PROP_TRACK_STATE, flag)
            if (flag) {
                putLong(PROP_TRACKING_START, currentTime)
                remove(PROP_TRACKING_END)
            } else {
                putLong(PROP_TRACKING_END, currentTime)
            }
        }
    }

    /**
     * Returns a flag whether the tracking statistics should be reset
     * automatically when starting a new track operation.
     * @return the auto reset statistics flag
     */
    fun isAutoResetStats(): Boolean = preferences.getBoolean(PROP_AUTO_RESET_STATS, false)

    /**
     * Returns an identifier that corresponds to the fading mode the user has
     * selected. (This is actually the menu identifier associated with this
     * mode.)
     * @return an identifier for the fading mode
     */
    fun getFadingMode(): Int = preferences.getInt(PROP_FADING_MODE, 0)

    /**
     * Updates the fading mode to the identifier specified.
     * @param mode the new fading mode
     */
    fun setFadingMode(mode: Int) {
        preferences.edit()
            .putInt(PROP_FADING_MODE, mode)
            .apply()
    }

    /**
     * Sets the preferences property for the last error to the given timestamp
     * and also updates the total error counter.
     * @param at the time when the error happened
     * @param count the total number of errors
     */
    fun recordError(at: Long, count: Int) {
        update {
            putLong(PROP_LAST_ERROR, at)
            putInt(PROP_ERROR_COUNT, count)
        }
    }

    /**
     * Updates the preferences properties for the last successful update with
     * regards to the passed in information.
     * @param at the time when the update happened
     * @param count the number of updates
     * @param distance the distance to the last position
     * @param totalDistance the accumulated distance
     */
    fun recordUpdate(at: Long, count: Int, distance: Int, totalDistance: Long) {
        update {
            putLong(PROP_LAST_UPDATE, at)
            putInt(PROP_UPDATE_COUNT, count)
            putInt(PROP_LAST_DISTANCE, distance)
            putLong(PROP_TOTAL_DISTANCE, totalDistance)
        }
    }

    /**
     * Sets the preferences property for the last check time to the given
     * timestamp and also updates the number of checks.
     * @param at the time when the last check has happened
     * @param count the number of checks
     */
    fun recordCheck(at: Long, count: Int) {
        update {
            putLong(PROP_LAST_CHECK, at)
            putInt(PROP_CHECK_COUNT, count)
        }
    }

    /**
     * Returns a _Date_ with the last error that happened. Result is *null* if
     * the last update was successful.
     * @return the date of the last error
     */
    fun lastError(): Date? = getDate(PROP_LAST_ERROR)

    /**
     * Returns the number of errors that have been encountered since the
     * statistics have been reset.
     * @return the number of errors
     */
    fun errorCount(): Int = preferences.getInt(PROP_ERROR_COUNT, 0)

    /**
     * Returns the number of checks that have been performed since the
     * statistics have been reset.
     * @return the number of checks
     */
    fun checkCount(): Int = preferences.getInt(PROP_CHECK_COUNT, 0)

    /**
     * Returns the number of updates that have been performed since the
     * statistics have been reset.
     * @return the number of updates
     */
    fun updateCount(): Int = preferences.getInt(PROP_UPDATE_COUNT, 0)

    /**
     * Returns a _Date_ when the last updated took place. Result is *null* if
     * no update has been recorded so far.
     * @return the date of the last update
     */
    fun lastUpdate(): Date? = getDate(PROP_LAST_UPDATE)

    /**
     * Returns a _Date_ when the last check for a location update took place.
     * This timestamp is recorded any time the service is invoked, even if
     * there was no change in the location. Result is *null* if no check time
     * has been recorded so far.
     * @return the date of the last check
     */
    fun lastCheck(): Date? = getDate(PROP_LAST_CHECK)

    /**
     * Returns the distance of the last location update in meters.
     * @return the distance of the last location update
     */
    fun lastDistance(): Int = preferences.getInt(PROP_LAST_DISTANCE, 0)

    /**
     * Returns the total distance of all checks that have been recorded (in
     * meters).
     * @return the total distance of all location updates
     */
    fun totalDistance(): Long = preferences.getLong(PROP_TOTAL_DISTANCE, 0)

    /**
     * Returns the recorded start date of the current tracking process.
     * Result is *null* if tracking has never been started.
     * @return the start date of the current tracking process
     */
    fun trackingStartDate(): Date? = getDate(PROP_TRACKING_START)

    /**
     * Returns the recorded end date of the current tracking process. Result
     * is *null* if tracking is currently active.
     * @return the time tracking was stopped or *null*
     */
    fun trackingEndDate(): Date? = getDate(PROP_TRACKING_END)

    /**
     * Registers the given change listener at the managed preferences.
     * @param listener the listener to be registered
     */
    fun registerListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        preferences.registerOnSharedPreferenceChangeListener(listener)
    }

    /**
     * Removes the given change listener from the managed preferences.
     * @param listener the listener to be removed
     */
    fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        preferences.unregisterOnSharedPreferenceChangeListener(listener)
    }

    /**
     * Sets default values for the shared preferences corresponding to options
     * of the track configuration if they are undefined. With this function it
     * can be ensured that the shared preferences are initialized with
     * meaningful value.
     */
    fun initTrackConfigDefaults() {
        val undefinedProps = CONFIG_DEFAULTS.filterNot { preferences.contains(it.key) }
        if (undefinedProps.isNotEmpty()) {
            val editor = preferences.edit()
            undefinedProps.forEach { pair ->
                editor.putString(pair.key, pair.value.toString())
            }
            editor.apply()
        }
    }

    /**
     * Resets all the stored values that are related to tracking statistics.
     */
    fun resetStatistics() {
        update {
            RESET_PROPS.forEach { remove(it) }
        }
    }

    companion object {
        /** Shared preferences property for the track server URI.*/
        const val PROP_SERVER_URI = "trackServerUri"

        /** Shared preferences property for the base path on the server.*/
        const val PROP_BASE_PATH = "trackRelativePath"

        /** Shared preferences property for the user name.*/
        const val PROP_USER = "userName"

        /** Shared preferences property for the password.*/
        const val PROP_PASSWORD = "password"

        /** Shared preferences property for the minimum track interval.*/
        const val PROP_MIN_TRACK_INTERVAL = "minTrackInterval"

        /** Shared preferences property for the maximum track interval.*/
        const val PROP_MAX_TRACK_INTERVAL = "maxTrackInterval"

        /** Shared preferences property for the increment interval.*/
        const val PROP_IDLE_INCREMENT = "intervalIncrementOnIdle"

        /** Shared preferences property for the increment interval.*/
        const val PROP_LOCATION_VALIDITY = "locationValidity"

        /** Shared preferences property for the location update threshold.*/
        const val PROP_LOCATION_UPDATE_THRESHOLD = "locationUpdateThreshold"

        /** Shared preferences property for the retry on error time. */
        const val PROP_RETRY_ON_ERROR_TIME = "retryOnErrorTime"

        /** Shared preferences property for the GPS timeout. */
        const val PROP_GPS_TIMEOUT = "gpsTimeout"

        /** Shared preferences property for the offline storage size. */
        const val PROP_OFFLINE_STORAGE_SIZE = "offlineStorageSize"

        /**
         * Shared preferences property for the sync time of the offline
         * storage.
         */
        const val PROP_OFFLINE_STORAGE_SYNC_TIME = "offlineStorageSyncTime"

        /**
         * Shared preferences property for the chunk size of a multi upload
         * operation to sync the offline storage.
         */
        const val PROP_MULTI_UPLOAD_CHUNK_SIZE = "multiUploadChunkSize"

        /**
         * Shared preferences property for the maximum speed increase, used to
         * detect suspicious GPS locations.
         */
        const val PROP_MAX_SPEED_INCREASE = "maxSpeedIncrease"

        /** Shared preferences property for the normal walking speed. */
        const val PROP_WALKING_SPEED = "walkingSpeed"

        /** Shared preferences property for the tracking state.*/
        const val PROP_TRACK_STATE = "trackEnabled"

        /** Shared preferences property for the latest update of location data. */
        const val PROP_LAST_UPDATE = "lastUpdate"

        /** Shared preferences property for the distance of the last update. */
        const val PROP_LAST_DISTANCE = "lastDistance"

        /** Shared preferences property for the latest error that occurred.*/
        const val PROP_LAST_ERROR = "lastError"

        /** Shared preferences property for the last check for an update. */
        const val PROP_LAST_CHECK = "lastCheck"

        /** Shared preferences property for the last tracking start time. */
        const val PROP_TRACKING_START = "trackingStart"

        /** Shared preferences property for the last time tracking was stopped. */
        const val PROP_TRACKING_END = "trackingEnd"

        /** Shared preferences property for the number of errors encountered.*/
        const val PROP_ERROR_COUNT = "errorCount"

        /** Shared preferences property for the number of checks. */
        const val PROP_CHECK_COUNT = "checkCount"

        /** Shared preferences property for the number of updates. */
        const val PROP_UPDATE_COUNT = "updateCount"

        /** Shared preferences property for the accumulated distance (in meters). */
        const val PROP_TOTAL_DISTANCE = "totalDistance"

        /** Shared preferences property to trigger the auto-reset of stats. */
        const val PROP_AUTO_RESET_STATS = "autoResetStats"

        /** Shared preferences property to store the fading mode. */
        const val PROP_FADING_MODE = "fadingMode"

        /** A default value for the minimum track interval (in seconds). */
        const val DEFAULT_MIN_TRACK_INTERVAL = 180

        /** A default value for the maximum track interval (in seconds). */
        const val DEFAULT_MAX_TRACK_INTERVAL = 900

        /** A default value for the idle increment interval (in seconds). */
        const val DEFAULT_IDLE_INCREMENT = 120

        /** A default value for the location validity time (in seconds).*/
        const val DEFAULT_LOCATION_VALIDITY = 43200 // 12 hours

        /** A default value for the retry on error time (in seconds). */
        const val DEFAULT_RETRY_ON_ERROR_TIME = 30

        /** A default value for the GPS timeout (in seconds). */
        const val DEFAULT_GPS_TIMEOUT = 45

        /** A default value for the location update threshold property. */
        const val DEFAULT_LOCATION_UPDATE_THRESHOLD = 10

        /** A default value for the size of the offline storage. */
        const val DEFAULT_OFFLINE_STORAGE_SIZE = 32

        /** A default value for the offline storage sync time (in sec). */
        const val DEFAULT_OFFLINE_STORAGE_SYNC_TIME = 30

        /** A default value for the multi-upload chunk size. */
        const val DEFAULT_MULTI_UPLOAD_CHUNK_SIZE = 4

        /** A default value for the maximum speed increase. */
        const val DEFAULT_MAX_SPEED_INCREASE = 2.0

        /** A default value for the average walking speed. */
        const val DEFAULT_WALKING_SPEED = 4.0 / 3.6 // 4 km/h in m/s

        /**
         * Constant for the minimum value accepted for a date (in millis).
         * This value should prevent that an undefined date property (set to 0)
         * is reported as a date in the 1970s.
         */
        const val MIN_DATE_VALUE = 100000L

        /** Constant for an undefined numeric property.*/
        private const val UNDEFINED_NUMBER = -1

        /** String value of an undefined numeric property.*/
        private const val UNDEFINED_NUMBER_STR = UNDEFINED_NUMBER.toString()

        /** A set with all properties related to configuration.*/
        private val CONFIG_PROPS = setOf(
            PROP_SERVER_URI, PROP_BASE_PATH, PROP_USER, PROP_PASSWORD,
            PROP_MIN_TRACK_INTERVAL, PROP_MAX_TRACK_INTERVAL, PROP_IDLE_INCREMENT, PROP_LOCATION_VALIDITY,
            PROP_LOCATION_UPDATE_THRESHOLD, PROP_RETRY_ON_ERROR_TIME, PROP_GPS_TIMEOUT,
            PROP_OFFLINE_STORAGE_SIZE, PROP_OFFLINE_STORAGE_SYNC_TIME, PROP_MULTI_UPLOAD_CHUNK_SIZE,
            PROP_MAX_SPEED_INCREASE, PROP_WALKING_SPEED
        )

        /**
         * A map with configuration properties and their default values. This
         * is used to initialize shared preferences.
         */
        private val CONFIG_DEFAULTS = mapOf(
            PROP_MIN_TRACK_INTERVAL to (DEFAULT_MIN_TRACK_INTERVAL / 60),
            PROP_MAX_TRACK_INTERVAL to (DEFAULT_MAX_TRACK_INTERVAL / 60),
            PROP_IDLE_INCREMENT to (DEFAULT_IDLE_INCREMENT / 60),
            PROP_LOCATION_VALIDITY to (DEFAULT_LOCATION_VALIDITY / 60),
            PROP_LOCATION_UPDATE_THRESHOLD to DEFAULT_LOCATION_UPDATE_THRESHOLD,
            PROP_RETRY_ON_ERROR_TIME to DEFAULT_RETRY_ON_ERROR_TIME,
            PROP_GPS_TIMEOUT to DEFAULT_GPS_TIMEOUT
        )

        /**
         * A set with the names of all the properties that need to be cleared
         * when statistics are reset.
         */
        private val RESET_PROPS = setOf(
            PROP_TOTAL_DISTANCE,
            PROP_ERROR_COUNT,
            PROP_LAST_CHECK,
            PROP_LAST_DISTANCE,
            PROP_LAST_UPDATE,
            PROP_LAST_ERROR,
            PROP_CHECK_COUNT,
            PROP_UPDATE_COUNT
        )

        /** Factor to convert minutes to seconds. */
        private const val MINUTE = 60

        /** Factor to convert Km/h to m/s. */
        private const val METER_PER_SECOND = 1.0 / 3.6

        /**
         * Creates a _PreferencesHandler_ object based on the given context.
         * @param context the current context
         * @return the _PreferencesHandler_
         */
        fun create(context: Context): PreferencesHandler {
            val pref = PreferenceManager.getDefaultSharedPreferences(context)
            return PreferencesHandler(pref)
        }

        /**
         * Checks whether the given property is related to the configuration
         * of the application. (Other properties contain persistent application
         * state.)
         * @param prop the property in question
         * @return *true* for a configuration property; *false* otherwise
         */
        fun isConfigProperty(prop: String): Boolean = CONFIG_PROPS.contains(prop)

        /**
         * Registers a preferences change listener at the default preferences.
         * @param context the current context
         * @param listener the listener
         */
        fun registerListener(context: Context, listener: SharedPreferences.OnSharedPreferenceChangeListener) {
            create(context).registerListener(listener)
        }

        /**
         * Removes the given change listener from the default preferences.
         * @param context the current context
         * @param listener the listener
         */
        fun unregisterListener(context: Context, listener: SharedPreferences.OnSharedPreferenceChangeListener) {
            create(context).unregisterListener(listener)
        }
    }
}
