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
package com.github.oheger.locationteller.server

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.MappingBuilder
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.options
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import com.github.tomakehurst.wiremock.matching.UrlPattern
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import io.kotest.core.listeners.TestListener
import io.kotest.core.spec.Spec
import io.kotest.core.test.TestCase
import io.kotest.core.test.TestResult

/**
 * A class providing functionality to use a WireMock server in a test spec.
 */
class WireMockSupport : TestListener {
    companion object {
        /** Test base path for server requests.*/
        private const val basePath = "/track/base"

        /** Test user name.*/
        const val user = "scott"

        /** Test password.*/
        const val password = "tiger"

        /** Constant for a success status.*/
        const val StatusOk = 200

        /**
         * Returns a mapping builder that is configured with the expected
         * authorization header.
         * @param mappingBuilder the builder to be configured
         * @return the configured mapping builder
         */
        fun authorized(mappingBuilder: MappingBuilder): MappingBuilder =
            mappingBuilder.withBasicAuth(user, password)
    }

    /** The server managed by this object.*/
    private lateinit var server: WireMockServer

    /**
     * This implementation stops the managed server.
     */
    override suspend fun afterSpec(spec: Spec) {
        server.stop()
    }

    /**
     * This implementation resets the server, so that new verifications can be
     * made for the next test case.
     */
    override suspend fun afterTest(testCase: TestCase, result: TestResult) {
        server.resetAll()
    }

    /**
     * This implementation creates the server to run tests against.
     */
    override suspend fun beforeSpec(spec: Spec) {
        val serverOptions = options().dynamicPort()
        server = WireMockServer(serverOptions)
        server.start()
    }

    /**
     * Add a stub to the wrapped server based on [builder].
     */
    fun stubFor(builder: MappingBuilder): StubMapping = server.stubFor(builder)

    /**
     * Run a verification based on [builder] against the wrapped server.
     */
    fun verify(builder: RequestPatternBuilder) {
        server.verify(builder)
    }

    /**
     * Returns a [ServerConfig] object that points to the mock server managed
     * by this object.
     * @return the server configuration
     */
    fun config(): ServerConfig =
        ServerConfig("http://localhost:${server.port()}", basePath, user, password)

    /**
     * Returns a URL pattern that matches a path on the mock server. The
     * configured base path is added to the given path.
     * @param path the path
     * @return the resulting URL patter
     */
    fun serverPath(path: String): UrlPattern = urlPathEqualTo(basePath + path)
}
