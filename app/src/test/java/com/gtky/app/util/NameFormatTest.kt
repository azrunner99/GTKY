package com.gtky.app.util

import org.junit.Assert.assertEquals
import org.junit.Test

class NameFormatTest {
    @Test fun lowercase() = assertEquals("Alex Smith", normalizeName("alex smith"))
    @Test fun uppercase() = assertEquals("Alex Smith", normalizeName("ALEX SMITH"))
    @Test fun mixedCase() = assertEquals("Alex Smith", normalizeName("aLeX sMiTh"))
    @Test fun singleInitial() = assertEquals("Alex S", normalizeName("alex s"))
    @Test fun extraWhitespace() = assertEquals("Alex S", normalizeName("  alex   s  "))
    @Test fun hyphenated() = assertEquals("Mary-Jane Lee", normalizeName("mary-jane LEE"))
    @Test fun apostrophe() = assertEquals("O'brien", normalizeName("o'brien"))
    @Test fun empty() = assertEquals("", normalizeName(""))
    @Test fun whitespaceOnly() = assertEquals("", normalizeName("   "))
    @Test fun singleLetter() = assertEquals("A", normalizeName("a"))
    @Test fun alreadyCorrect() = assertEquals("Alex Smith", normalizeName("Alex Smith"))
    @Test fun threeWords() = assertEquals("Mary Ann Lee", normalizeName("mary ann lee"))
}
