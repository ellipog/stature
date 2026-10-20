package dev.ellipog.stature.common.channel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

import dev.ellipog.stature.api.ChannelSpec;
import dev.ellipog.stature.api.ChannelTransition;
import dev.ellipog.stature.api.Easing;
import dev.ellipog.stature.api.ScaleAxes;
import dev.ellipog.stature.common.ScaleService;
import dev.ellipog.stature.common.StatureScale;
import dev.ellipog.stature.common.StatureConfig;
import dev.ellipog.stature.common.api.StatureEvents;
import dev.ellipog.stature.common.net.StaturePackets;

import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Owns every channel value on every entity (plan §14.9).
 *
 * <p>One rule runs the whole file: an unset channel follows the base scale for free, so storage,
 * packets and ticks only ever touch deliberate choices. An entity with no overrides and no
 * in-flight transitions costs two empty-map checks per tick and nothing else, which is what
 * "costs nothing until it is used" means in code.
 *
 * <p>Effects come in two halves. Attribute channels write {@code default × resolved} through the
 * same missing-attribute skip as the backbone, so a creature without an attribute simply does not
 * get that channel. Visual channels ({@code model} today) have no sim-side effect at all: the
 * render path reads them straight off the entity. Everything here is server-side except the
 * reads; a client that calls a setter is a mod bug, and it fails loudly rather than disagreeing
 * quietly.
 */
public final class ChannelService {

    /** The visual size multiplier, read by the render path. No sim-side effect. */
    public static final String MODEL = "stature:model";

    /** The hunger drain rate multiplier, read by the food exhaustion hook. */
    public static final String SATURATION = "stature:saturation";

    /**
     * Which game attribute each value channel drives. Absent by design for {@code model} (visual
     * only), {@code saturation} (driven through food exhaustion instead), and anything a future
     * layer owns (knockback pair, camera raycast): no entry, no write.
     */
    private static final Map<String, Holder<Attribute>> ATTRIBUTES = Map.ofEntries(
            Map.entry("stature:reach_block", Attributes.BLOCK_INTERACTION_RANGE),
            Map.entry("stature:reach_entity", Attributes.ENTITY_INTERACTION_RANGE),
            Map.entry("stature:step_height", Attributes.STEP_HEIGHT),
            Map.entry("stature:motion", Attributes.MOVEMENT_SPEED),
            Map.entry("stature:jump", Attributes.JUMP_STRENGTH),
            Map.entry("stature:mining_speed", Attributes.BLOCK_BREAK_SPEED),
            Map.entry("stature:submerged_mining_speed", Attributes.SUBMERGED_MINING_SPEED),
            Map.entry("stature:mining_efficiency", Attributes.MINING_EFFICIENCY),
            Map.entry("stature:attack", Attributes.ATTACK_DAMAGE),
            Map.entry("stature:attack_speed", Attributes.ATTACK_SPEED),
            Map.entry("stature:attack_knockback", Attributes.ATTACK_KNOCKBACK),
            Map.entry("stature:sweeping_damage", Attributes.SWEEPING_DAMAGE_RATIO),
            Map.entry("stature:defense", Attributes.ARMOR),
            Map.entry("stature:armor_toughness", Attributes.ARMOR_TOUGHNESS),
            Map.entry("stature:knockback_resistance", Attributes.KNOCKBACK_RESISTANCE),
            Map.entry("stature:explosion_knockback_resistance", Attributes.EXPLOSION_KNOCKBACK_RESISTANCE),
            Map.entry("stature:health", Attributes.MAX_HEALTH),
            Map.entry("stature:absorption", Attributes.MAX_ABSORPTION),
            Map.entry("stature:luck", Attributes.LUCK),
            Map.entry("stature:oxygen_bonus", Attributes.OXYGEN_BONUS),
            Map.entry("stature:follow_range", Attributes.FOLLOW_RANGE),
            Map.entry("stature:tempt_range", Attributes.TEMPT_RANGE),
            Map.entry("stature:movement_efficiency", Attributes.MOVEMENT_EFFICIENCY),
            Map.entry("stature:water_movement_efficiency", Attributes.WATER_MOVEMENT_EFFICIENCY),
            Map.entry("stature:sneaking_speed", Attributes.SNEAKING_SPEED),
            Map.entry("stature:flying_speed", Attributes.FLYING_SPEED),
            Map.entry("stature:gravity", Attributes.GRAVITY),
            Map.entry("stature:safe_fall_distance", Attributes.SAFE_FALL_DISTANCE),
            Map.entry("stature:fall_damage", Attributes.FALL_DAMAGE_MULTIPLIER),
            Map.entry("stature:burning_time", Attributes.BURNING_TIME),
            Map.entry("stature:bounciness", Attributes.BOUNCINESS),
            Map.entry("stature:friction", Attributes.FRICTION_MODIFIER),
            Map.entry("stature:air_drag", Attributes.AIR_DRAG_MODIFIER),
            Map.entry("stature:camera_distance", Attributes.CAMERA_DISTANCE),
            Map.entry("stature:name_tag_distance", Attributes.NAME_TAG_DISTANCE),
            Map.entry("stature:below_name_distance", Attributes.BELOW_NAME_DISTANCE));

    private ChannelService() {
    }

    /**
     * Sets a channel override, eased over {@code ticks}.
     *
     * @throws IllegalArgumentException when no channel with that id is declared
     * @throws IllegalStateException    when called anywhere but the server
     */
    public static void setChannel(LivingEntity entity, String id, float value, int ticks, Easing easing) {
        setChannel(entity, id, value, ticks, easing, StatureEvents.Cause.COMMAND);
    }

    /**
     * Sets a channel override and reports the committed value with its cause.
     *
     * <p>Internal: the command and facade pass their own cause, so a listener can tell an operator
     * action from another mod's call.
     */
    public static void setChannel(
            LivingEntity entity, String id, float value, int ticks, Easing easing,
            StatureEvents.Cause cause) {
        StatureEvents.batch(() -> {
            requireServer(entity);
            ChannelSpec spec = require(id);
            if (!Float.isFinite(value)) {
                throw new IllegalArgumentException(
                        "channel " + id + " needs a finite value, got " + value);
            }

            StatureScale duck = (StatureScale) entity;
            if (MODEL.equals(id)) {
                // A hand-set model is no longer the dangerous set's leftover, so a later plain
                // /stature set must preserve it. The dangerous command sets the flag back to true
                // after this call.
                duck.stature$setDangerousModel(false);
            }
            Float before = duck.stature$channelOverrides().get(id);
            float from = resolvedValue(entity, spec);
            duck.stature$channelOverrides().put(id, value);
            if (ticks <= 0) {
                duck.stature$channelTransitions().remove(id);
                duck.stature$channelCauses().remove(id);
                applyAttributes(entity);
            } else {
                duck.stature$channelTransitions().put(
                        id, ChannelTransition.between(from, value, ticks, easing));
                duck.stature$channelCauses().put(id, cause);
            }
            // An empty map never reaches here (the override was just put), and a reset below sends
            // even an empty one: the receiver replaces its whole map, so an empty send is how a
            // removal travels rather than a packet dropped for having nothing in it.
            StaturePackets.broadcast(entity, syncMap(entity));
            StatureEvents.fireChannelChanged(entity, id, before, value, cause);
        });
    }

    /**
     * Drops a channel override: the channel goes back to following the base scale.
     *
     * @throws IllegalArgumentException when no channel with that id is declared
     * @throws IllegalStateException    when called anywhere but the server
     */
    public static void resetChannel(LivingEntity entity, String id) {
        resetChannel(entity, id, StatureEvents.Cause.COMMAND);
    }

    /** Drops one override and reports the removal, if there was one. */
    public static void resetChannel(LivingEntity entity, String id, StatureEvents.Cause cause) {
        StatureEvents.batch(() -> {
            requireServer(entity);
            require(id);

            StatureScale duck = (StatureScale) entity;
            if (MODEL.equals(id)) {
                duck.stature$setDangerousModel(false);
            }
            Float before = duck.stature$channelOverrides().remove(id);
            duck.stature$channelTransitions().remove(id);
            duck.stature$channelCauses().remove(id);
            applyAttributes(entity);
            StaturePackets.broadcast(entity, syncMap(entity));
            if (before != null) {
                StatureEvents.fireChannelChanged(entity, id, before, null, cause);
            }
        });
    }

    /**
     * Drops every channel override and every in-flight channel change on this entity.
     *
     * <p>Used by {@code /stature reset}. One packet leaves with the empty map, because a receiver
     * replaces its whole map rather than merging: an empty send is how "none left" travels, the
     * same contract {@link #resetChannel} follows.
     */
    public static void clearAll(LivingEntity entity) {
        clearAll(entity, StatureEvents.Cause.COMMAND);
    }

    /**
     * Drops every override and every in-flight channel change, reporting each removal.
     *
     * <p>The receiver replaces its whole map rather than merging, so an empty send is how "none
     * left" travels, the same contract {@link #resetChannel} follows.
     */
    public static void clearAll(LivingEntity entity, StatureEvents.Cause cause) {
        StatureEvents.batch(() -> {
            requireServer(entity);
            StatureScale duck = (StatureScale) entity;
            Map<String, Float> before = Map.copyOf(duck.stature$channelOverrides());
            boolean hadAny = !before.isEmpty() || !duck.stature$channelTransitions().isEmpty();
            duck.stature$channelOverrides().clear();
            duck.stature$channelTransitions().clear();
            duck.stature$channelCauses().clear();
            duck.stature$setDangerousModel(false);
            applyAttributes(entity);
            if (hadAny) {
                StaturePackets.broadcast(entity, Map.of());
            }
            fireChanges(entity, before, Map.of(), cause);
        });
    }

    /**
     * What a channel currently says: its override, else its basis raised to its exponent.
     *
     * @throws IllegalArgumentException when no channel with that id is declared
     */
    public static float resolvedValue(LivingEntity entity, String id) {
        return resolvedValue(entity, require(id));
    }

    /** The visual multiplier the render path draws with: the override, else the base scale. */
    public static float visualScale(LivingEntity entity, float baseScale) {
        Float override = ((StatureScale) entity).stature$channelOverrides().get(MODEL);
        return override != null ? override : baseScale;
    }

    /**
     * The multiplier for knockback this entity takes: the override, else the base scale to the
     * negative root. A giant shrugs, a coblyn flies; an override multiplies either way.
     * The follow is continuous across every scale rather than tier-gated, so there is no
     * cliff at the micro edge; the config flag disables the follow when an operator wants
     * plain vanilla knockback.
     */
    public static float knockbackReceivedFactor(LivingEntity entity) {
        if (!StatureConfig.enabled(entity, StatureConfig.AMPLIFIED_KNOCKBACK)) {
            Float override = ((StatureScale) entity).stature$channelOverrides().get("stature:knockback_received");
            return override != null && override >= 0.0F ? override : 1.0F;
        }
        return channelFactor(entity, "stature:knockback_received");
    }

    /** The multiplier for knockback this entity deals: the override, else the root of the base. */
    public static float knockbackDealtFactor(LivingEntity entity) {
        return channelFactor(entity, "stature:knockback_dealt");
    }

    /**
     * A multiplier channel's number: its override, else its follow, else exactly 1.
     *
     * <p>Used only where the game holds no base to be absolute about -- a knockback power, a
     * hunger amount -- so unlike attribute channels there is nothing to divide by and nothing
     * that can be zero. A corrupt override degrades to neutral rather than poisoning physics
     * with a NaN, for the same reason the packet clamps rather than throws.
     */
    static float channelFactor(LivingEntity entity, String id) {
        StatureScale duck = (StatureScale) entity;
        Float override = duck.stature$channelOverrides().get(id);
        if (override != null) {
            return override >= 0.0F ? override : 1.0F;
        }
        ChannelSpec spec = ChannelRegistry.instance().get(id);
        if (spec == null) {
            return 1.0F;
        }
        return spec.resolve(
                ScaleService.stateOf(entity).scale(), duck.stature$axes(), null).orElse(1.0).floatValue();
    }

    /**
     * The hunger drain multiplier for this entity: the saturation override, else exactly 1.
     *
     * <p>Unlike attribute channels this one multiplies each incoming exhaustion amount rather than
     * an attribute base, because hunger has no base value to be absolute about. Zero drains
     * nothing; a negative or non-finite override is treated as unset rather than trusted, since a
     * NaN here would freeze the hunger bar in a state no eating could fix.
     */
    public static float exhaustionMultiplier(LivingEntity entity) {
        Float override = ((StatureScale) entity).stature$channelOverrides().get(SATURATION);
        return override != null && override >= 0.0F ? override : 1.0F;
    }

    /** Whether this entity carries any channel state worth saving or syncing. */
    public static boolean hasSyncedState(LivingEntity entity) {
        StatureScale duck = (StatureScale) entity;
        return !duck.stature$channelOverrides().isEmpty() || !duck.stature$channelTransitions().isEmpty();
    }

    /** A copy of the overrides, for packets: what is set is what travels, nothing else. */
    public static Map<String, Float> syncMap(LivingEntity entity) {
        return Map.copyOf(((StatureScale) entity).stature$channelOverrides());
    }

    /**
     * Advances in-flight channel changes and applies their effects.
     *
     * <p>Server-only, called from the entity tick hook. Idle entities return after two emptiness
     * checks; attribute writes live here rather than scattered across callers so there is exactly
     * one place that decides what a channel does to the game.
     */
    public static void tick(LivingEntity entity) {
        StatureScale duck = (StatureScale) entity;
        Map<String, ChannelTransition> transitions = duck.stature$channelTransitions();
        if (transitions.isEmpty()) {
            return;
        }

        StatureEvents.batch(() -> {
            List<Change> changes = new ArrayList<>();
            for (Map.Entry<String, ChannelTransition> entry
                    : new LinkedHashMap<>(transitions).entrySet()) {
                String id = entry.getKey();
                Float before = duck.stature$channelOverrides().get(id);
                float value = entry.getValue().advance();
                duck.stature$channelOverrides().put(id, value);
                StatureEvents.Cause cause =
                        duck.stature$channelCauses().getOrDefault(id, StatureEvents.Cause.API);
                changes.add(new Change(id, before, value, cause));
                if (entry.getValue().isDone()) {
                    transitions.remove(id);
                    duck.stature$channelCauses().remove(id);
                }
            }

            if (!changes.isEmpty()) {
                applyAttributes(entity);
                // The client holds a copy of the overrides for what it draws, so an eased value is
                // re-sent every tick it moves -- the same cadence as a shape mid-ease, for the same
                // reason: the far end converges instead of snapping.
                StaturePackets.broadcast(entity, syncMap(entity));
                changes.sort(Comparator.comparing(Change::id));
                for (Change change : changes) {
                    StatureEvents.fireChannelChanged(
                            entity, change.id(), change.from(), change.to(), change.cause());
                }
            }
        });
    }

    /** One channel commit inside a tick, kept until every effect has been applied. */
    private record Change(String id, Float from, float to, StatureEvents.Cause cause) {
    }

    /**
     * Applies every attribute channel as a transient modifier on the entity's own base.
     *
     * <p>Modifiers, never base writes, and the reason is a measured bug: the write used to
     * multiply the attribute's <em>registered</em> default, but a zombie's motion base is 0.23
     * while the registered default is 0.7, so every shaped zombie walked at triple speed. A
     * multiplier modifier composes onto whatever base the entity actually has -- vanilla's,
     * modded, potion-modified -- and removing it restores pristine behavior with nothing stored
     * anywhere. Transient modifiers never reach the save, so there is no stale-modifier state to
     * converge on load; the overrides re-derive everything.
     *
     * <p>Overrides are absolute effective values, exactly as the plan's {@code value} field says:
     * with a nonzero base they become a multiply-total modifier of {@code value / base - 1}, and
     * with a near-zero base (luck, armor, absorption) an add-value modifier of the value itself,
     * because no multiplier can move zero. A desired factor of 1.0 removes the modifier rather
     * than writing a no-op, so untouched entities carry no modifiers at all.
     */
    public static void applyAttributes(LivingEntity entity) {
        float scale = ScaleService.stateOf(entity).scale();
        ScaleAxes axes = ((StatureScale) entity).stature$axes();
        ChannelRegistry registry = ChannelRegistry.instance();
        Map<String, Float> overrides = ((StatureScale) entity).stature$channelOverrides();

        for (Map.Entry<String, Holder<Attribute>> entry : ATTRIBUTES.entrySet()) {
            AttributeInstance instance = entity.getAttribute(entry.getValue());
            if (instance == null) {
                continue;
            }
            ChannelSpec spec = registry.get(entry.getKey());
            if (spec == null) {
                continue;
            }
            Identifier modId = modifierId(entry.getKey());
            Float override = overrides.get(entry.getKey());
            if (override == null) {
                double factor = spec.resolve(scale, axes, null).orElse(1.0);
                setModifier(instance, modId, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, factor - 1.0);
            } else if (!Float.isFinite(override)) {
                continue;
            } else if (Math.abs(instance.getBaseValue()) < 1e-9) {
                setModifier(instance, modId, AttributeModifier.Operation.ADD_VALUE, override);
            } else {
                setModifier(instance, modId, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL,
                        override / instance.getBaseValue() - 1.0);
            }
        }
    }

    private static void setModifier(AttributeInstance instance, Identifier modId,
            AttributeModifier.Operation operation, double amount) {
        if (Math.abs(amount) < 1e-9) {
            instance.removeModifier(modId);
            return;
        }
        AttributeModifier existing = instance.getModifier(modId);
        if (existing != null && existing.operation() == operation && existing.amount() == amount) {
            return;
        }
        if (existing != null) {
            instance.removeModifier(modId);
        }
        instance.addOrUpdateTransientModifier(new AttributeModifier(modId, amount, operation));
    }

    private static final Map<String, Identifier> MODIFIER_IDS = new java.util.concurrent.ConcurrentHashMap<>();

    private static Identifier modifierId(String channelId) {
        return MODIFIER_IDS.computeIfAbsent(channelId, Identifier::parse);
    }

    /**
     * Restores overrides and applies them at once: a load lands, it does not resume easing.
     *
     * @return true when the save named any channels at all, even ones since dropped
     */
    public static boolean load(LivingEntity entity, ValueInput input) {
        StatureScale duck = (StatureScale) entity;
        Map<String, Float> overrides = duck.stature$channelOverrides();
        Map<String, Float> before = Map.copyOf(overrides);
        boolean[] seen = new boolean[1];
        StatureEvents.batch(() -> {
            seen[0] = ChannelSaveCodec.restore(input, overrides);
            if (seen[0] && !entity.level().isClientSide() && !overrides.isEmpty()) {
                applyAttributes(entity);
            }
            fireChanges(entity, before, overrides, StatureEvents.Cause.LOAD);
        });
        return seen[0];
    }

    /**
     * Replaces the client's override map from a server packet and reports every difference.
     *
     * <p>Client-side and called on the client's main thread by both loaders. Replacement rather than
     * merge is the payload's contract: a key missing from the packet was removed on the server.
     */
    public static void applyFromServer(LivingEntity entity, Map<String, Float> channels) {
        StatureEvents.batch(() -> {
            StatureScale duck = (StatureScale) entity;
            Map<String, Float> before = Map.copyOf(duck.stature$channelOverrides());
            duck.stature$channelOverrides().clear();
            duck.stature$channelOverrides().putAll(Objects.requireNonNull(channels, "channels"));
            duck.stature$channelCauses().clear();
            fireChanges(entity, before, duck.stature$channelOverrides(), StatureEvents.Cause.CLIENT);
        });
    }

    /** Reports each id whose override differs between two maps, in id order. */
    private static void fireChanges(
            LivingEntity entity, Map<String, Float> before, Map<String, Float> after,
            StatureEvents.Cause cause) {
        Set<String> ids = new TreeSet<>(before.keySet());
        ids.addAll(after.keySet());
        for (String id : ids) {
            StatureEvents.fireChannelChanged(entity, id, before.get(id), after.get(id), cause);
        }
    }

    /** Saves overrides only: easing progress is transient, targets travel. */
    public static void save(LivingEntity entity, ValueOutput output) {
        ChannelSaveCodec.save(((StatureScale) entity).stature$channelOverrides(), output);
    }

    private static float resolvedValue(LivingEntity entity, ChannelSpec spec) {
        if (StatureConfig.isTunedChannel(spec.id())) {
            return ChannelTuning.factor(entity, spec.id());
        }
        return resolvedValue(entity, spec,
                ScaleService.stateOf(entity).scale(), ((StatureScale) entity).stature$axes());
    }

    private static float resolvedValue(LivingEntity entity, ChannelSpec spec, float scale, ScaleAxes axes) {
        Float override = ((StatureScale) entity).stature$channelOverrides().get(spec.id());
        return spec.resolve(scale, axes, override).orElse(1.0).floatValue();
    }

    private static ChannelSpec require(String id) {
        ChannelSpec spec = ChannelRegistry.instance().get(id);
        if (spec == null) {
            throw new IllegalArgumentException("no channel named " + id + " (declare it first)");
        }
        return spec;
    }

    private static void requireServer(LivingEntity entity) {
        if (entity.level().isClientSide()) {
            throw new IllegalStateException("channel sizes are server-authoritative");
        }
    }
}
