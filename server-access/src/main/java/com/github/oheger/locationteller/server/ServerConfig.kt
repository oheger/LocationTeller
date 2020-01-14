/*
 * Copyright 2019-2020 The Developers.
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
package com.github.oheger.locationteller.server

/**
 * A class with configuration settings related to the server to which location
 * data needs to be pushed.
 *
 * This configuration is evaluated by the components that interact with the
 * location server. Requests to upload location data or to retrieve the latest
 * known locations are generated based on this information.
 *
 * @param serverUri the URI of the server
 * @param basePath the relative base path under which location information is
 * stored on the server; this path is prepended to relative URIs used for
 * server interactions
 * @param user the user name for authentication
 * @param password the password to log into the server
 */
data class ServerConfig(
    val serverUri: String,
    val basePath: String,
    val user: String,
    val password: String
)
