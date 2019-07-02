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
import io.ktor.client.call.receive
import io.ktor.client.request.*
import io.ktor.client.response.HttpResponse
import io.ktor.client.response.readText
import io.ktor.http.HttpMethod
import io.ktor.http.isSuccess
import org.slf4j.LoggerFactory
import java.io.InputStream
import javax.xml.parsers.SAXParserFactory

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
    private val log = LoggerFactory.getLogger(DavClient::class.java)

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
     * Loads a folder from the server and returns an object with its content.
     *
     * The object returned contains the elements (files and folders) that are
     * children of the folder requested. The list with elements is sorted
     * alphabetically.
     *
     * If the folder does not exist or some other error occurs, a folder object
     * with an empty path and an empty content list is returned.
     * @param path the path to the folder to be retrieved
     * @return an object describing the folder requested
     */
    suspend fun loadFolder(path: String): DavFolder {
        val resolvedPath = appendSeparator(resolvePath(path))
        try {
            val response = httpClient.request<HttpResponse>(resolvedPath) {
                method = HttpMethod(MethodPropFind)
                header(HeaderAccept, MediaXml)
                header(HeaderDepth, DepthValue)
                header(HeaderAuthorization, authorizationHeader)
            }

            val handler = FolderContentSaxHandler()
            val parserFactory = SAXParserFactory.newInstance()
            val parser = parserFactory.newSAXParser()
            parser.parse(response.receive<InputStream>(), handler)
            return DavFolder(path, handler.folderContent())
        } catch (e: Exception) {
            log.error("Could not load content of folder $resolvedPath.", e)
            return DummyFolder
        }
    }

    /**
     * Removes elements from the server.
     * @param path the path to the element to be removed
     * @return a flag whether this operation was successful
     */
    suspend fun delete(path: String): Boolean {
        val response = httpClient.delete<HttpResponse>(resolvePath(path)) {
            header(HeaderAuthorization, authorizationHeader)
        }
        return response.status.isSuccess()
    }

    /**
     * Creates a folder under the given path.
     * @param path the path to create the new folder
     * @return a flag whether this operation was successful
     */
    suspend fun createFolder(path: String): Boolean {
        val resolvedPath = appendSeparator(resolvePath(path))
        val response = httpClient.request<HttpResponse>(resolvedPath) {
            method = HttpMethod("MKCOL")
            header(HeaderAuthorization, authorizationHeader)
        }

        // Status 'Method not allowed' is returned if the folder already exists.
        // This is treated as success here because for the further proceeding it
        // only matters that the folder exists.
        return response.status.isSuccess() || response.status.value == StatusMethodNotAllowed
    }

    /**
     * Reads the content of a file from the server. It is expected that the
     * file is small, and the whole content can be read at once. If the request
     * fails for some reason, an empty string is returned.
     * @param path the path to the file to be read
     * @return the content of the file or an empty string
     */
    suspend fun readFile(path: String): String {
        val resolvedPath = resolvePath(path)
        return try {
            val response = httpClient.get<HttpResponse>(resolvedPath) {
                header(HeaderAuthorization, authorizationHeader)
            }
            response.readText()
        } catch (e: Exception) {
            log.error("Could not read file $resolvedPath.", e)
            ""
        }
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

        /** Name of the Accept header.*/
        private const val HeaderAccept = "Accept"

        /** Name of the custom Depth header.*/
        private const val HeaderDepth = "Depth"

        /** The media type for XML to be used for the accept header).*/
        private const val MediaXml = "text/xml"

        /** The value to be passed to the Depth header.*/
        private const val DepthValue = "1"

        /** Constant for the custom PROPFIND HTTP method.*/
        private const val MethodPropFind = "PROPFIND"

        /** Constant for the HTTP status code 'method not allowed'.*/
        private const val StatusMethodNotAllowed = 405

        /** Constant for a dummy folder to be returned in case of an error.*/
        private val DummyFolder = DavFolder("", listOf())

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
            val authStr = "${config.user}:${config.password}"
            return "Basic " + Base64.encode(authStr)
        }

        /**
         * Adds a separator to a path if it does not contain one yet.
         * @param path the path
         * @return the modified path
         */
        private fun appendSeparator(path: String): String =
            if (path.endsWith(UriSeparator)) path
            else path + UriSeparator
    }
}
