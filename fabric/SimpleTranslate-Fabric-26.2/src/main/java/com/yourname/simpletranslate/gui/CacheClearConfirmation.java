package com.yourname.simpletranslate.gui;

/**
 * Expiring two-step confirmation shared by destructive cache actions.
 *
 * <p>The first click arms the action for {@link #WINDOW_MILLIS}; a second click
 * inside that window performs it, and an expired window silently disarms. The
 * class deliberately holds no client state so the timing rules can be covered
 * by a plain unit test.</p>
 */
final class CacheClearConfirmation {
    static final long WINDOW_MILLIS = 5000L;

    private long armedUntil;

    /** @return true while a previous click still confirms the action. */
    boolean isArmed(long now) {
        return armedUntil != 0L && now <= armedUntil;
    }

    /**
     * Arms the confirmation when it is not already armed.
     *
     * @return true when this call armed the window (the click only warned), or
     *         false when the window was already armed (the click must act).
     */
    boolean armIfDisarmed(long now) {
        if (isArmed(now)) {
            return false;
        }
        armedUntil = now + WINDOW_MILLIS;
        return true;
    }

    /** @return true when an armed window has just expired and was disarmed. */
    boolean expireIfElapsed(long now) {
        if (armedUntil != 0L && now > armedUntil) {
            armedUntil = 0L;
            return true;
        }
        return false;
    }

    void reset() {
        armedUntil = 0L;
    }
}
