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

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.apache.commons.lang3.RandomStringUtils

/**
 * Test class for [Base64].
 */
class Base64Spec : StringSpec() {
    /**
     * Checks the encoding of random strings with the given length.
     */
    private fun checkEncoding(length: Int) {
        for (i in 0..64) {
            val str = RandomStringUtils.randomAscii(length)
            val expected = org.apache.commons.codec.binary.Base64.encodeBase64String(str.toByteArray())

            val actual = Base64.encode(str)
            actual shouldBe expected
        }
    }

    init {
        "Base64 should correctly encode strings without padding" {
            checkEncoding(60)
        }

        "Base64 should correctly encode strings with a padding of 1" {
            checkEncoding(61)
        }

        "Base64 should correctly encode strings with a padding of 2" {
            checkEncoding(62)
        }
    }
}