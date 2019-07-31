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

import okhttp3.*
import org.slf4j.LoggerFactory
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.Continuation
import kotlin.coroutines.suspendCoroutine

/**
 * A class representing the response of an HTTP request.
 *
 * This class is restricted to the information that is relevant for the use
 * cases of this application. It contains a flag whether the request was
 * successful and an additional status code. If the response has a body, it is
 * available here as a string. (We expect that the sizes of responses will not
 * become too git; therefore, they are loaded directly into memory. If there is
 * no response entity, or the request was a failure, an empty string is used as
 * content.
 *
 * @param successful flag whether the request was successful
 * @param status the HTTP status code
 * @param content the content of the response entity
 */
data class HttpResponse(
    val successful: Boolean,
    val status: Int,
    val content: String
)

/**
 * A class that wraps an HTTP client and offers functions for conveniently
 * executing different types of requests.
 *
 * This is not an all-purpose HTTP client, but it is very focused on the use
 * cases of the tracking application. Therefore, the API is very lean. The main
 * goal of this class is to access the asynchronous HTTP client used behind the
 * scenes via co-routines.
 */
class TrackHttpClient private constructor(private val client: OkHttpClient) {
    private val log = LoggerFactory.getLogger(TrackHttpClient::class.java)

    /**
     * Sends a request to retrieve data. The major difference to an update
     * request is that this function evaluates the body of the response and
     * transforms it to a string that is delivered with the [HttpResponse]
     * object.
     * @param request the request to be sent
     * @return the response for this request
     */
    suspend fun request(request: Request): HttpResponse =
        sendRequest(request, processBody = true)

    /**
     * Sends an update request. This is a request for which no data is loaded
     * from the server; only a status code about the success of the operation
     * is expected.
     * @param request the request to be sent
     * @return the response for this request
     */
    suspend fun update(request: Request): HttpResponse =
        sendRequest(request, processBody = false)

    /**
     * Helper function to send a request and generate the response. This
     * function does the communication with the underlying HTTP client and
     * converts the asynchronous programming model to the co-routine paradigm.
     * @param request the request to be sent
     * @param processBody flag whether the body should be evaluated
     * @return the response for this request
     */
    private suspend fun sendRequest(request: Request, processBody: Boolean): HttpResponse = suspendCoroutine { cont ->
        client.newCall(request).enqueue(createCallback(cont, processBody))
    }

    /**
     * Creates a callback object to handle an HTTP call. The given continuation
     * is resumed when a result (or an error) is received from the call. All
     * responses - including errors - are mapped to [HttpResponse] objects.
     * @param cont the object to resume the current co-routine
     * @param processBody flag whether the body should be evaluated
     */
    private fun createCallback(cont: Continuation<HttpResponse>, processBody: Boolean): Callback =
        object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                log.error("Call failed.", e)
                cont.resumeWith(Result.success(errorResponse))
            }

            override fun onResponse(call: Call, response: Response) {
                val content = if (processBody) {
                    response.body.use { body -> body?.string() ?: emptyContent }
                } else emptyContent
                cont.resumeWith(Result.success(HttpResponse(response.isSuccessful, response.code, content)))
            }
        }

    companion object {
        /** Constant for the string indicating that no content is available.*/
        const val emptyContent = ""

        /**
         * Constant for the generic error response. This response is returned
         * if there was a fatal error during HTTP communication. No response
         * from the server could be retrieved; hence, there is no valid status
         * code.
         */
        val errorResponse = HttpResponse(false, -1, emptyContent)

        /**
         * Creates a new instance of _TrackHttpClient_.
         * @param timeoutMillis the timeout for calls
         * @return the new client instance
         */
        fun create(timeoutMillis: Long): TrackHttpClient {
            val client = OkHttpClient.Builder().callTimeout(timeoutMillis, TimeUnit.MILLISECONDS).build()
            return TrackHttpClient(client)
        }
    }
}
