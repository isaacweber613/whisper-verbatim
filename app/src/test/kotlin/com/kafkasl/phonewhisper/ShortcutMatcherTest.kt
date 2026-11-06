package com.kafkasl.phonewhisper

import com.kafkasl.phonewhisper.ShortcutMatcher.Result
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortcutMatcherTest {
    private val five = 12
    private val star = 17
    private val other = 9
    private var clock = 0L
    /** A time 5 seconds after the last one, so bounce and re-trigger guards don't apply. */
    private fun next(): Long { clock += 5000; return clock }

    @Test fun quickPressFiresOnDown() {
        val m = ShortcutMatcher(setOf(five), 0)
        assertEquals(Result.FIRE, m.onDown(five, 0, next()))
        assertEquals(Result.NONE, m.onDown(five, 1, next()))
    }

    @Test fun otherKeysDoNothing() {
        val m = ShortcutMatcher(setOf(five), 0)
        assertEquals(Result.NONE, m.onDown(other, 0, next()))
    }

    @Test fun holdFiresOnlyIfStillHeld() {
        val m = ShortcutMatcher(setOf(five), 500)
        assertEquals(Result.WAIT, m.onDown(five, 0, next()))
        assertTrue(m.holdElapsed(next()))
        assertFalse(m.holdElapsed(next()))
    }

    @Test fun releasingBeforeTheHoldTimeCancels() {
        val m = ShortcutMatcher(setOf(five), 500)
        assertEquals(Result.WAIT, m.onDown(five, 0, next()))
        m.onUp(five, next())
        assertFalse(m.holdElapsed(next()))
    }

    @Test fun combinationFiresWhenTheLastKeyGoesDown() {
        val m = ShortcutMatcher(setOf(star, five), 0)
        assertEquals(Result.NONE, m.onDown(star, 0, next()))
        assertEquals(Result.FIRE, m.onDown(five, 0, next()))
    }

    @Test fun combinationNeedsExactlyTheShortcutKeys() {
        val m = ShortcutMatcher(setOf(star, five), 0)
        m.onDown(other, 0, next())
        m.onDown(star, 0, next())
        assertEquals(Result.NONE, m.onDown(five, 0, next()))
    }

    @Test fun firesAgainOnTheNextPress() {
        val m = ShortcutMatcher(setOf(five), 0)
        assertEquals(Result.FIRE, m.onDown(five, 0, next())); m.onUp(five, next())
        assertEquals(Result.FIRE, m.onDown(five, 0, next()))
    }

    @Test fun letGoOfOneComboKeyBeforeTheHoldCancels() {
        val m = ShortcutMatcher(setOf(star, five), 500)
        m.onDown(star, 0, next())
        assertEquals(Result.WAIT, m.onDown(five, 0, next()))
        m.onUp(star, next())
        assertFalse(m.holdElapsed(next()))
    }

    @Test fun keysAreNotBlockedByDefault() {
        val m = ShortcutMatcher(setOf(five), 0)
        assertFalse(m.blocks(five))
    }

    @Test fun aBlockedOneKeyShortcutHidesOnlyThatKey() {
        val m = ShortcutMatcher(setOf(five), 0, blockKey = true)
        assertTrue(m.blocks(five))
        assertFalse(m.blocks(other))
    }

    @Test fun twoKeyShortcutsNeverBlock() {
        val m = ShortcutMatcher(setOf(star, five), 0, blockKey = true)
        assertFalse(m.blocks(star))
        assertFalse(m.blocks(five))
    }

    @Test fun aSecondPressRightAfterLettingGoIsIgnored() {
        val m = ShortcutMatcher(setOf(five), 0)
        assertEquals(Result.FIRE, m.onDown(five, 0, 10_000))
        m.onUp(five, 10_050)
        assertEquals(Result.NONE, m.onDown(five, 0, 10_100))
        m.onUp(five, 10_150)
    }

    @Test fun aSecondTriggerWithinASecondIsIgnored() {
        val m = ShortcutMatcher(setOf(five), 0)
        assertEquals(Result.FIRE, m.onDown(five, 0, 10_000))
        m.onUp(five, 10_100)
        assertEquals(Result.NONE, m.onDown(five, 0, 10_500))
        m.onUp(five, 10_600)
        assertEquals(Result.FIRE, m.onDown(five, 0, 11_200))
    }
}
