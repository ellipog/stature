package dev.ellipog.stature.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ChannelTransitionTest {

    private static final float DELTA = 0.0001F;

    @Test
    void aZeroTickTransitionIsOverBeforeItStarts() {
        ChannelTransition transition = ChannelTransition.between(1.0F, 4.0F, 0, Easing.ELASTIC);
        assertTrue(transition.isDone());
        assertEquals(4.0F, transition.advance(), DELTA);
    }

    @Test
    void linearWalksTheWholeWay() {
        ChannelTransition transition = ChannelTransition.between(1.0F, 3.0F, 4, Easing.LINEAR);
        assertFalse(transition.isDone());
        assertEquals(1.5F, transition.advance(), DELTA);
        assertEquals(2.0F, transition.advance(), DELTA);
        assertEquals(2.5F, transition.advance(), DELTA);
        assertEquals(3.0F, transition.advance(), DELTA);
        assertTrue(transition.isDone());
    }

    @Test
    void tickingPastTheEndStaysOnTheTarget() {
        ChannelTransition transition = ChannelTransition.between(2.0F, 8.0F, 2, Easing.LINEAR);
        transition.advance();
        transition.advance();
        assertEquals(8.0F, transition.advance(), DELTA);
        assertEquals(8.0F, transition.advance(), DELTA);
    }

    @Test
    void aTransitionGoingNowhereIsDoneAtOnce() {
        ChannelTransition transition = ChannelTransition.between(2.0F, 2.0F, 20, Easing.ELASTIC);
        assertTrue(transition.isDone());
        assertEquals(2.0F, transition.advance(), DELTA);
    }
}
