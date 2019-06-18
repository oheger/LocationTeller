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

import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import java.lang.StringBuilder

/**
 * A handler class for parsing an XML document with the content of a folder on
 * a DAV server.
 *
 * An instance of this class can be created and passed to the parse() method of
 * a SAX parser. The result of the parse operation can then be obtained by
 * calling the _folderContent()_ method.
 */
class FolderContentSaxHandler : DefaultHandler() {
    /** Stores the child elements extracted during the parse operation.*/
    private val children = mutableListOf<DavElement>()

    /** A string builder for storing the content of the current element.*/
    private val elemContent = StringBuilder()

    /** Stores the reference to the current child element.*/
    private var elemRef = ""

    /** A flag whether the current child element is a folder.*/
    private var folderFlag = false

    /** A flag whether the text of the current element needs to be recorded.*/
    private var recordText = false

    /**
     * Returns the sorted list of child elements of the current folder that has
     * been created during the parse operation.
     * @return a sorted list with all child elements
     */
    fun folderContent(): List<DavElement> =
        children.drop(1).sortedBy { it.name }

    override fun endElement(uri: String?, localName: String?, qName: String?) {
        when (xmlElementName(qName)) {
            ElemCollection ->
                folderFlag = "true" == elemText()
            ElemRef ->
                elemRef = elemText()
            ElemResponse ->
                if (elemRef.isNotEmpty()) {
                    children.add(createDavElement(elemRef, folderFlag))
                }
            else -> {
                // ignore other elements
            }

        }
    }

    override fun characters(ch: CharArray?, start: Int, length: Int) {
        if (recordText) {
            elemContent.append(ch, start, length)
        }
    }

    override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes?) {
        val name = xmlElementName(qName)
        recordText = ElemRef == name || ElemCollection == name
        if (ElemResponse == name) {
            elemRef = ""
            folderFlag = false
        }
    }

    /**
     * Returns the text of the current XML element that has been processed.
     * @return the text of the current XML element
     */
    private fun elemText(): String {
        val text = elemContent.toString().trim()
        elemContent.setLength(0)
        return text
    }

    /**
     * Extracts the name of an XML element from the qName.
     * @param name the qualified element name
     * @return the name with the namespace removed
     */
    private fun xmlElementName(name: String?): String {
        val posNs = name?.indexOf(':') ?: -1
        return name?.substring(posNs + 1) ?: ""
    }

    /**
     * Creates a [DavElement] from the passed in information. The path is
     * correctly extracted from the ref URI.
     * @param ref the reference from the folder's XML document
     * @param folderFlag flag whether the element is a folder
     */
    private fun createDavElement(ref: String, folderFlag: Boolean): DavElement {
        val refUri = if (ref.endsWith(UriSeparator)) ref.substring(0, ref.length - 1)
        else ref
        val nameStartPos = refUri.lastIndexOf(UriSeparator)
        val name = refUri.substring(nameStartPos + 1)
        return DavElement(name, folderFlag)
    }

    companion object {
        /** XML element that indicates whether an element is a collection.*/
        private const val ElemCollection = "iscollection"

        /** XML element that contains the reference to a folder element.*/
        private const val ElemRef = "href"

        /** XML element wrapping a child element response of a folder.*/
        private const val ElemResponse = "response"
    }
}