package dev.ellipog.stature.common;

import dev.ellipog.stature.api.Easing;
import dev.ellipog.stature.api.ScaleAxes;
import dev.ellipog.stature.api.ScaleState;
import dev.ellipog.stature.api.ScaleTransition;
import dev.ellipog.stature.api.ChannelTransition;
import dev.ellipog.stature.api.AxesTransition;
import dev.ellipog.stature.common.api.StatureEvents;
import dev.ellipog.stature.common.client.RenderSampler;
import dev.ellipog.stature.common.parts.HitRegion;
import dev.ellipog.stature.common.frame.FrameSource;

import java.util.Map;

import net.minecraft.resources.Identifier;

import org.jetbrains.annotations.Nullable;

/**
 * What a scaled entity can be asked, and what a third-party mod may hold on to.
 *
 * <p>The {@code stature$} prefix is not decoration: these members are added to a Minecraft class by
 * a mixin, and Mixin refuses a member without the configured prefix. The shape is plan §14.2's
 * {@code StatureEntity} sketch, arriving early because the backbone needs somewhere to keep its
 * transition, its shape and its frame.
 *
 * <p>Everything here is deliberately per-entity rather than global state: a transition belongs to the
 * entity that is scaling, travels with it, and is advanced by that entity's own tick.
 */
public interface StatureScale {

    /** No eye-height override: the camera sits where the shaped dimensions put it. */
    float NO_EYE_HEIGHT = -1.0F;

    /**
     * This entity's live transition, created on first use at whatever size the entity is at.
     *
     * <p>Never null once asked for. A freshly created one is finished and sits at the current size,
     * so "no transition" and "a transition that is over" are the same state rather than two.
     */
    ScaleTransition stature$transition();

    /**
     * Starts a change towards {@code target} over {@code ticks}, easing with {@code easing}.
     *
     * <p>Replaces any transition already running, from wherever the entity is right now rather than
     * from the old transition's start -- so interrupting a change does not jump.
     */
    void stature$setTarget(ScaleState target, int ticks, Easing easing);

    /** The size the entity is at now: the game's attribute for the scale, this duck for the shape. */
    ScaleState stature$currentState();

    /**
     * Why the current (or next) size change was started.
     *
     * <p>Stored on the entity because an ease commits one tick at a time, long after the call that
     * asked for it returned; the cause travels with the transition so every tick reports the same
     * reason to {@link StatureEvents}.
     */
    StatureEvents.Cause stature$scaleCause();

    void stature$setScaleCause(StatureEvents.Cause cause);

    /** The box's shape. {@link ScaleAxes#UNIFORM} when nothing has shaped it. */
    ScaleAxes stature$axes();

    /**
     * Sets the box's shape with no easing and no packet.
     *
     * <p>Called by the service (server side, which then syncs) and by the client receiver (which is
     * the sync). It does not reshape the box -- the caller does that, because a caller that sets
     * three shapes in a row should pay for one rebuild.
     */
    void stature$setAxes(ScaleAxes axes);

    /** The frame this entity was sized by, or null when it was shaped by hand. */
    @Nullable
    Identifier stature$frame();

    void stature$setFrame(@Nullable Identifier frame);

    /** Why the current frame or hand size won. Automatic rules must leave anything but NONE alone. */
    FrameSource stature$frameSource();

    void stature$setFrameSource(FrameSource source);

    /**
     * Set when a load restores a custom size that clients may not know yet.
     *
     * <p>Transient on purpose: it answers "was this just loaded", not "what won", and recomputing
     * it after every load is cheaper than trusting a saved copy. Only players ever read it, because
     * a tracked mob re-syncs through StartTracking while your own player entity never does.
     */
    boolean stature$needsSync();

    void stature$setNeedsSync(boolean needsSync);

    /**
     * This entity's channel overrides, live.
     *
     * <p>Empty for almost every entity, which is the point: an unset channel follows the base
     * scale for free, so only deliberate choices are stored. Read and write these through
     * {@code ChannelService}, not directly -- the service keeps transitions, effects, saves and
     * packets in step with the map, and a hand edit to the map alone updates none of those.
     */
    Map<String, Float> stature$channelOverrides();

    /**
     * This entity's in-flight channel changes, live.
     *
     * <p>Same deal as the overrides: the service owns the lifecycle, ticking these and applying
     * their effects, and a hand edit here bypasses all of that.
     */
    Map<String, ChannelTransition> stature$channelTransitions();

    /**
     * Why each in-flight channel transition was started, by channel id.
     *
     * <p>Only ids with a running ease appear. The same lifecycle rule as the transitions: the
     * service puts an entry when it starts an ease and removes it when the ease finishes or is
     * replaced.
     */
    Map<String, StatureEvents.Cause> stature$channelCauses();

    /**
     * This entity's part factors by model path, live. Empty means a vanilla silhouette; use
     * {@code PartService}, not this map, for the same lifecycle reasons as channels.
     */
    Map<String, ScaleAxes> stature$partFactors();

    /** In-flight part changes, same deal. */
    Map<String, AxesTransition> stature$partTransitions();

    /**
     * Whether this entity's first-person hands stay vanilla. Set from the frame, synced
     * with the parts; the render hook reads it off the local player.
     */
    boolean stature$firstPersonVanilla();

    void stature$setFirstPersonVanilla(boolean vanilla);

    /**
     * Whether this entity's {@code stature:model} channel was written by a dangerous set.
     *
     * <p>Saved with the entity. A plain {@code /stature set} clears the model only when this is
     * true, so a model somebody chose by hand survives a later size change. A dangerous set marks
     * it; a hand channel write clears it.
     */
    boolean stature$dangerousModel();

    void stature$setDangerousModel(boolean dangerousModel);

    /**
     * Whether this transition has already paid its one displacement hit.
     *
     * <p>Displacement runs once, on the final box of a transition. When there is nowhere to move,
     * the entity is hurt once for that transition rather than once per eased tick.
     */
    boolean stature$displacementHurt();

    void stature$setDisplacementHurt(boolean hurt);

    /**
     * This entity's per-frame render history, client-side only.
     *
     * <p>A fresh render state every frame means the state cannot remember the last tick, so
     * the entity remembers it instead: the client tick samples the visual scale and shape
     * here, and {@code extractRenderState} blends them at the frame's partial tick. Never
     * touched on a dedicated server. {@code RenderSampler} is pure math precisely so this
     * interface -- loaded on both sides -- can name it without dragging a client class
     * along.
     */
    RenderSampler stature$renderSampler();

    /**
     * A hit region waiting for damage application, or null. Producers stash it with the current
     * tick; the damage hook consumes it in the same tick and records it as the last hit.
     */
    HitRegion stature$incomingRegion();

    void stature$setIncomingRegion(HitRegion region);

    /** The last region hit on this entity, for downstream consumers. Never saved. */
    HitRegion stature$lastHit();

    void stature$setLastHit(HitRegion hit);

    /** The frame's eye height in blocks at scale 1, or {@link #NO_EYE_HEIGHT}. */
    float stature$eyeHeight();

    void stature$setEyeHeight(float eyeHeight);

    /**
     * This entity's server-resolved threshold tiers and flags, or null.
     *
     * <p>Null means "no frame overrides this entity": the configured values apply and
     * nothing needs sending. Set from the thresholds packet on the client and resolved
     * server-side on change, so movement gating agrees on both sides without the
     * client ever seeing the frame registry.
     */
    @Nullable
    ResolvedThresholds stature$thresholds();

    void stature$setThresholds(@Nullable ResolvedThresholds thresholds);
}
