package dev.ellipog.stature.common.frame;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The precedence rule, pinned where a headless run can reach it.
 *
 * <p>Full assignment needs living entities, which tests do not have -- but the rule itself is two
 * predicates, and these are the two halves an integration depends on: an operator's choice always
 * wins over a mod's claim, and a mod's claim travels through death and save/load exactly like an
 * operator's. If anyone reorders the levels, this fails before a player ever feels it.
 */
class FramePrecedenceTest {

    @Test
    void anOperatorOwnsManualAndCommandAndNothingElse() {
        assertTrue(FrameService.isExplicitlyOwned(FrameSource.MANUAL));
        assertTrue(FrameService.isExplicitlyOwned(FrameSource.COMMAND));

        assertFalse(FrameService.isExplicitlyOwned(FrameSource.NONE));
        assertFalse(FrameService.isExplicitlyOwned(FrameSource.INTEGRATION));
        assertFalse(FrameService.isExplicitlyOwned(FrameSource.TAG));
        assertFalse(FrameService.isExplicitlyOwned(FrameSource.TYPE));
        assertFalse(FrameService.isExplicitlyOwned(FrameSource.DEFAULT));
    }

    @Test
    void explicitChoicesAndIntegrationClaimsTravelWhilePacksReResolve() {
        assertTrue(FrameService.survivesDeath(FrameSource.MANUAL));
        assertTrue(FrameService.survivesDeath(FrameSource.COMMAND));
        assertTrue(FrameService.survivesDeath(FrameSource.INTEGRATION));

        assertFalse(FrameService.survivesDeath(FrameSource.NONE));
        assertFalse(FrameService.survivesDeath(FrameSource.TAG));
        assertFalse(FrameService.survivesDeath(FrameSource.TYPE));
        assertFalse(FrameService.survivesDeath(FrameSource.DEFAULT));
    }
}
