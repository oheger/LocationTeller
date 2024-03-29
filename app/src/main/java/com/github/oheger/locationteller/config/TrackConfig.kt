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
package com.github.oheger.locationteller.config

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

        /** Factor to convert Km/h to m/s. */
        private const val METER_PER_SECOND = 1.0 / 3.6

        /**
         * An instance of [TrackConfig] with default values for all properties.
         */
        val DEFAULT = TrackConfig(
            minTrackInterval = 180,
            maxTrackInterval = 900,
            intervalIncrementOnIdle = 120,
            locationValidity = 43_200, // 12 hours
            locationUpdateThreshold = 10,
            retryOnErrorTime = 30,
            gpsTimeout = 45,
            offlineStorageSize = 32,
            maxOfflineStorageSyncTime = 30,
            multiUploadChunkSize = 4,
            autoResetStats = false,
            maxSpeedIncrease = 2.0,
            walkingSpeed = 4.0 * METER_PER_SECOND
        )

        /** A set with all properties related to configuration.*/
        private val CONFIG_PROPS = setOf(
            PROP_AUTO_RESET_STATS,
            PROP_MIN_TRACK_INTERVAL,
            PROP_MAX_TRACK_INTERVAL,
            PROP_IDLE_INCREMENT,
            PROP_LOCATION_VALIDITY,
            PROP_LOCATION_UPDATE_THRESHOLD,
            PROP_RETRY_ON_ERROR_TIME,
            PROP_GPS_TIMEOUT,
            PROP_OFFLINE_STORAGE_SIZE,
            PROP_OFFLINE_STORAGE_SYNC_TIME,
            PROP_MULTI_UPLOAD_CHUNK_SIZE,
            PROP_MAX_SPEED_INCREASE,
            PROP_WALKING_SPEED
        )

        /**
         * Return a new instance of [TrackConfig] that is initialized from the preferences managed by the given
         * [preferencesHandler].
         */
        fun fromPreferences(preferencesHandler: PreferencesHandler): TrackConfig {
            val minTrackInterval = preferencesHandler.getInt(PROP_MIN_TRACK_INTERVAL, DEFAULT.minTrackInterval)
            val maxTrackInterval = preferencesHandler.getInt(PROP_MAX_TRACK_INTERVAL, DEFAULT.maxTrackInterval)
            val intervalIncrementOnIdle = preferencesHandler.getInt(
                PROP_IDLE_INCREMENT,
                DEFAULT.intervalIncrementOnIdle
            )
            val locationValidity = preferencesHandler.getInt(PROP_LOCATION_VALIDITY, DEFAULT.locationValidity)
            val locationUpdateThreshold = preferencesHandler.getInt(
                PROP_LOCATION_UPDATE_THRESHOLD,
                DEFAULT.locationUpdateThreshold
            )
            val retryOnErrorTime = preferencesHandler.getInt(PROP_RETRY_ON_ERROR_TIME, DEFAULT.retryOnErrorTime)
            val gpsTimeout = preferencesHandler.getInt(PROP_GPS_TIMEOUT, DEFAULT.gpsTimeout)
            val offlineStorageSize =
                preferencesHandler.getInt(PROP_OFFLINE_STORAGE_SIZE, DEFAULT.offlineStorageSize)
            val offlineStorageSyncTime = preferencesHandler.getInt(
                PROP_OFFLINE_STORAGE_SYNC_TIME,
                DEFAULT.maxOfflineStorageSyncTime
            )
            val multiUploadChunkSize = preferencesHandler.getInt(
                PROP_MULTI_UPLOAD_CHUNK_SIZE,
                DEFAULT.multiUploadChunkSize
            )
            val maxSpeedIncrease = preferencesHandler.getDouble(PROP_MAX_SPEED_INCREASE, DEFAULT.maxSpeedIncrease)
            val walkingSpeed = preferencesHandler.getDouble(PROP_WALKING_SPEED, DEFAULT.walkingSpeed)
            val autoResetStats = preferencesHandler.getBoolean(PROP_AUTO_RESET_STATS, false)

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
         * Check whether [prop] is a property that belongs to this configuration class. This can be used for instance
         * by a listener on shared preferences to react on property change events.
         */
        fun isProperty(prop: String): Boolean = prop in CONFIG_PROPS
    }

    /** The walking speed in Km/h. */
    val walkingSpeedKmH: Double
        get() = walkingSpeed / METER_PER_SECOND

    /**
     * Return a new instance of [TrackConfig] with [walkingSpeed] set to [speed] in Km/h.
     */
    fun updateWalkingSpeedKmH(speed: Double): TrackConfig = copy(walkingSpeed = speed * METER_PER_SECOND)

    /**
     * Write the values contained in this configuration into the preferences managed by [handler].
     */
    fun save(handler: PreferencesHandler) {
        handler.update {
            putInt(PROP_MIN_TRACK_INTERVAL, minTrackInterval)
            putInt(PROP_MAX_TRACK_INTERVAL, maxTrackInterval)
            putInt(PROP_IDLE_INCREMENT, intervalIncrementOnIdle)
            putInt(PROP_LOCATION_VALIDITY, locationValidity)
            putInt(PROP_LOCATION_UPDATE_THRESHOLD, locationUpdateThreshold)
            putInt(PROP_RETRY_ON_ERROR_TIME, retryOnErrorTime)
            putInt(PROP_GPS_TIMEOUT, gpsTimeout)
            putInt(PROP_OFFLINE_STORAGE_SIZE, offlineStorageSize)
            putInt(PROP_OFFLINE_STORAGE_SYNC_TIME, maxOfflineStorageSyncTime)
            putInt(PROP_MULTI_UPLOAD_CHUNK_SIZE, multiUploadChunkSize)
            putFloat(PROP_MAX_SPEED_INCREASE, maxSpeedIncrease.toFloat())
            putFloat(PROP_WALKING_SPEED, walkingSpeed.toFloat())
            putBoolean(PROP_AUTO_RESET_STATS, autoResetStats)
        }
    }
}
