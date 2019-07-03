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
import io.kotlintest.matchers.collections.shouldHaveSize
import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import io.ktor.client.HttpClient
import io.mockk.*

/**
 * Test class for [DavClient].
 */
class DavClientSpec : StringSpec() {
    override fun listeners(): List<TestListener> = listOf(WireMockSupport)

    /**
     * Adds a stubbing declaration for a request to a folder that is served with
     * the file specified.
     *
     * @param path          the path of the folder
     * @param responseFile the file to serve the request
     * @param status       the status code to return from the request
     */
    private fun stubFolderRequest(path: String, responseFile: String, status: Int = StatusOk) {
        val normalizedPath = if (path.endsWith(UriSeparator)) path
        else path + UriSeparator
        stubFor(
            authorized(
                request("PROPFIND", serverPath(normalizedPath))
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

        "DavClient should return a DavFolder object when a folder is queried" {
            val path = "/test/folder"
            stubFolderRequest(path, "folder.xml")
            val expectedElements = listOf(
                DavElement("audio.mp3", false),
                DavElement("childFolder", true), DavElement("music.mp3", false),
                DavElement("sound.mp3", false), DavElement("subFolder", true)
            )
            val client = DavClient.create(WireMockSupport.config())

            val folder = client.loadFolder(path)
            folder.path shouldBe path
            folder.elements shouldBe expectedElements
        }

        "DavClient should return a dummy folder object for a failed request" {
            val path = "/"
            stubFolderRequest(path, "folder.xml", 400)
            val client = DavClient.create(WireMockSupport.config())

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
            val client = DavClient.create(WireMockSupport.config())

            val folder = client.loadFolder(path)
            folder.path shouldBe path
            folder.elements shouldBe expectedElements
        }

        "DavClient should handle empty folder results" {
            stubFolderRequest("/", "folder_empty.xml")
            val client = DavClient.create(WireMockSupport.config())

            val folder = client.loadFolder("/")
            folder.path shouldBe "/"
            folder.elements shouldHaveSize 0
        }

        "DavClient should delete an element from the server" {
            val path = "data/test.txt"
            stubFor(
                authorized(delete(serverPath(path)))
                    .willReturn(aResponse().withStatus(StatusOk))
            )
            val client = DavClient.create(WireMockSupport.config())

            client.delete(path) shouldBe true
            verify(deleteRequestedFor(serverPath(path)))
        }

        "DavClient should evaluate the status code when deleting elements" {
            stubFor(
                authorized(delete(anyUrl()))
                    .willReturn(aResponse().withStatus(403))
            )
            val client = DavClient.create(WireMockSupport.config())

            client.delete("/some/path") shouldBe false
        }

        "DavClient should create a new folder on the server" {
            val path = "/my/new/folder"
            stubFor(
                authorized(request("MKCOL", serverPath("$path/")))
                    .willReturn(aResponse().withStatus(StatusOk))
            )
            val client = DavClient.create(WireMockSupport.config())

            client.createFolder(path) shouldBe true
        }

        "DavClient should evaluate the status code when creating a new folder" {
            val path = "/broken/folder/"
            stubFor(
                authorized(request("MKCOL", serverPath(path)))
                    .willReturn(aResponse().withStatus(500))
            )
            val client = DavClient.create(WireMockSupport.config())

            client.createFolder(path) shouldBe false
        }

        "DavClient should treat a 405 response when creating a folder as successful" {
            val path = "/existing/folder/"
            stubFor(
                authorized(request("MKCOL", serverPath(path)))
                    .willReturn(aResponse().withStatus(405))
            )
            val client = DavClient.create(WireMockSupport.config())

            client.createFolder(path) shouldBe true
        }

        "DavClient should read a file from the server" {
            val path = "/my/data/file.txt"
            val content = "This is the content of my data file!"
            stubFor(
                authorized(get(serverPath(path)))
                    .willReturn(
                        aResponse()
                            .withStatus(StatusOk).withBody(content)
                    )
            )
            val client = DavClient.create(WireMockSupport.config())

            client.readFile(path) shouldBe content
        }

        "DavClient should handle errors when reading a file" {
            val path = "/my/error/file.bad"
            stubFor(
                authorized(get(serverPath(path)))
                    .willReturn(
                        aResponse()
                            .withStatus(403)
                    )
            )
            val client = DavClient.create(WireMockSupport.config())

            client.readFile(path) shouldBe ""
        }

        "DavClient should close itself" {
            val httpClient = mockk<HttpClient>()
            every { httpClient.close() } just runs
            val client = DavClient(WireMockSupport.config(), httpClient)

            client.close()
            verify { httpClient.close() }
        }
    }
}
