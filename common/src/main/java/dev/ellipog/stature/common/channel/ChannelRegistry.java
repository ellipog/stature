package dev.ellipog.stature.common.channel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.ellipog.stature.api.ChannelFollow;
import dev.ellipog.stature.api.ChannelSpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Every channel Stature or another mod declares (plan §14.9).
 *
 * <p>The table in the plan is the built-in set, not a closed one: a mod registers its own spec
 * and Stature stores it, eases it, saves it and syncs it like any other. Anything outside the
 * built-in table ships experimental, which is the plan's rule for staying generous now without
 * freezing a bad idea forever -- and the registry enforces it by marking non-{@code stature}
 * ids experimental no matter what flag the caller passes.
 *
 * <p>Instance-based so tests can build a throwaway registry instead of polluting global state.
 * The server uses {@link #instance()}; everyone agrees on one table because there is one server.
 */
public final class ChannelRegistry {

    private static final Logger LOG = LoggerFactory.getLogger("stature");

    private static final ChannelRegistry INSTANCE = new ChannelRegistry();

    private final Map<String, ChannelSpec> specs = new LinkedHashMap<>();

    /** The shared table, preloaded with the plan's built-in channels. */
    public static ChannelRegistry instance() {
        return INSTANCE;
    }

    public ChannelRegistry() {
        registerBuiltIn(new ChannelSpec("stature:reach_block", ChannelFollow.BASE, 1.0, false));
        registerBuiltIn(new ChannelSpec("stature:reach_entity", ChannelFollow.BASE, 1.0, false));
        registerBuiltIn(new ChannelSpec("stature:step_height", ChannelFollow.BASE, 1.0, false));
        registerBuiltIn(new ChannelSpec("stature:motion", ChannelFollow.BASE, 0.5, false));
        registerBuiltIn(new ChannelSpec("stature:jump", ChannelFollow.BASE, 0.5, false));
        registerBuiltIn(new ChannelSpec("stature:mining_speed", ChannelFollow.BASE, 0.5, false));
        registerBuiltIn(new ChannelSpec("stature:submerged_mining_speed", ChannelFollow.BASE, 0.5, false));
        registerBuiltIn(new ChannelSpec("stature:mining_efficiency", ChannelFollow.BASE, 1.0, false));
        registerBuiltIn(new ChannelSpec("stature:attack", ChannelFollow.BASE, 1.0, false));
        registerBuiltIn(new ChannelSpec("stature:attack_speed", ChannelFollow.BASE, 1.0, false));
        registerBuiltIn(new ChannelSpec("stature:attack_knockback", ChannelFollow.BASE, 1.0, false));
        registerBuiltIn(new ChannelSpec("stature:sweeping_damage", ChannelFollow.BASE, 1.0, false));
        registerBuiltIn(new ChannelSpec("stature:defense", ChannelFollow.BASE, 1.0, false));
        registerBuiltIn(new ChannelSpec("stature:armor_toughness", ChannelFollow.BASE, 1.0, false));
        registerBuiltIn(new ChannelSpec("stature:knockback_resistance", ChannelFollow.BASE, 1.0, false));
        registerBuiltIn(new ChannelSpec("stature:explosion_knockback_resistance", ChannelFollow.BASE, 1.0, false));
        registerBuiltIn(new ChannelSpec("stature:health", ChannelFollow.BASE, 1.0, false));
        registerBuiltIn(new ChannelSpec("stature:absorption", ChannelFollow.BASE, 1.0, false));
        registerBuiltIn(new ChannelSpec("stature:luck", ChannelFollow.BASE, 1.0, false));
        registerBuiltIn(new ChannelSpec("stature:oxygen_bonus", ChannelFollow.BASE, 1.0, false));
        registerBuiltIn(new ChannelSpec("stature:follow_range", ChannelFollow.BASE, 1.0, false));
        registerBuiltIn(new ChannelSpec("stature:tempt_range", ChannelFollow.BASE, 1.0, false));
        registerBuiltIn(new ChannelSpec("stature:movement_efficiency", ChannelFollow.BASE, 1.0, false));
        registerBuiltIn(new ChannelSpec("stature:water_movement_efficiency", ChannelFollow.BASE, 1.0, false));
        registerBuiltIn(new ChannelSpec("stature:sneaking_speed", ChannelFollow.BASE, 1.0, false));
        registerBuiltIn(new ChannelSpec("stature:flying_speed", ChannelFollow.BASE, 1.0, false));
        registerBuiltIn(new ChannelSpec("stature:gravity", ChannelFollow.BASE, 1.0, false));
        registerBuiltIn(new ChannelSpec("stature:safe_fall_distance", ChannelFollow.BASE, 1.0, false));
        registerBuiltIn(new ChannelSpec("stature:fall_damage", ChannelFollow.BASE, 1.0, false));
        registerBuiltIn(new ChannelSpec("stature:burning_time", ChannelFollow.BASE, 1.0, false));
        registerBuiltIn(new ChannelSpec("stature:bounciness", ChannelFollow.BASE, 1.0, false));
        registerBuiltIn(new ChannelSpec("stature:friction", ChannelFollow.BASE, 1.0, false));
        registerBuiltIn(new ChannelSpec("stature:air_drag", ChannelFollow.BASE, 1.0, false));
        registerBuiltIn(new ChannelSpec("stature:camera_distance", ChannelFollow.BASE, 1.0, false));
        registerBuiltIn(new ChannelSpec("stature:name_tag_distance", ChannelFollow.BASE, 1.0, false));
        registerBuiltIn(new ChannelSpec("stature:name_tag_height", ChannelFollow.BASE, 1.0, false));
        registerBuiltIn(new ChannelSpec("stature:model", ChannelFollow.BASE, 1.0, false));
        // No sane default follows anything: hunger drain is per-action, so this one stays off
        // until set rather than deriving a rate from the scale.
        registerBuiltIn(new ChannelSpec("stature:saturation", ChannelFollow.NONE, 1.0, false));
        // Multiplier channels, not attribute channels: knockback power and the voice line have no
        // game base to be absolute about, so their overrides multiply rather than replace. Voice
        // resolves today and drives nothing until a voice mod reads it.
        registerBuiltIn(new ChannelSpec("stature:knockback_received", ChannelFollow.BASE, -0.5, false));
        registerBuiltIn(new ChannelSpec("stature:knockback_dealt", ChannelFollow.BASE, 0.5, false));
        registerBuiltIn(new ChannelSpec("stature:voice_pitch", ChannelFollow.BASE, -0.35, false));
        // Render-only multipliers with no sane follow: each defaults to exactly 1, meaning vanilla
        // behavior, and an override scales just that layer. The shadow already follows the uniform
        // scale on its own; its channel only bends it further.
        registerBuiltIn(new ChannelSpec("stature:visibility", ChannelFollow.NONE, 1.0, false));
        registerBuiltIn(new ChannelSpec("stature:held_item", ChannelFollow.NONE, 1.0, false));
        registerBuiltIn(new ChannelSpec("stature:shadow", ChannelFollow.NONE, 1.0, false));
    }

    private void registerBuiltIn(ChannelSpec spec) {
        this.specs.put(spec.id(), spec);
    }

    /**
     * Declares a channel.
     *
     * <p>Loud on duplicates: two mods claiming one id is a bug in one of them, and the second
     * registration throwing at init beats either behavior silently winning. Ids outside the
     * {@code stature} namespace are always experimental, whatever was passed in.
     */
    public synchronized void register(ChannelSpec spec) {
        String id = spec.id();
        if (this.specs.containsKey(id)) {
            throw new IllegalArgumentException("channel " + id + " is already declared");
        }
        boolean experimental = spec.experimental() || !id.startsWith("stature:");
        ChannelSpec stored = new ChannelSpec(id, spec.follows(), spec.exponent(), experimental);
        this.specs.put(id, stored);
        LOG.info("Channel declared: {} (experimental)", id);
    }

    /** The channel with this id, or null when nobody declared it. */
    public synchronized ChannelSpec get(String id) {
        return this.specs.get(id);
    }

    /** Every declared id, sorted, for tab-completion and {@code channel list}. */
    public synchronized List<String> ids() {
        List<String> ids = new ArrayList<>(this.specs.keySet());
        ids.sort(String::compareTo);
        return ids;
    }

    /** How many channels are declared. */
    public synchronized int size() {
        return this.specs.size();
    }
}
