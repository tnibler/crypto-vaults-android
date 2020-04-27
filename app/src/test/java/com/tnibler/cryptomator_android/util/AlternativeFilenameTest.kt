package com.tnibler.cryptomator_android.util

import org.junit.After
import org.junit.Assert
import org.junit.Before

import org.junit.Assert.*
import org.junit.Test

class AlternativeFilenameTest {
    fun List<Pair<String, String>>.runTests() {
        forEach {  c ->
            Assert.assertEquals(c.second, alternativeName(c.first))
        }
    }

    @Test
    fun noExtensionNoNumber() {
        val cases: List<Pair<String, String>> = listOf(
            "asd123" to "asd123 (1)",
            "testFileName" to "testFileName (1)",
            "19323" to "19323 (1)",
            "no_number (in parens)" to "no_number (in parens) (1)",
            "space everywhere   " to "space everywhere    (1)",
            "just a dot." to "just a dot. (1)"
        )
        cases.runTests()
    }

    @Test
    fun extensionNoNumber() {
        val cases: List<Pair<String, String>> = listOf(
            "asd123.txt" to "asd123 (1).txt",
            "testFileName.ext_" to "testFileName (1).ext_",
            "19323.jpg" to "19323 (1).jpg",
            "no_number (in parens).ext1" to "no_number (in parens) (1).ext1",
            "space everywhere   .bmp" to "space everywhere    (1).bmp",
            "what.is.the.extension" to "what.is.the (1).extension"
        )
        cases.runTests()
    }

    @Test
    fun noExtensionNumber() {
        val cases: List<Pair<String, String>> = listOf(
            "asd123 (1)" to "asd123 (2)",
            "testFileName (2)" to "testFileName (3)",
            "19323(( (2)" to "19323(( (3)",
            "no_number (in parens) (123)" to "no_number (in parens) (124)",
            "space everywhere   (23)" to "space everywhere   (24)",
            "just a dot. (1)" to "just a dot. (2)"
        )
        cases.runTests()
    }

    @Test
    fun extensionNumber() {
        val cases: List<Pair<String, String>> = listOf(
            "asd123 (3).txt" to "asd123 (4).txt",
            "testFileName (1230).ext_" to "testFileName (1231).ext_",
            "19323 (2).jpg" to "19323 (3).jpg",
            "no_number (in parens) (2).ext1" to "no_number (in parens) (3).ext1",
            "space everywhere   (38).bmp" to "space everywhere   (39).bmp",
            "what.is.the (23).extension" to "what.is.the (24).extension"
        )
        cases.runTests()
    }
}