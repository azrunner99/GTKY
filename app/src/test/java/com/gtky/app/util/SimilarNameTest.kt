package com.gtky.app.util

import com.gtky.app.data.repository.MatchKind
import com.gtky.app.data.repository.classifyNameMatch
import org.junit.Assert.assertEquals
import org.junit.Test

class SimilarNameTest {
    @Test fun exactMatch() =
        assertEquals(MatchKind.EXACT, classifyNameMatch("Alex", "Smith", "Alex", "Smith"))
    @Test fun exactMatchCaseInsensitive() =
        assertEquals(MatchKind.EXACT, classifyNameMatch("alex", "smith", "Alex", "Smith"))
    @Test fun prefixLongerTyped() =
        assertEquals(MatchKind.PREFIX_LONGER, classifyNameMatch("Alex", "S", "Alex", "Smith"))
    @Test fun prefixShorterTyped() =
        assertEquals(MatchKind.PREFIX_SHORTER, classifyNameMatch("Alex", "Smith", "Alex", "S"))
    @Test fun sameInitialDifferentName() =
        assertEquals(MatchKind.SAME_INITIAL, classifyNameMatch("Alex", "Smith", "Alex", "Smyth"))
    @Test fun differentFirstName() =
        assertEquals(null, classifyNameMatch("Alex", "S", "Alan", "Smith"))
    @Test fun differentInitial() =
        assertEquals(null, classifyNameMatch("Alex", "Jones", "Alex", "Smith"))
    @Test fun bothLastEmpty() =
        assertEquals(MatchKind.EXACT, classifyNameMatch("Alex", "", "Alex", ""))
    @Test fun typedLastEmptyExistingHasLast() =
        assertEquals(null, classifyNameMatch("Alex", "", "Alex", "Smith"))
    @Test fun typedHasLastExistingEmpty() =
        assertEquals(null, classifyNameMatch("Alex", "Smith", "Alex", ""))
}
