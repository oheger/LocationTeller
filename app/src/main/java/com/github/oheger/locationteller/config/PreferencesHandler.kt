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

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

import androidx.preference.PreferenceManager

import java.util.Date

/**
 * A class managing access to the preferences for the location teller
 * application.
 *
 * This class is a thin wrapper that offers an abstraction over the direct access to shared preferences.
 *
 * Clients obtain an instance via the [getInstance] function of the companion object. Here a single, shared instance is
 * managed. This yields the optimum performance, but also ensures that change listeners registered at the instance
 * are notified for changes made application-wide.
 */
class PreferencesHandler internal constructor(
    /** The managed [SharedPreferences] instance. */
    private val preferences: SharedPreferences
) {
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
     * Return the [Int] value of the property with the given [key] from the managed preferences object or
     * [defaultValue] if this property is undefined.
     */
    fun getInt(key: String, defaultValue: Int = UNDEFINED_NUMBER): Int = preferences.getInt(key, defaultValue)

    /**
     * Return the [Double] value of the property with the given [key] from the managed preference object or
     * [defaultValue] if this property is undefined.
     */
    fun getDouble(key: String, defaultValue: Double): Double =
        preferences.getFloat(key, defaultValue.toFloat()).toDouble()

    /**
     * Return the [String] value of the property with the given [key] from the managed preferences object or
     * [defaultValue] if this property is undefined.
     */
    fun getString(key: String, defaultValue: String = ""): String =
        preferences.getString(key, null) ?: defaultValue

    /**
     * Return the [Long] value of the property with the given [key] from the managed preferences object or
     * [defaultValue] if this property is undefined.
     */
    fun getLong(key: String, defaultValue: Long): Long = preferences.getLong(key, defaultValue)

    /**
     * Return the [Boolean] value of the property with the given [key] from the managed preferences object or
     * [defaultValue] if this property is undefined.
     */
    fun getBoolean(key: String, defaultValue: Boolean = false): Boolean = preferences.getBoolean(key, defaultValue)

    /**
     * Return a flag whether the managed preferences contain the given [key].
     */
    operator fun contains(key: String): Boolean = key in preferences

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

    companion object {
        /** Shared preferences property for the track server URI.*/
        const val PROP_SERVER_URI = "trackServerUri"

        /** Shared preferences property for the base path on the server.*/
        const val PROP_BASE_PATH = "trackRelativePath"

        /** Shared preferences property for the user name.*/
        const val PROP_USER = "userName"

        /** Shared preferences property for the password.*/
        const val PROP_PASSWORD = "password"

        /** Shared preferences property for the tracking state.*/
        const val PROP_TRACK_STATE = "trackEnabled"

        /** Shared preferences property for the last tracking start time. */
        const val PROP_TRACKING_START = "trackingStart"

        /** Shared preferences property for the last time tracking was stopped. */
        const val PROP_TRACKING_END = "trackingEnd"

        /** Shared preferences property to store the fading mode. */
        const val PROP_FADING_MODE = "fadingMode"

        /**
         * Constant for the minimum value accepted for a date (in millis).
         * This value should prevent that an undefined date property (set to 0)
         * is reported as a date in the 1970s.
         */
        const val MIN_DATE_VALUE = 100000L

        /** Constant for an undefined numeric property.*/
        private const val UNDEFINED_NUMBER = -1

        /** A set with all properties related to configuration (not managed by other classes). */
        private val CONFIG_PROPS = setOf(PROP_SERVER_URI, PROP_BASE_PATH, PROP_USER, PROP_PASSWORD)

        /** Holds the shared instance of this class. */
        private var instance: PreferencesHandler? = null

        /**
         * Return the shared [PreferencesHandler] instance. Create it on initial access using [context]. Note that
         * this function must be called from the main thread.
         */
        fun getInstance(context: Context): PreferencesHandler =
            instance ?: PreferencesHandler(PreferenceManager.getDefaultSharedPreferences(context)).also {
                instance = it
                Log.i("PreferencesHandler", "Created shared instance of PreferencesHandler.")
            }

        /**
         * Checks whether the given property is related to the configuration
         * of the application. (Other properties contain persistent application
         * state.)
         * @param prop the property in question
         * @return *true* for a configuration property; *false* otherwise
         */
        fun isConfigProperty(prop: String): Boolean = CONFIG_PROPS.contains(prop)
    }
}
