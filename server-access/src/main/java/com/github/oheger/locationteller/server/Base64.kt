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
 * An object providing base64 encoding capabilities.
 *
 * In this project, we have a problem with base64 encoding:
 * * The encoder class from the JDK is Java 8 only and not available on lower
 * Android API levels.
 * * The variant from Apache Commons Codec does not work under Android because
 * there is a conflict with an older version used internally.
 * * The Android encoder class should not be used because this a pure Java
 * library.
 *
 * Therefore, this is a custom, minimum base 64 encoder implementation.
 */
object Base64 {
    /** The mapping table.*/
    private val mapping = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

    /**
     * Applies a Base64 encoding to the given input string.
     * @param input the input string
     * @return the Base64-encoded input string
     */
    fun encode(input: String): String {
        val inputBytes = input.toByteArray()
        val resultBuf = StringBuilder(inputBytes.size / 3 + 1)

        fun byteAt(idx: Int): Int =
            if (idx >= inputBytes.size) 0 else inputBytes[idx].toInt()

        var idx = 0
        while (idx < inputBytes.size) {
            val byte1 = inputBytes[idx].toInt()
            val byte2 = byteAt(idx + 1)
            val byte3 = byteAt(idx + 2)

            val dig1 = (byte1 and 0xFC) shr 2
            val dig2 = ((byte1 and 3) shl 4) or ((byte2 and 0xF0) shr 4)
            val dig3 = ((byte2 and 0x0F) shl 2) or ((byte3 and 0xC0) shr 6)
            val dig4 = byte3 and 0x3F
            resultBuf.append(mapping[dig1]).append(mapping[dig2]).append(mapping[dig3]).append(mapping[dig4])
            idx += 3
        }

        val modulus = inputBytes.size % 3
        val padSize = if (modulus == 0) 0 else 3 - modulus
        val padIdx = resultBuf.length - padSize
        for (i in 0 until padSize) {
            resultBuf[padIdx + i] = '='
        }
        return resultBuf.toString()
    }
}