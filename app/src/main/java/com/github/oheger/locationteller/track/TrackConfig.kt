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

/**
 * A data class representing the configuration for location tracking.
 */
data class TrackConfig(
    /** The minimum track interval (in sec). */
    val minTrackInterval: Int,

    /** The maximum track interval (in sec). */
    val maxTrackInterval: Int,

    /** The value (in sec) to increase the track interval if the location has not changed. */
    val intervalIncrementOnIdle: Int,

    /** The time (in sec) how long a location should stay on the server. */
    val locationValidity: Int,

    /**
     * The threshold for the distance (in meters) between the current location and the last one before an update is
     * reported. This helps dealing with GPS results that are not very accurate.
     */
    val locationUpdateThreshold: Int,

    /** A time when the next location update should be attempted after an error occurred (in sec). */
    val retryOnErrorTime: Int,

    /** Timeout for querying the current GPS location (in sec). */
    val gpsTimeout: Int,

    /**
     * The maximum size of the offline storage. Here the data that could not be uploaded is stored until an internet
     * connection is available again.
     */
    val offlineStorageSize: Int,

    /** The maximum time frame (in sec) to sync the offline storage. */
    val maxOfflineStorageSyncTime: Int,

    /** The number of data items from the offline storage to upload in a single operation. */
    val multiUploadChunkSize: Int,

    /** A flag whether tracking statistics should be reset automatically when tracking is started. */
    val autoResetStats: Boolean,

    /**
     * A factor defining the maximum increase of speed to be considered normal; this is used to detect wrong GPS
     * locations: if the speed between two GPS locations increases more than this factory, the current locations is
     * treated as inaccurate.
     */
    val maxSpeedIncrease: Double,

    /**
     * The average walking speed (in m/s); as long as the speed is in this area, no illegal speed increment is
     * reported.
     */
    val walkingSpeed: Double
) {
    companion object {
        /** Shared preferences property for the minimum track interval. */
        const val PROP_MIN_TRACK_INTERVAL = "minTrackInterval"

        /** Shared preferences property for the maximum track interval. */
        const val PROP_MAX_TRACK_INTERVAL = "maxTrackInterval"

        /** Shared preferences property for the increment interval. */
        const val PROP_IDLE_INCREMENT = "intervalIncrementOnIdle"

        /** Shared preferences property for the increment interval. */
        const val PROP_LOCATION_VALIDITY = "locationValidity"

        /** Shared preferences property for the location update threshold. */
        const val PROP_LOCATION_UPDATE_THRESHOLD = "locationUpdateThreshold"

        /** Shared preferences property for the retry on error time. */
        const val PROP_RETRY_ON_ERROR_TIME = "retryOnErrorTime"

        /** Shared preferences property for the GPS timeout. */
        const val PROP_GPS_TIMEOUT = "gpsTimeout"

        /** Shared preferences property for the offline storage size. */
        const val PROP_OFFLINE_STORAGE_SIZE = "offlineStorageSize"

        /** Shared preferences property for the sync time of the offline storage. */
        const val PROP_OFFLINE_STORAGE_SYNC_TIME = "offlineStorageSyncTime"

        /**
         * Shared preferences property for the chunk size of a multi upload operation to sync the offline storage.
         */
        const val PROP_MULTI_UPLOAD_CHUNK_SIZE = "multiUploadChunkSize"

        /**
         * Shared preferences property for the maximum speed increase, used to detect suspicious GPS locations.
         */
        const val PROP_MAX_SPEED_INCREASE = "maxSpeedIncrease"

        /** Shared preferences property for the normal walking speed. */
        const val PROP_WALKING_SPEED = "walkingSpeed"

        /** Shared preferences property to trigger the auto-reset of stats. */
        const val PROP_AUTO_RESET_STATS = "autoResetStats"

        /** A default value for the minimum track interval (in seconds). */
        const val DEFAULT_MIN_TRACK_INTERVAL = 180

        /** A default value for the maximum track interval (in seconds). */
        const val DEFAULT_MAX_TRACK_INTERVAL = 900

        /** A default value for the idle increment interval (in seconds). */
        const val DEFAULT_IDLE_INCREMENT = 120

        /** A default value for the location validity time (in seconds). */
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

        /** Factor to convert minutes to seconds. */
        private const val MINUTE = 60

        /** Factor to convert Km/h to m/s. */
        private const val METER_PER_SECOND = 1.0 / 3.6

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
         * Return a new instance of [TrackConfig] that is initialized from the preferences managed by the given
         * [preferencesHandler].
         */
        fun fromPreferences(preferencesHandler: PreferencesHandler): TrackConfig {
            val minTrackInterval = preferencesHandler.getNumeric(
                PROP_MIN_TRACK_INTERVAL,
                factor = MINUTE,
                defaultValue = DEFAULT_MIN_TRACK_INTERVAL
            )
            val maxTrackInterval = preferencesHandler.getNumeric(
                PROP_MAX_TRACK_INTERVAL,
                factor = MINUTE,
                defaultValue = DEFAULT_MAX_TRACK_INTERVAL
            )
            val intervalIncrementOnIdle = preferencesHandler.getNumeric(
                PROP_IDLE_INCREMENT,
                factor = MINUTE,
                defaultValue = DEFAULT_IDLE_INCREMENT
            )
            val locationValidity = preferencesHandler.getNumeric(
                PROP_LOCATION_VALIDITY,
                factor = MINUTE,
                defaultValue = DEFAULT_LOCATION_VALIDITY
            )
            val locationUpdateThreshold = preferencesHandler.getNumeric(
                PROP_LOCATION_UPDATE_THRESHOLD,
                defaultValue = DEFAULT_LOCATION_UPDATE_THRESHOLD
            )
            val retryOnErrorTime = preferencesHandler.getNumeric(
                PROP_RETRY_ON_ERROR_TIME,
                defaultValue = DEFAULT_RETRY_ON_ERROR_TIME
            )
            val gpsTimeout = preferencesHandler.getNumeric(
                PROP_GPS_TIMEOUT,
                defaultValue = DEFAULT_GPS_TIMEOUT
            )
            val offlineStorageSize = preferencesHandler.getNumeric(
                PROP_OFFLINE_STORAGE_SIZE,
                defaultValue = DEFAULT_OFFLINE_STORAGE_SIZE
            )
            val offlineStorageSyncTime = preferencesHandler.getNumeric(
                PROP_OFFLINE_STORAGE_SYNC_TIME,
                defaultValue = DEFAULT_OFFLINE_STORAGE_SYNC_TIME
            )
            val multiUploadChunkSize = preferencesHandler.getNumeric(
                PROP_MULTI_UPLOAD_CHUNK_SIZE,
                defaultValue = DEFAULT_MULTI_UPLOAD_CHUNK_SIZE
            )
            val maxSpeedIncrease = preferencesHandler.getDouble(
                PROP_MAX_SPEED_INCREASE,
                defaultValue = DEFAULT_MAX_SPEED_INCREASE
            )
            val walkingSpeed = preferencesHandler.getDouble(
                PROP_WALKING_SPEED,
                factor = METER_PER_SECOND,
                defaultValue = DEFAULT_WALKING_SPEED
            )
            val autoResetStats = preferencesHandler.preferences.getBoolean(PROP_AUTO_RESET_STATS, false)

            return TrackConfig(
                minTrackInterval = minTrackInterval,
                maxTrackInterval = maxTrackInterval,
                intervalIncrementOnIdle = intervalIncrementOnIdle,
                locationValidity = locationValidity,
                locationUpdateThreshold = locationUpdateThreshold,
                retryOnErrorTime = retryOnErrorTime,
                gpsTimeout = gpsTimeout,
                autoResetStats = autoResetStats,
                offlineStorageSize = offlineStorageSize,
                maxOfflineStorageSyncTime = offlineStorageSyncTime,
                multiUploadChunkSize = multiUploadChunkSize,
                maxSpeedIncrease = maxSpeedIncrease,
                walkingSpeed = walkingSpeed
            )
        }

        /**
         * Initialize the preferences managed by [handler] with default values for the tracking configuration, as
         * long as they are not yet defined. While this initialization is unnecessary for reading a [TrackConfig] from
         * preferences (here defaults are applied automatically for undefined properties), it is useful for the
         * configuration screen (which is directly backed by preferences).
         */
        fun initDefaults(handler: PreferencesHandler) {
            CONFIG_DEFAULTS.filterNot { handler.preferences.contains(it.key) }
                .takeUnless { it.isEmpty() }?.let { undefinedProps ->
                    handler.update {
                        undefinedProps.forEach { pair ->
                            putString(pair.key, pair.value.toString())
                        }
                    }
                }
        }
    }
}
