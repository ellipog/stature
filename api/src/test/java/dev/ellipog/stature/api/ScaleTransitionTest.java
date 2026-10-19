package dev.ellipog.stature.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ScaleTransitionTest {

    private static final double DELTA = 1.0E-5;

    private static ScaleState at(float scale) {
        return ScaleState.of(scale);
    }

    @Test
    void aZeroTickTransitionIsOverBeforeItStarts() {
        ScaleTransition transition = ScaleTransition.between(at(1.0F), at(3.0F), 0, Easing.LINEAR);
        assertTrue(transition.isDone());
        assertEquals(3.0F, transition.state().scale(), DELTA);
    }

    @Test
    void linearWalksTheWholeWay() {
        ScaleTransition transition = ScaleTransition.between(at(1.0F), at(3.0F), 4, Easing.LINEAR);
        assertEquals(1.0F, transition.state().scale(), DELTA);
        assertEquals(1.5F, transition.advance().scale(), DELTA);
        assertEquals(2.0F, transition.advance().scale(), DELTA);
        assertEquals(2.5F, transition.advance().scale(), DELTA);
        assertEquals(3.0F, transition.advance().scale(), DELTA);
        assertTrue(transition.isDone());
    }

    @Test
    void tickingPastTheEndStaysOnTheTarget() {
        ScaleTransition transition = ScaleTransition.between(at(1.0F), at(3.0F), 2, Easing.LINEAR);
        transition.advance();
        transition.advance();
        for (int i = 0; i < 10; i++) {
            assertEquals(3.0F, transition.advance().scale(), DELTA);
        }
    }

    @Test
    void theCurveIsTheEasingOne() {
        ScaleTransition eased = ScaleTransition.between(
                ScaleState.of(ScaleLimits.MIN), at(1.0F), 2, Easing.EASE_OUT_CUBIC);
        // E(0.5) is 0.875, so half way through a two-tick cubic is already most of the way there.
        // The lower bound of the envelope is where the change starts, since 0.0 is not a legal scale.
        assertEquals(ScaleLimits.MIN + (1.0F - ScaleLimits.MIN) * 0.875F, eased.advance().scale(), DELTA);
    }

    @Test
    void theShapeEasesWithTheScale() {
        ScaleTransition transition = ScaleTransition.between(
                ScaleState.of(1.0F),
                ScaleState.of(1.0F).withAxes(new ScaleAxes(1.0F, 2.0F, 1.0F)),
                2,
                Easing.LINEAR);
        assertEquals(1.5F, transition.advance().axes().height(), DELTA);
        assertEquals(1.0F, transition.state().axes().width(), DELTA);
    }

    @Test
    void aTransitionThatIsGoingNowhereIsDoneAtOnce() {
        ScaleTransition transition = ScaleTransition.between(at(2.0F), at(2.0F), 20, Easing.ELASTIC);
        assertTrue(transition.isDone());
        assertEquals(2.0F, transition.state().scale(), DELTA);
    }

    @Test
    void atIsAnInstantState() {
        ScaleTransition transition = ScaleTransition.at(at(4.0F));
        assertTrue(transition.isDone());
        assertEquals(4.0F, transition.state().scale(), DELTA);
        assertFalse(ScaleTransition.between(at(1.0F), at(2.0F), 5, Easing.LINEAR).isDone());
    }
}
