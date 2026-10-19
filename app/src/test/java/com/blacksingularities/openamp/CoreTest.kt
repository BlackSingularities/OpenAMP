package com.blacksingularities.openamp

import com.blacksingularities.openamp.lyrics.LrcParser
import com.blacksingularities.openamp.lyrics.LyricsSource
import com.blacksingularities.openamp.update.isNewer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreTest {
    @Test
    fun versions() {
        assertTrue(isNewer("1.0.12", "1.0.9"))
        assertTrue(isNewer("1.1.0", "1.0.9.40"))
        assertFalse(isNewer("1.0.0", "1.0.0"))
        assertFalse(isNewer("1.0.0", "1.0.0.5"))
    }

    @Test
    fun syncedLyrics() {
        val l = LrcParser.parse("[ar:X]\n[00:01.50]Pierwsza\n[00:03.00][00:10.00]Refren\n[00:05.123]<00:05.20>Słowo", LyricsSource.USER)!!
        assertTrue(l.synced)
        assertEquals(listOf(1500L, 3000L, 5123L, 10000L), l.lines.map { it.timeMs })
        assertEquals("Słowo", l.lines[2].text)
        assertEquals(-1, l.indexAt(1000))
        assertEquals(1, l.indexAt(4000))
        assertEquals(3, l.indexAt(60_000))
    }

    @Test
    fun plainLyrics() {
        val l = LrcParser.parse("Linia 1\nLinia 2", LyricsSource.TAGS)!!
        assertFalse(l.synced)
        assertEquals(2, l.lines.size)
    }
}
