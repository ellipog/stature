package dev.ellipog.stature.common.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import dev.ellipog.stature.api.ChannelFollow;
import dev.ellipog.stature.api.ChannelSpec;

import org.junit.jupiter.api.Test;

/**
 * The game-free half of the facade: it exists, it is one instance, and its surface cannot grow or
 * shrink without a test change (improvement plan M9). The entity-bearing behaviour is covered by
 * the shared gametest.
 */
class StatureApiTest {

    @Test
    void getReturnsTheSameFacadeEveryTime() {
        StatureApi first = StatureApi.get();
        assertNotNull(first);
        assertSame(first, StatureApi.get());
    }

    @Test
    void thePublicSurfaceIsPinned() {
        Set<String> expected = Set.of(
                "get",
                "state",
                "setScale",
                "setShape",
                "channelOverride",
                "setChannel",
                "clearChannel",
                "setIntegrationFrame",
                "clearIntegrationFrame",
                "registerChannel");
        Set<String> actual = Arrays.stream(StatureApi.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .filter(method -> !method.isSynthetic())
                .map(Method::getName)
                .collect(Collectors.toSet());
        assertEquals(expected, actual, "StatureApi's public surface changed");
    }

    @Test
    void registerChannelDelegatesAndRefusesADuplicateId() {
        ChannelSpec duplicate = new ChannelSpec("stature:model", ChannelFollow.BASE, 1.0, false);
        assertThrows(IllegalArgumentException.class,
                () -> StatureApi.get().registerChannel(duplicate),
                "the facade did not reach the channel registry");
    }
}
