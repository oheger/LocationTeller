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

import com.github.oheger.locationteller.server.WireMockSupport.StatusOk
import com.github.oheger.locationteller.server.WireMockSupport.authorized
import com.github.oheger.locationteller.server.WireMockSupport.serverPath
import com.github.tomakehurst.wiremock.client.BasicCredentials
import com.github.tomakehurst.wiremock.client.WireMock.*
import io.kotlintest.extensions.TestListener
import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec

/**
 * Test class for [DavClient].
 */
class DavClientSpec : StringSpec() {
    override fun listeners(): List<TestListener> = listOf(WireMockSupport)

    init {
        "DavClient should upload data" {
            val path = "/foo/loc.txt"
            val content = "123,21231;654,214578"
            stubFor(
                authorized(put(serverPath(path)))
                    .willReturn(
                        aResponse()
                            .withStatus(StatusOk)
                    )
            )
            val client = DavClient.create(WireMockSupport.config())

            client.upload(path, content) shouldBe true
            verify(
                putRequestedFor(serverPath(path))
                    .withRequestBody(equalTo(content))
                    .withBasicAuth(BasicCredentials(WireMockSupport.user, WireMockSupport.password))
            )
        }

        "DavClient should evaluate the status code when uploading data" {
            val path = "/foo/loc.txt"
            stubFor(
                authorized(put(serverPath(path)))
                    .willReturn(
                        aResponse()
                            .withStatus(400)
                    )
            )
            val client = DavClient.create(WireMockSupport.config())

            client.upload(path, "some content") shouldBe false
        }
    }
}
