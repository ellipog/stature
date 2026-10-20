package dev.ellipog.stature.common;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

class DropScalingTest {

    @Test
    void oneAndBelowLeaveTheCountUntouched() {
        assertEquals(List.of(3), DropScaling.counts(3, 64, 1.0F));
        assertEquals(List.of(3), DropScaling.counts(3, 64, 0.5F));
        assertEquals(List.of(3), DropScaling.counts(3, 64, Float.NaN));
    }

    @Test
    void aCountMultipliesAndSplitsAtTheStackLimit() {
        assertEquals(List.of(64, 32), DropScaling.counts(48, 64, 2.0F));
        assertEquals(List.of(64, 64, 64), DropScaling.counts(64, 64, 3.0F));
    }

    @Test
    void uniqueItemsAreNeverDuplicated() {
        assertEquals(List.of(1), DropScaling.counts(1, 1, 3.0F));
    }

    @Test
    void aFractionRoundsButNeverShrinksVanilla() {
        assertEquals(List.of(4), DropScaling.counts(3, 64, 1.2F));
        assertEquals(List.of(3), DropScaling.counts(3, 64, 1.1F));
        assertEquals(List.of(2), DropScaling.counts(2, 64, 1.0F));
    }

    @Test
    void anEmptyStackStaysEmpty() {
        assertEquals(List.of(0), DropScaling.counts(0, 64, 4.0F));
    }
}
