package dev.ellipog.stature.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AxesTransitionTest {

    private static final float DELTA = 0.0001F;

    private static final ScaleAxes FROM = new ScaleAxes(1.0F, 1.0F, 1.0F);

    private static final ScaleAxes TO = new ScaleAxes(2.0F, 0.5F, 1.0F);

    @Test
    void aZeroTickTransitionIsOverBeforeItStarts() {
        AxesTransition transition = AxesTransition.between(FROM, TO, 0, Easing.ELASTIC);
        assertTrue(transition.isDone());
        assertEquals(TO, transition.advance());
    }

    @Test
    void linearWalksEveryAxis() {
        AxesTransition transition = AxesTransition.between(FROM, TO, 4, Easing.LINEAR);
        assertFalse(transition.isDone());
        assertEquals(new ScaleAxes(1.25F, 0.875F, 1.0F), transition.advance());
        assertEquals(new ScaleAxes(1.5F, 0.75F, 1.0F), transition.advance());
        assertEquals(new ScaleAxes(1.75F, 0.625F, 1.0F), transition.advance());
        assertEquals(TO, transition.advance());
        assertTrue(transition.isDone());
    }

    @Test
    void tickingPastTheEndStaysOnTheTarget() {
        AxesTransition transition = AxesTransition.between(FROM, TO, 1, Easing.LINEAR);
        transition.advance();
        assertEquals(TO, transition.advance());
    }
}
