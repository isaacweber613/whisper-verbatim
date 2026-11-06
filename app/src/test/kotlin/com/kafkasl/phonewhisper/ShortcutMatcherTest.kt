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

    @Test fun quickPressFiresOnDown() {
        val m = ShortcutMatcher(setOf(five), 0)
        assertEquals(Result.FIRE, m.onDown(five, 0))
        assertEquals(Result.NONE, m.onDown(five, 1))
    }

    @Test fun otherKeysDoNothing() {
        val m = ShortcutMatcher(setOf(five), 0)
        assertEquals(Result.NONE, m.onDown(other, 0))
    }

    @Test fun holdFiresOnlyIfStillHeld() {
        val m = ShortcutMatcher(setOf(five), 500)
        assertEquals(Result.WAIT, m.onDown(five, 0))
        assertTrue(m.holdElapsed())
        assertFalse(m.holdElapsed())
    }

    @Test fun releasingBeforeTheHoldTimeCancels() {
        val m = ShortcutMatcher(setOf(five), 500)
        assertEquals(Result.WAIT, m.onDown(five, 0))
        m.onUp(five)
        assertFalse(m.holdElapsed())
    }

    @Test fun combinationFiresWhenTheLastKeyGoesDown() {
        val m = ShortcutMatcher(setOf(star, five), 0)
        assertEquals(Result.NONE, m.onDown(star, 0))
        assertEquals(Result.FIRE, m.onDown(five, 0))
    }

    @Test fun combinationNeedsExactlyTheShortcutKeys() {
        val m = ShortcutMatcher(setOf(star, five), 0)
        m.onDown(other, 0)
        m.onDown(star, 0)
        assertEquals(Result.NONE, m.onDown(five, 0))
    }

    @Test fun firesAgainOnTheNextPress() {
        val m = ShortcutMatcher(setOf(five), 0)
        assertEquals(Result.FIRE, m.onDown(five, 0)); m.onUp(five)
        assertEquals(Result.FIRE, m.onDown(five, 0))
    }

    @Test fun letGoOfOneComboKeyBeforeTheHoldCancels() {
        val m = ShortcutMatcher(setOf(star, five), 500)
        m.onDown(star, 0)
        assertEquals(Result.WAIT, m.onDown(five, 0))
        m.onUp(star)
        assertFalse(m.holdElapsed())
    }
}
