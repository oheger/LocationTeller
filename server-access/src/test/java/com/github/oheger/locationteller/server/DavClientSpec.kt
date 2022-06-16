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

import com.github.oheger.locationteller.server.WireMockSupport.Companion.authorized
import com.github.tomakehurst.wiremock.client.BasicCredentials
import com.github.tomakehurst.wiremock.client.WireMock.*
import io.kotest.core.listeners.TestListener
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

/**
 * Test class for [DavClient].
 */
class DavClientSpec : StringSpec() {
    private val wireMockSupport = WireMockSupport()

    override fun listeners(): List<TestListener> = listOf(wireMockSupport)

    /**
     * Adds a stubbing declaration for a request to a folder that is served with
     * the file specified.
     *
     * @param path          the path of the folder
     * @param responseFile the file to serve the request
     * @param status       the status code to return from the request
     */
    private fun stubFolderRequest(
        path: String,
        responseFile: String,
        status: Int = WireMockSupport.StatusOk
    ) {
        val normalizedPath = if (path.endsWith(UriSeparator)) path
        else path + UriSeparator
        wireMockSupport.stubFor(
            authorized(
                request("PROPFIND", wireMockSupport.serverPath(normalizedPath))
                    .withHeader("Accept", equalTo("text/xml"))
                    .withHeader("Depth", equalTo("1"))
                    .willReturn(
                        aResponse()
                            .withStatus(status)
                            .withBodyFile(responseFile)
                    )
            )
        )
    }

    init {
        "DavClient should upload data" {
            val path = "/foo/loc.txt"
            val content = "123,21231;654,214578"
            wireMockSupport.stubFor(
                authorized(put(wireMockSupport.serverPath(path)))
                    .willReturn(
                        aResponse()
                            .withStatus(WireMockSupport.StatusOk)
                    )
            )
            val client = DavClient.create(wireMockSupport.config())

            client.upload(path, content) shouldBe true
            wireMockSupport.verify(
                putRequestedFor(wireMockSupport.serverPath(path))
                    .withRequestBody(equalTo(content))
                    .withBasicAuth(BasicCredentials(WireMockSupport.user, WireMockSupport.password))
            )
        }

        "DavClient should evaluate the status code when uploading data" {
            val path = "/foo/loc.txt"
            wireMockSupport.stubFor(
                authorized(put(wireMockSupport.serverPath(path)))
                    .willReturn(
                        aResponse()
                            .withStatus(400)
                    )
            )
            val client = DavClient.create(wireMockSupport.config())

            client.upload(path, "some content") shouldBe false
        }

        "DavClient should return a DavFolder object when a folder is queried" {
            val path = "/test/folder"
            stubFolderRequest(path, "folder.xml")
            val expectedElements = listOf(
                DavElement("audio.mp3", false),
                DavElement("childFolder", true), DavElement("music.mp3", false),
                DavElement("sound.mp3", false), DavElement("subFolder", true)
            )
            val client = DavClient.create(wireMockSupport.config())

            val folder = client.loadFolder(path)
            folder.path shouldBe path
            folder.elements shouldBe expectedElements
        }

        "DavClient should return a dummy folder object for a failed request" {
            val path = "/"
            stubFolderRequest(path, "folder.xml", 400)
            val client = DavClient.create(wireMockSupport.config())

            val folder = client.loadFolder(path)
            folder.elements shouldHaveSize 0
            folder.path shouldBe ""
        }

        "DavClient should handle folder results with missing elements" {
            val path = "/test/"
            stubFolderRequest(path, "folder_missing_elements.xml")
            val expectedElements = listOf(
                DavElement("audio.mp3", false),
                DavElement("sound.mp3", false)
            )
            val client = DavClient.create(wireMockSupport.config())

            val folder = client.loadFolder(path)
            folder.path shouldBe path
            folder.elements shouldBe expectedElements
        }

        "DavClient should handle empty folder results" {
            stubFolderRequest("/", "folder_empty.xml")
            val client = DavClient.create(wireMockSupport.config())

            val folder = client.loadFolder("/")
            folder.path shouldBe "/"
            folder.elements shouldHaveSize 0
        }

        "DavClient should handle a folder result without content" {
            wireMockSupport.stubFor(
                request("PROPFIND", wireMockSupport.serverPath("/"))
                    .willReturn(aResponse().withStatus(200))
            )
            val client = DavClient.create(wireMockSupport.config())

            val folder = client.loadFolder("/")
            folder.path shouldBe ""
            folder.elements shouldHaveSize 0
        }

        "DavClient should handle an invalid XML response for a folder" {
            stubFolderRequest("/", "folder_invalid.txt")
            val client = DavClient.create(wireMockSupport.config())

            val folder = client.loadFolder("/")
            folder.path shouldBe ""
            folder.elements shouldHaveSize 0
        }

        "DavClient should delete an element from the server" {
            val path = "data/test.txt"
            wireMockSupport.stubFor(
                authorized(delete(wireMockSupport.serverPath(path)))
                    .willReturn(aResponse().withStatus(WireMockSupport.StatusOk))
            )
            val client = DavClient.create(wireMockSupport.config())

            client.delete(path) shouldBe true
            wireMockSupport.verify(deleteRequestedFor(wireMockSupport.serverPath(path)))
        }

        "DavClient should evaluate the status code when deleting elements" {
            wireMockSupport.stubFor(
                authorized(delete(anyUrl()))
                    .willReturn(aResponse().withStatus(403))
            )
            val client = DavClient.create(wireMockSupport.config())

            client.delete("/some/path") shouldBe false
        }

        "DavClient should treat status code 207 as error when deleting elements" {
            wireMockSupport.stubFor(authorized(delete(anyUrl()))
                .willReturn(aResponse().withStatus(207)
                    .withBody("<error>Some errors happened.</error>")))
            val client = DavClient.create(wireMockSupport.config())

            client.delete("/foo/bar") shouldBe false
        }

        "DavClient should create a new folder on the server" {
            val path = "/my/new/folder"
            wireMockSupport.stubFor(
                authorized(request("MKCOL", wireMockSupport.serverPath("$path/")))
                    .willReturn(aResponse().withStatus(WireMockSupport.StatusOk))
            )
            val client = DavClient.create(wireMockSupport.config())

            client.createFolder(path) shouldBe true
        }

        "DavClient should evaluate the status code when creating a new folder" {
            val path = "/broken/folder/"
            wireMockSupport.stubFor(
                authorized(request("MKCOL", wireMockSupport.serverPath(path)))
                    .willReturn(aResponse().withStatus(500))
            )
            val client = DavClient.create(wireMockSupport.config())

            client.createFolder(path) shouldBe false
        }

        "DavClient should treat a 405 response when creating a folder as successful" {
            val path = "/existing/folder/"
            wireMockSupport.stubFor(
                authorized(request("MKCOL", wireMockSupport.serverPath(path)))
                    .willReturn(aResponse().withStatus(405))
            )
            val client = DavClient.create(wireMockSupport.config())

            client.createFolder(path) shouldBe true
        }

        "DavClient should read a file from the server" {
            val path = "/my/data/file.txt"
            val content = "This is the content of my data file!"
            wireMockSupport.stubFor(
                authorized(get(wireMockSupport.serverPath(path)))
                    .willReturn(
                        aResponse()
                            .withStatus(WireMockSupport.StatusOk).withBody(content)
                    )
            )
            val client = DavClient.create(wireMockSupport.config())

            client.readFile(path) shouldBe content
        }

        "DavClient should handle errors when reading a file" {
            val path = "/my/error/file.bad"
            wireMockSupport.stubFor(
                authorized(get(wireMockSupport.serverPath(path)))
                    .willReturn(
                        aResponse()
                            .withStatus(403)
                    )
            )
            val client = DavClient.create(wireMockSupport.config())

            client.readFile(path) shouldBe ""
        }

        "DavClient should apply the timeout when doing requests" {
            val timeout = 500
            val path = "/my/error/timeout.txt"
            wireMockSupport.stubFor(
                authorized(
                    put(wireMockSupport.serverPath(path))
                        .willReturn(
                            aResponse().withStatus(200)
                                .withFixedDelay(2 * timeout)
                        )
                )
            )
            val client = DavClient.create(wireMockSupport.config(), timeout.toLong())

            client.upload(path, "someData") shouldBe false
        }
    }
}
