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

/**
 * A class managing access to the central configuration objects used within this application.
 *
 * The class reads the configuration objects from shared preferences when they are accessed for the first time and then
 * caches them. Updates of configurations (typically triggered from configuration UIs) are handled by this class as
 * well. It is possible to register a change listener that is notified when a specific configuration is updated; so
 * parts of the application can react on configuration changes.
 *
 * Implementation note: This class is not thread-safe. All interactions - including obtaining an instance via the
 * factory function - have to take place in the main thread.
 */
class ConfigManager private constructor(
) {
    companion object {
        /** Stores the shared instance of this class. */
        private var instance: ConfigManager? = null

        /**
         * Return the single shared instance of [ConfigManager].
         */
        fun getInstance(): ConfigManager =
            instance ?: ConfigManager().also { instance = it }
    }

    /** The backing field for the tracking-related configuration. */
    private var currentTrackConfig: TrackConfig? = null

    /** The backing field for the server-related configuration. */
    private var currentServerConfig: TrackServerConfig? = null

    /** The backing field for the receiver-related configuration. */
    private var currentReceiverConfig: ReceiverConfig? = null

    /** Stores the listeners for changes on the managed [TrackConfig]. */
    private val trackConfigListeners = mutableSetOf<(TrackConfig) -> Unit>()

    /** Stores the listeners for changes on the managed [TrackServerConfig]. */
    private val serverConfigListeners = mutableSetOf<(TrackServerConfig) -> Unit>()

    /** Stores the listeners for changes on the managed [ReceiverConfig]. */
    private val receiverConfigListeners = mutableSetOf<(ReceiverConfig) -> Unit>()

    /**
     * Return the [TrackConfig] managed by this instance. Use [context] to load it on first access.
     */
    fun trackConfig(context: Context): TrackConfig =
        currentTrackConfig
            ?: loadConfig(context, loader = { TrackConfig.fromPreferences(it) }, cache = { currentTrackConfig = it })

    /**
     * Update the tracking-related configuration to the given [config] using [context] to access the shared
     * preferences.
     */
    fun updateTrackConfig(context: Context, config: TrackConfig) {
        currentTrackConfig = config
        config.save(PreferencesHandler.getInstance(context))
        trackConfigListeners.notify(config)
    }

    /**
     * Return the [TrackServerConfig] managed by this instance. Use [context] to load it on first access.
     */
    fun serverConfig(context: Context): TrackServerConfig =
        currentServerConfig
            ?: loadConfig(
                context,
                loader = { TrackServerConfig.fromPreferences(it) },
                cache = { currentServerConfig = it }
            )

    /**
     * Update the server-related configuration to the given [config] using [context] to access the shared preferences.
     */
    fun updateServerConfig(context: Context, config: TrackServerConfig) {
        currentServerConfig = config
        config.save(PreferencesHandler.getInstance(context))
        serverConfigListeners.notify(config)
    }

    /**
     * Return the [ReceiverConfig] managed by this instance. Use [context] to load it on first access.
     */
    fun receiverConfig(context: Context): ReceiverConfig =
        currentReceiverConfig
            ?: loadConfig(
                context,
                loader = { ReceiverConfig.fromPreferences(it) },
                cache = { currentReceiverConfig = it }
            )

    /**
     * Update the receiver-related configuration to the given [config] using [context] to access the shared
     * preferences.
     */
    fun updateReceiverConfig(context: Context, config: ReceiverConfig) {
        currentReceiverConfig = config
        config.save(PreferencesHandler.getInstance(context))
        receiverConfigListeners.notify(config)
    }

    /**
     * Add the given [listener] for changes on the managed [TrackConfig] to this object. It will be notified whenever
     * this configuration is updated.
     */
    fun addTrackConfigChangeListener(listener: (TrackConfig) -> Unit) {
        trackConfigListeners += listener
    }

    /**
     * Remove the given [listener] for changes on the managed [TrackConfig] from this object.
     */
    fun removeTrackConfigChangeListener(listener: (TrackConfig) -> Unit) {
        trackConfigListeners -= listener
    }

    /**
     * Add the given [listener] for changes on the managed [TrackServerConfig] to this object. It will be notified
     * whenever this configuration is updated.
     */
    fun addServerConfigChangeListener(listener: (TrackServerConfig) -> Unit) {
        serverConfigListeners += listener
    }

    /**
     * Remove the given [listener] for changes on the managed [TrackServerConfig] from this object.
     */
    fun removeServerConfigChangeListener(listener: (TrackServerConfig) -> Unit) {
        serverConfigListeners -= listener
    }

    /**
     * Add the given [listener] for changes on the managed [ReceiverConfig] to this object. It will be notified
     * whenever this configuration is updated.
     */
    fun addReceiverConfigChangeListener(listener: (ReceiverConfig) -> Unit) {
        receiverConfigListeners += listener
    }

    /**
     * Remove the given [listener] for changes on the managed [ReceiverConfig] from this object.
     */
    fun removeReceiverConfigChangeListener(listener: (ReceiverConfig) -> Unit) {
        receiverConfigListeners -= listener
    }

    /**
     * Helper function to load a configuration via a [PreferencesHandler]. The handler is obtained from [context].
     * Use [loader] to load the configuration object from preferences and [cache] to store it in an internal property.
     */
    private fun <T> loadConfig(context: Context, loader: (PreferencesHandler) -> T, cache: (T) -> Unit): T {
        val preferencesHandler = PreferencesHandler.getInstance(context)
        return loader(preferencesHandler).also(cache)
    }

    /**
     * Notify all registered event listeners of the given type about a change on the specified [config].
     */
    private fun <T> Set<(T) -> Unit>.notify(config: T) {
        forEach { it(config) }
    }
}
