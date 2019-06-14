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
package com.github.oheger.locationteller.server

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.put
import io.ktor.client.response.HttpResponse
import io.ktor.http.isSuccess
import org.apache.commons.codec.binary.Base64

/**
 * A class implementing basic DAV operations against the location server.
 *
 * This class offers functionality (on a protocol level) to upload data files
 * to the server and to retrieve location data stored there.
 *
 * @param config the server configuration
 * @param httpClient the client for sending HTTP requests
 */
class DavClient(val config: ServerConfig, private val httpClient: HttpClient) {
    /** The authorization header.*/
    private val authorizationHeader = authHeader(config)

    /**
     * Uploads a file to the server. A file with the given path and the content
     * specified is created on the server.
     * @param path the path to the file to be created
     * @param content the content of the new file
     * @return a flag whether this operation was successful
     */
    suspend fun upload(path: String, content: String): Boolean {
        val response = httpClient.put<HttpResponse>(resolvePath(path)) {
            body = content
            header(HeaderAuthorization, authorizationHeader)
        }
        return response.status.isSuccess()
    }

    /**
     * Resolves a relative path to a URI for the target server.
     * @param path the path to be resolved
     * @return the resolved path
     */
    private fun resolvePath(path: String) = "${config.serverUri}${config.basePath}$path"

    companion object {
        /** Name of the authorization header.*/
        private const val HeaderAuthorization = "Authorization"

        /**
         * Creates a new instance of [DavClient] that is configured with a
         * default HTTP client.
         * @param config the server configuration
         * @return the new client instance
         */
        fun create(config: ServerConfig): DavClient {
            val httpClient = HttpClient()
            return DavClient(config, httpClient)
        }

        /**
         * Calculates the header value for the Authorization header based on
         * the given configuration.
         * @param config the server configuration
         * @return the value for the Authorization header
         */
        private fun authHeader(config: ServerConfig): String {
            val authBytes = "${config.user}:${config.password}".toByteArray()
            return "Basic " + Base64.encodeBase64String(authBytes)
        }
    }
}
