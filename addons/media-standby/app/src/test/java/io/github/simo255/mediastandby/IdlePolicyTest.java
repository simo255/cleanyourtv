package io.github.simo255.mediastandby;

import org.junit.Test;
import static org.junit.Assert.*;

public class IdlePolicyTest {
    @Test public void noPlaybackFinishesAfterAttachmentGrace() {
        IdlePolicy policy = new IdlePolicy(1000, 600000);
        assertFalse(policy.shouldFinish(1000, false));
        assertFalse(policy.shouldFinish(2999, false));
        assertTrue(policy.shouldFinish(3000, false));
    }

    @Test public void continuousPlaybackOutlivesInactivityTimeout() {
        IdlePolicy policy = new IdlePolicy(0, 600000);
        for (long now = 0; now < 7200000; now += 1000) {
            assertFalse(policy.shouldFinish(now, true));
        }
    }

    @Test public void pauseWaitsTenMinutesAndResumeResetsSilence() {
        IdlePolicy policy = new IdlePolicy(0, 600000);
        assertFalse(policy.shouldFinish(1000, true));
        assertFalse(policy.shouldFinish(600999, false));
        assertTrue(policy.shouldFinish(601000, false));
        assertFalse(policy.shouldFinish(601001, true));
        assertFalse(policy.shouldFinish(1201000, false));
        assertTrue(policy.shouldFinish(1201001, false));
    }

    @Test public void shortTrackGapDoesNotCauseStandby() {
        IdlePolicy policy = new IdlePolicy(0, 600000);
        assertFalse(policy.shouldFinish(0, true));
        assertFalse(policy.shouldFinish(3000, false));
        assertFalse(policy.shouldFinish(5000, true));
        assertFalse(policy.shouldFinish(604999, false));
    }
}
