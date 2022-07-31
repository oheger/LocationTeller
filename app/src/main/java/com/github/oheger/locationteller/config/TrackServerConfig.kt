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
 * A data class that holds the configuration of the server which stores tracking information.
 *
 * This data is evaluated by the app components when location data needs to be downloaded or published.
 *
 * _Note_: Currently, the properties in this class correspond to the _ServerConfig_ class from the _server-access_
 * module, but this may change in the future.
 */
data class TrackServerConfig(
    /** The URI of the server. */
    val serverUri: String,

    /**
     * The relative base path under which location information is stored on the server; this path is prepended to
     * relative URIs used for server interactions.
     */
    val basePath: String,

    /** The username for authentication against the server. */
    val user: String,

    /** The password for authentication against the server. */
    val password: String
) {
    companion object {
        /** Shared preferences property for the track server URI.*/
        const val PROP_SERVER_URI = "trackServerUri"

        /** Shared preferences property for the base path on the server.*/
        const val PROP_BASE_PATH = "trackRelativePath"

        /** Shared preferences property for the user name.*/
        const val PROP_USER = "userName"

        /** Shared preferences property for the password.*/
        const val PROP_PASSWORD = "password"

        /**
         * Return an instance of [TrackServerConfig] that is initialized from the preferences managed by the given
         * [preferencesHandler].
         */
        fun fromPreferences(preferencesHandler: PreferencesHandler): TrackServerConfig =
            TrackServerConfig(
                serverUri = preferencesHandler.stringProperty(PROP_SERVER_URI),
                basePath = preferencesHandler.stringProperty(PROP_BASE_PATH),
                user = preferencesHandler.stringProperty(PROP_USER),
                password = preferencesHandler.stringProperty(PROP_PASSWORD)
            )
    }

    /**
     * Check whether all properties of this configuration are defined, so that a tracking operation can be started.
     */
    fun isDefined(): Boolean =
        serverUri.isNotEmpty() && basePath.isNotEmpty() && user.isNotEmpty() && password.isNotEmpty()
}

/**
 * Return the value of the property with the given [name] from the managed preferences or an empty string if the
 * property does not exist.
 */
private fun PreferencesHandler.stringProperty(name: String): String =
    preferences.getString(name, null).orEmpty()
