package com.os4.musiccover.lyrics

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AppleLyricStoreTest {
    private val opening = "The opening line"
    private fun lyric(lines: String, rev: Int) = JSONObject().put("lyric", lines).put("revision", rev).toString()
    private val plain = lyric("[00:00.275]$opening\n[00:05.000]a different division\n", 1)
    private val duet = lyric("[00:00.266]$opening\n[00:04.763]the second voice\n", 2)

    @Test fun richNativeCallbackSurvivesItsRepeatedPlainVariantAndAnEqualRoleUpdateWins() {
        val store = AppleLyricStore()
        store.playing("track", "title")
        assertEquals(plain, store.offer(store.begin("track"), plain, false)!!.payload)
        val b = store.offer(store.begin("track"), duet, true)!!
        assertNull(store.offer(store.begin("track"), duet, true))
        assertNull(store.offer(store.begin("track"), plain, false))
        assertSame(b, store.held())
        assertEquals(LyricSource.lyricRef("track", duet), b.ref)
        val edit = lyric("[00:00.266]$opening\n[00:04.763]corrected line", 3)
        assertEquals(edit, store.offer(store.begin("track"), edit, true)!!.payload)
        assertNull(store.offer(store.begin("track"), plain, false))
    }

    @Test fun sameIdWithUnrelatedTextOrAnotherRecordingCanReplaceTheDuet() {
        val store = AppleLyricStore()
        store.playing("a", "name")
        store.offer(store.begin("a"), plain, false)
        val unrelated = lyric("[00:03.000]totally different\n[01:10.000]nothing shared", 2)
        store.offer(store.begin("a"), unrelated, true)
        assertEquals(plain, store.offer(store.begin("a"), plain, false)!!.payload)
        store.offer(store.begin("a"), duet, true)
        val late = store.begin("a")
        store.playing("another-recording", "name")
        assertNull(store.held())
        assertNull(store.offer(late, duet, true))
        assertEquals(plain, store.offer(store.begin("another-recording"), plain, false)!!.payload)
        assertEquals("another-recording", store.held()!!.id)
        store.playing("a", "name")
        assertNull(store.held())
        assertNull(store.offer(late, duet, true))
    }

    @Test fun prefetchCanLandBeforeOrAfterTheSwitchAndCannotEvictThePlayingTrack() {
        val store = AppleLyricStore()
        val early = store.begin("a")
        assertNull(store.offer(early, plain, false))
        assertEquals(plain, store.playing("a", "first")!!.payload)
        val next = store.begin("b")
        val old = store.begin("a")
        store.playing("b", "second")
        assertEquals(duet, store.offer(next, duet, true)!!.payload)
        assertNull(store.offer(old, plain, false))
        assertNull(store.offer(store.begin("a"), plain, false))
        assertEquals("b", store.held()!!.id)
        assertEquals(plain, store.playing("a", "first")!!.payload)
    }

    @Test fun outOfOrderEquivalentCallbackCannotUndoALaterEdit() {
        val store = AppleLyricStore()
        store.playing("a", "title")
        val older = store.begin("a")
        val newer = store.begin("a")
        val fixed = lyric("[00:00.266]$opening\n[00:08.000]edited words", 9)
        store.offer(newer, fixed, true)
        assertNull(store.offer(older, duet, true))
        assertEquals(fixed, store.held()!!.payload)
    }

    @Test fun twoTimedLinesIdentifyAPoorerVariantEvenIfTheRichOneArrivesFirst() {
        val store = AppleLyricStore()
        store.playing("a", "name")
        val strong = lyric("[00:00.200]Hello\n[00:02.000]World\n[00:06.000]answer", 1)
        val smaller = lyric("[00:00.220]ＨＥＬＬＯ!\n[00:02.010]World\n", 2)
        store.offer(store.begin("a"), strong, true)
        assertNull(store.offer(store.begin("a"), smaller, false))
        assertEquals(strong, store.held()!!.payload)
    }

    @Test fun hotReloadKeepsTheSelectionAndEachReplayCanRequestAgain() {
        val from = AppleLyricStore()
        from.playing("a", "song")
        val epoch = from.missingEpoch("a")
        from.offer(from.begin("a"), plain, false)
        from.offer(from.begin("a"), duet, true)
        val s = from.save()
        val to = AppleLyricStore()
        to.adopt(s[0] as String?, s[1] as String?, s[2] as String?, s[3] as String?, s[4] as String?, s[5] as String?)
        assertNull(to.offer(to.begin("a"), plain, false))
        assertEquals(LyricSource.lyricRef("a", duet), to.held()!!.ref)
        from.clear()
        from.playing("a", "song")
        assertTrue(from.missingEpoch("a") > epoch)
    }
}
