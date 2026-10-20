package dev.ellipog.stature.common.api;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import dev.ellipog.stature.api.ScaleAxes;
import dev.ellipog.stature.api.ScaleState;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Pins the ordering and failure guarantees {@link StatureEvents} documents (improvement plan M9).
 *
 * <p>The entity in these tests is null because dispatch does not read it: the point here is the
 * listener loop, the queue and the batch, all of which are game-free. The real entity-bearing path
 * is exercised by the shared gametest.
 */
class StatureEventsTest {

    private static final ScaleState ONE = new ScaleState(1.0F, ScaleAxes.UNIFORM);

    private static final ScaleState TWO = new ScaleState(2.0F, ScaleAxes.UNIFORM);

    private static final ScaleState FOUR = new ScaleState(4.0F, ScaleAxes.UNIFORM);

    @AfterEach
    void clearListeners() {
        StatureEvents.SCALE_CHANGED.clear();
    }

    @Test
    void listenersRunInRegistrationOrder() {
        List<String> calls = new ArrayList<>();
        StatureEvents.ScaleChangedListener first = (entity, from, to, cause) -> calls.add("first");
        StatureEvents.ScaleChangedListener second = (entity, from, to, cause) -> calls.add("second");
        StatureEvents.ScaleChangedListener third = (entity, from, to, cause) -> calls.add("third");
        StatureEvents.SCALE_CHANGED.add(first);
        StatureEvents.SCALE_CHANGED.add(second);
        StatureEvents.SCALE_CHANGED.add(third);

        StatureEvents.fireScaleChanged(null, ONE, TWO, StatureEvents.Cause.API);

        assertEquals(List.of("first", "second", "third"), calls);
    }

    @Test
    void batchHoldsEventsUntilTheOutermostBodyReturns() {
        List<String> calls = new ArrayList<>();
        StatureEvents.SCALE_CHANGED.add((entity, from, to, cause) -> calls.add("event"));

        StatureEvents.batch(() -> {
            StatureEvents.fireScaleChanged(null, ONE, TWO, StatureEvents.Cause.API);
            assertTrue(calls.isEmpty(), "an event escaped its batch");
        });

        assertEquals(List.of("event"), calls);
    }

    @Test
    void reentrantCommitIsQueuedBehindTheCurrentListeners() {
        List<String> calls = new ArrayList<>();
        StatureEvents.ScaleChangedListener first = (entity, from, to, cause) -> {
            if (from == ONE) {
                calls.add("outer-first");
                StatureEvents.fireScaleChanged(null, TWO, FOUR, StatureEvents.Cause.API);
            } else {
                calls.add("nested-first");
            }
        };
        StatureEvents.ScaleChangedListener second = (entity, from, to, cause) ->
                calls.add(from == ONE ? "outer-second" : "nested-second");
        StatureEvents.SCALE_CHANGED.add(first);
        StatureEvents.SCALE_CHANGED.add(second);

        StatureEvents.fireScaleChanged(null, ONE, TWO, StatureEvents.Cause.API);

        assertEquals(List.of("outer-first", "outer-second", "nested-first", "nested-second"), calls,
                "a re-entrant commit interleaved with the event that caused it");
    }

    @Test
    void aListenerExceptionDoesNotStopLaterListenersOrTheCaller() {
        List<String> calls = new ArrayList<>();
        StatureEvents.SCALE_CHANGED.add((entity, from, to, cause) -> {
            throw new IllegalStateException("listener failure is part of the test");
        });
        StatureEvents.SCALE_CHANGED.add((entity, from, to, cause) -> calls.add("after"));

        assertDoesNotThrow(() ->
                StatureEvents.fireScaleChanged(null, ONE, TWO, StatureEvents.Cause.API));
        assertEquals(List.of("after"), calls);
    }

    @Test
    void listenerAddedDuringDeliverySeesTheNextEventOnly() {
        List<String> calls = new ArrayList<>();
        StatureEvents.ScaleChangedListener late = (entity, from, to, cause) -> calls.add("late");
        StatureEvents.SCALE_CHANGED.add((entity, from, to, cause) -> {
            calls.add("first");
            StatureEvents.SCALE_CHANGED.add(late);
        });

        StatureEvents.fireScaleChanged(null, ONE, TWO, StatureEvents.Cause.API);
        assertEquals(List.of("first"), calls,
                "a listener added during delivery ran in the same event");

        calls.clear();
        StatureEvents.fireScaleChanged(null, TWO, FOUR, StatureEvents.Cause.API);
        assertEquals(List.of("first", "late"), calls,
                "a listener added during the previous event did not run on the next one");
    }
}
