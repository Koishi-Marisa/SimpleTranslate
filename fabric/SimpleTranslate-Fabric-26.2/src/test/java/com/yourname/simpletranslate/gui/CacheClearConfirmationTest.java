package com.yourname.simpletranslate.gui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Guards the timing rules of the two-step confirmation used when clearing the
 * whole translation cache.
 */
class CacheClearConfirmationTest {
    @Test
    void firstClickArmsAndSecondClickActs() {
        CacheClearConfirmation confirmation = new CacheClearConfirmation();
        long now = 1_000L;

        assertFalse(confirmation.isArmed(now));
        assertTrue(confirmation.armIfDisarmed(now), "the first click only warns");
        assertTrue(confirmation.isArmed(now));
        assertTrue(confirmation.isArmed(now + CacheClearConfirmation.WINDOW_MILLIS));
        assertFalse(confirmation.armIfDisarmed(now + 1_000L), "the second click inside the window acts");
    }

    @Test
    void windowExpiresAndMustBeArmedAgain() {
        CacheClearConfirmation confirmation = new CacheClearConfirmation();
        long now = 5_000L;

        confirmation.armIfDisarmed(now);
        long afterWindow = now + CacheClearConfirmation.WINDOW_MILLIS + 1L;
        assertFalse(confirmation.isArmed(afterWindow));
        assertTrue(confirmation.expireIfElapsed(afterWindow));
        assertFalse(confirmation.expireIfElapsed(afterWindow), "expiry only reports once");
        assertTrue(confirmation.armIfDisarmed(afterWindow), "an expired window must warn again");
    }

    @Test
    void resetDisarmsImmediately() {
        CacheClearConfirmation confirmation = new CacheClearConfirmation();
        long now = 42L;

        confirmation.armIfDisarmed(now);
        confirmation.reset();

        assertFalse(confirmation.isArmed(now));
        assertFalse(confirmation.expireIfElapsed(now));
        assertTrue(confirmation.armIfDisarmed(now));
    }
}
