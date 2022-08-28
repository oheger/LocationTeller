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

data class ReceiverConfig(
    /** The interval in which data from the server is fetched and the map view is updated. */
    val updateInterval: Int,

    /**
     * Flag whether old markers on the map should fade out. If this flag is *true*, the other flags controlling the
     * fade out are taken into account; otherwise, they are ignored.
     */
    val fadeOutEnabled: Boolean,

    /** Flag whether old markers should fade out fast. */
    val fastFadeOut: Boolean,

    /** Flag whether old markers should fade out strongly. */
    val strongFadeOut: Boolean,

    /** Flag whether a newly received position should be centered automatically on the map view. */
    val centerNewPosition: Boolean
) {
    companion object {
        /** Name of the property defining the update interval. */
        const val PROP_UPDATE_INTERVAL = "recUpdateInterval"

        /** Name of the property whether fade out is enabled. */
        const val PROP_FADE_OUT_ENABLED = "recFadeOut"

        /** Name of the property whether fast fade out is enabled. */
        const val PROP_FADE_OUT_FAST = "recFadeFast"

        /** Name of the property whether string fade out is enabled. */
        const val PROP_FADE_OUT_STRONG = "recFadeStrong"

        /** Name of the property that controls the auto-center mode. */
        const val PROP_AUTO_CENTER = "recAutoCenter"

        /**
         * A configuration instance with default settings. These settings are used if no explicit settings are found
         * in the app's shared preferences.
         */
        val DEFAULT = ReceiverConfig(
            updateInterval = 180,
            fadeOutEnabled = false,
            fastFadeOut = false,
            strongFadeOut = false,
            centerNewPosition = false
        )

        /**
         * Return an instance of [ReceiverConfig] that is initialized from the shared preferences managed by the
         * given [PreferencesHandler].
         */
        fun fromPreferences(preferencesHandler: PreferencesHandler): ReceiverConfig =
            ReceiverConfig(
                updateInterval = preferencesHandler.getNumeric(
                    PROP_UPDATE_INTERVAL,
                    defaultValue = DEFAULT.updateInterval
                ),
                fadeOutEnabled = preferencesHandler.preferences.getBoolean(
                    PROP_FADE_OUT_ENABLED,
                    DEFAULT.fadeOutEnabled
                ),
                fastFadeOut = preferencesHandler.preferences.getBoolean(PROP_FADE_OUT_FAST, DEFAULT.fastFadeOut),
                strongFadeOut = preferencesHandler.preferences.getBoolean(PROP_FADE_OUT_STRONG, DEFAULT.strongFadeOut),
                centerNewPosition = preferencesHandler.preferences.getBoolean(
                    PROP_AUTO_CENTER,
                    DEFAULT.centerNewPosition
                )
            )
    }

    /**
     * Write the settings contained in this configuration into the shared preferences managed by the given
     * [preferencesHandler].
     */
    fun save(preferencesHandler: PreferencesHandler) {
        preferencesHandler.update {
            putInt(PROP_UPDATE_INTERVAL, updateInterval)
            putBoolean(PROP_FADE_OUT_ENABLED, fadeOutEnabled)
            putBoolean(PROP_FADE_OUT_FAST, fastFadeOut)
            putBoolean(PROP_FADE_OUT_STRONG, strongFadeOut)
            putBoolean(PROP_AUTO_CENTER, centerNewPosition)
        }
    }
}
