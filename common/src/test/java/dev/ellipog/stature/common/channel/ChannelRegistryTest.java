package dev.ellipog.stature.common.channel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.ellipog.stature.api.ChannelFollow;
import dev.ellipog.stature.api.ChannelSpec;

import org.junit.jupiter.api.Test;

/**
 * The registry is instance-based precisely so this file can build throwaway tables: nothing here
 * touches the server's singleton, and no test can pollute another through shared state.
 */
class ChannelRegistryTest {

    @Test
    void theBuiltInTableShips() {
        ChannelRegistry registry = new ChannelRegistry();
        for (String id : new String[]{
                "stature:reach_block", "stature:reach_entity", "stature:step_height",
                "stature:motion", "stature:jump", "stature:mining_speed",
                "stature:submerged_mining_speed", "stature:mining_efficiency",
                "stature:attack", "stature:attack_speed", "stature:attack_knockback",
                "stature:sweeping_damage", "stature:defense", "stature:armor_toughness",
                "stature:knockback_resistance", "stature:explosion_knockback_resistance",
                "stature:health", "stature:absorption", "stature:luck", "stature:oxygen_bonus",
                "stature:follow_range", "stature:tempt_range", "stature:movement_efficiency",
                "stature:water_movement_efficiency", "stature:sneaking_speed", "stature:flying_speed",
                "stature:gravity", "stature:safe_fall_distance", "stature:fall_damage",
                "stature:burning_time", "stature:bounciness", "stature:friction", "stature:air_drag",
                "stature:camera_distance", "stature:name_tag_distance", "stature:below_name_distance",
                "stature:model", "stature:saturation",
                "stature:knockback_received", "stature:knockback_dealt", "stature:voice_pitch",
                "stature:visibility", "stature:held_item", "stature:shadow"}) {
            assertTrue(registry.get(id) != null, id);
            assertFalse(registry.get(id).experimental(), id);
        }
        assertEquals(44, registry.size());
    }

    @Test
    void outsideIdsAreExperimentalWhateverWasPassed() {
        ChannelRegistry registry = new ChannelRegistry();
        registry.register(new ChannelSpec("pack:wing_span", ChannelFollow.BASE, 1.0, false));
        assertTrue(registry.get("pack:wing_span").experimental());
    }

    @Test
    void statureIdsKeepTheirFlag() {
        ChannelRegistry registry = new ChannelRegistry();
        registry.register(new ChannelSpec("stature:custom", ChannelFollow.NONE, 1.0, false));
        assertFalse(registry.get("stature:custom").experimental());
    }

    @Test
    void aDuplicateClaimThrowsInsteadOfWinningQuietly() {
        ChannelRegistry registry = new ChannelRegistry();
        registry.register(new ChannelSpec("pack:thing", ChannelFollow.BASE, 1.0, true));
        assertThrows(IllegalArgumentException.class, () ->
                registry.register(new ChannelSpec("pack:thing", ChannelFollow.BASE, 2.0, true)));
    }

    @Test
    void unknownIdsReadNull() {
        assertNull(new ChannelRegistry().get("pack:nothing"));
    }
}
