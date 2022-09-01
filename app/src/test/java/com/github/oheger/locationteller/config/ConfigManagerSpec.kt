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

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.verify

import java.util.concurrent.atomic.AtomicReference

class ConfigManagerSpec : WordSpec() {
    /** The mock context used by tests. */
    private val context = mockk<Context>()

    /** The mock preferences handler. */
    private val preferencesHandler = createMockPreferencesHandler()

    init {
        "create" should {
            "return a shared singleton instance" {
                val manager = ConfigManager.getInstance()

                val manager2 = ConfigManager.getInstance()

                manager2 shouldBeSameInstanceAs manager
            }
        }

        "TrackConfig" should {
            "be read and cached on first access" {
                mockkObject(TrackConfig)
                val trackConfig = mockk<TrackConfig>()
                every { TrackConfig.fromPreferences(preferencesHandler) } returns trackConfig
                val manager = ConfigManager.getInstance()

                manager.trackConfig(context) shouldBe trackConfig

                manager.trackConfig(context) shouldBe trackConfig
                verify(exactly = 1) {
                    TrackConfig.fromPreferences(preferencesHandler)
                }
            }

            "be updatable" {
                val trackConfig = mockk<TrackConfig>()
                every { trackConfig.save(preferencesHandler) } just runs
                val manager = ConfigManager.getInstance()

                manager.updateTrackConfig(context, trackConfig)

                manager.trackConfig(context) shouldBe trackConfig
                verify { trackConfig.save(preferencesHandler) }
            }

            "support change listeners" {
                val changedConfigRef = AtomicReference<TrackConfig>()
                val changedConfig = mockk<TrackConfig>(relaxed = true)
                val manager = ConfigManager.getInstance()

                manager.addTrackConfigChangeListener(changedConfigRef::set)
                manager.updateTrackConfig(context, changedConfig)

                changedConfigRef.get() shouldBe changedConfig
            }

            "support removing change listeners" {
                val changedConfigRef = AtomicReference<TrackConfig>()
                val manager = ConfigManager.getInstance()
                manager.addTrackConfigChangeListener(changedConfigRef::set)

                manager.removeTrackConfigChangeListener(changedConfigRef::set)
                manager.updateTrackConfig(context, mockk(relaxed = true))

                changedConfigRef.get() should beNull()
            }
        }

        "TrackServerConfig" should {
            "be read and cached on first access" {
                mockkObject(TrackServerConfig)
                val serverConfig = mockk<TrackServerConfig>()
                every { TrackServerConfig.fromPreferences(preferencesHandler) } returns serverConfig

                val manager = ConfigManager.getInstance()

                manager.serverConfig(context) shouldBe serverConfig

                manager.serverConfig(context) shouldBe serverConfig
                verify(exactly = 1) {
                    TrackServerConfig.fromPreferences(preferencesHandler)
                }
            }

            "be updatable" {
                val serverConfig = mockk<TrackServerConfig>()
                every { serverConfig.save(preferencesHandler) } just runs
                val manager = ConfigManager.getInstance()

                manager.updateServerConfig(context, serverConfig)

                manager.serverConfig(context) shouldBe serverConfig
                verify { serverConfig.save(preferencesHandler) }
            }

            "support change listeners" {
                val changedConfigRef = AtomicReference<TrackServerConfig>()
                val changedConfig = mockk<TrackServerConfig>(relaxed = true)
                val manager = ConfigManager.getInstance()

                manager.addServerConfigChangeListener(changedConfigRef::set)
                manager.updateServerConfig(context, changedConfig)

                changedConfigRef.get() shouldBe changedConfig
            }

            "support removing change listeners" {
                val changedConfigRef = AtomicReference<TrackServerConfig>()
                val manager = ConfigManager.getInstance()
                manager.addServerConfigChangeListener(changedConfigRef::set)

                manager.removeServerConfigChangeListener(changedConfigRef::set)
                manager.updateServerConfig(context, mockk(relaxed = true))

                changedConfigRef.get() should beNull()
            }
        }

        "ReceiverConfig" should {
            "be read and cached on first access" {
                mockkObject(ReceiverConfig)
                val receiverConfig = mockk<ReceiverConfig>()
                every { ReceiverConfig.fromPreferences(preferencesHandler) } returns receiverConfig

                val manager = ConfigManager.getInstance()

                manager.receiverConfig(context) shouldBe receiverConfig

                manager.receiverConfig(context) shouldBe receiverConfig
                verify(exactly = 1) {
                    ReceiverConfig.fromPreferences(preferencesHandler)
                }
            }

            "be updatable" {
                val receiverConfig = mockk<ReceiverConfig>()
                every { receiverConfig.save(preferencesHandler) } just runs
                val manager = ConfigManager.getInstance()

                manager.updateReceiverConfig(context, receiverConfig)

                manager.receiverConfig(context) shouldBe receiverConfig
                verify { receiverConfig.save(preferencesHandler) }
            }

            "support change listeners" {
                val changedConfigRef = AtomicReference<ReceiverConfig>()
                val changedConfig = mockk<ReceiverConfig>(relaxed = true)
                val manager = ConfigManager.getInstance()

                manager.addReceiverConfigChangeListener(changedConfigRef::set)
                manager.updateReceiverConfig(context, changedConfig)

                changedConfigRef.get() shouldBe changedConfig
            }

            "support removing change listeners" {
                val changedConfigRef = AtomicReference<ReceiverConfig>()
                val manager = ConfigManager.getInstance()
                manager.addReceiverConfigChangeListener(changedConfigRef::set)

                manager.removeReceiverConfigChangeListener(changedConfigRef::set)
                manager.updateReceiverConfig(context, mockk(relaxed = true))

                changedConfigRef.get() should beNull()
            }
        }
    }

    /**
     * Create a mock [PreferencesHandler] and install it, so that is gets returned by the _create()_ function of
     * [PreferencesHandler].
     */
    private fun createMockPreferencesHandler(): PreferencesHandler {
        mockkObject(PreferencesHandler)
        val mockHandler = mockk<PreferencesHandler>()
        every { PreferencesHandler.getInstance(context) } returns mockHandler

        return mockHandler
    }
}
