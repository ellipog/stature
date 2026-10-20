package dev.ellipog.stature.common.channel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import dev.ellipog.stature.api.ChannelFollow;
import dev.ellipog.stature.api.ChannelSpec;

import org.junit.jupiter.api.Test;

/**
 * Pins every built-in channel's default follow and exponent (plan M1).
 *
 * <p>A future edit to the defaults is a conscious test change: the table below names all 43
 * built-ins explicitly, so adding, removing or retuning a channel fails here first. Body
 * physics follows the base with a tuned exponent; combat, defence, health, AI ranges and
 * physics constants are override-only (Pehkui parity); the camera and name-tag channels stay
 * off because vanilla already scales them.
 */
class ChannelDefaultsTest {

    /** One row of the pinned table: id, follow, exponent. */
    private record Row(String id, ChannelFollow follows, double exponent) {
    }

    private static final Row[] TABLE = {
            new Row("stature:reach_block", ChannelFollow.BASE, 1.0),
            new Row("stature:reach_entity", ChannelFollow.BASE, 1.0),
            new Row("stature:step_height", ChannelFollow.BASE, 1.0),
            new Row("stature:motion", ChannelFollow.BASE, 0.5),
            new Row("stature:jump", ChannelFollow.BASE, 0.5),
            new Row("stature:mining_speed", ChannelFollow.NONE, 1.0),
            new Row("stature:submerged_mining_speed", ChannelFollow.NONE, 1.0),
            new Row("stature:mining_efficiency", ChannelFollow.NONE, 1.0),
            new Row("stature:attack", ChannelFollow.NONE, 1.0),
            new Row("stature:attack_speed", ChannelFollow.NONE, 1.0),
            new Row("stature:attack_knockback", ChannelFollow.NONE, 1.0),
            new Row("stature:sweeping_damage", ChannelFollow.NONE, 1.0),
            new Row("stature:defense", ChannelFollow.NONE, 1.0),
            new Row("stature:armor_toughness", ChannelFollow.NONE, 1.0),
            new Row("stature:knockback_resistance", ChannelFollow.NONE, 1.0),
            new Row("stature:explosion_knockback_resistance", ChannelFollow.NONE, 1.0),
            new Row("stature:health", ChannelFollow.NONE, 1.0),
            new Row("stature:absorption", ChannelFollow.NONE, 1.0),
            new Row("stature:luck", ChannelFollow.NONE, 1.0),
            new Row("stature:oxygen_bonus", ChannelFollow.NONE, 1.0),
            new Row("stature:follow_range", ChannelFollow.NONE, 1.0),
            new Row("stature:tempt_range", ChannelFollow.NONE, 1.0),
            new Row("stature:movement_efficiency", ChannelFollow.NONE, 1.0),
            new Row("stature:water_movement_efficiency", ChannelFollow.NONE, 1.0),
            new Row("stature:sneaking_speed", ChannelFollow.NONE, 1.0),
            new Row("stature:flying_speed", ChannelFollow.BASE, 0.5),
            new Row("stature:gravity", ChannelFollow.NONE, 1.0),
            new Row("stature:safe_fall_distance", ChannelFollow.BASE, 1.0),
            new Row("stature:fall_damage", ChannelFollow.NONE, 1.0),
            new Row("stature:burning_time", ChannelFollow.NONE, 1.0),
            new Row("stature:bounciness", ChannelFollow.NONE, 1.0),
            new Row("stature:friction", ChannelFollow.NONE, 1.0),
            new Row("stature:air_drag", ChannelFollow.NONE, 1.0),
            new Row("stature:camera_distance", ChannelFollow.NONE, 1.0),
            new Row("stature:name_tag_distance", ChannelFollow.NONE, 1.0),
            new Row("stature:below_name_distance", ChannelFollow.NONE, 1.0),
            new Row("stature:model", ChannelFollow.BASE, 1.0),
            new Row("stature:saturation", ChannelFollow.NONE, 1.0),
            new Row("stature:knockback_received", ChannelFollow.BASE, -0.5),
            new Row("stature:knockback_dealt", ChannelFollow.BASE, 0.5),
            new Row("stature:visibility", ChannelFollow.NONE, 1.0),
            new Row("stature:held_item", ChannelFollow.NONE, 1.0),
            new Row("stature:shadow", ChannelFollow.NONE, 1.0),
            new Row("stature:projectile_scale", ChannelFollow.BASE, 1.0),
            new Row("stature:item_pickup_range", ChannelFollow.BASE, 1.0),
            new Row("stature:riding_offset", ChannelFollow.BASE, 1.0),
            new Row("stature:explosion_scale", ChannelFollow.BASE, 0.5),
            new Row("stature:drops_scale", ChannelFollow.BASE, 0.5),
    };

    @Test
    void everyBuiltInKeepsItsPinnedDefault() {
        ChannelRegistry registry = new ChannelRegistry();
        assertEquals(TABLE.length, registry.size(), "built-in channel count moved");
        for (Row row : TABLE) {
            ChannelSpec spec = registry.get(row.id());
            assertNotNull(spec, row.id());
            assertFalse(spec.experimental(), row.id());
            assertEquals(row.follows(), spec.follows(), row.id());
            assertEquals(row.exponent(), spec.exponent(), 1e-9, row.id());
        }
    }
}
