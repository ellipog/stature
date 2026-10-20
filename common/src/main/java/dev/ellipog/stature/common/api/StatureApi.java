package dev.ellipog.stature.common.api;

import java.util.OptionalDouble;

import dev.ellipog.stature.api.ChannelSpec;
import dev.ellipog.stature.api.Easing;
import dev.ellipog.stature.api.ScaleAxes;
import dev.ellipog.stature.api.ScaleState;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;

/**
 * The supported way for another mod to read and change a living entity's size (improvement plan M9).
 *
 * <p>Everything else in {@code stature-common} is internal Stature machinery. This interface and
 * {@link StatureEvents} are the stable surface: a dependent mod compiles against them, and the
 * implementation delegates to the same services the commands and frames use rather than growing a
 * second size path.
 *
 * <p><b>Threads.</b> {@link #state} and {@link #channelOverride} may be called on either side.
 * Every mutator is server-side and must run on the server thread: the size, the shape, the frame
 * claim and the channel overrides are server-authoritative, and the services make that assumption
 * throughout. A mutator called on a client or off the server thread throws
 * {@link IllegalStateException} rather than changing state that will be overwritten on the next
 * sync.
 *
 * <p><b>Events.</b> State changes made through this facade fire the matching listener in
 * {@link StatureEvents}. A listener runs after the change has been committed, so a read from
 * inside one sees the new value. See {@link StatureEvents} for ordering, re-entry and exception
 * guarantees.
 *
 * <p>The facade is installed with the mod and needs no initialisation call: {@link #get()} returns
 * the same instance for the life of the process.
 */
public interface StatureApi {

    /**
     * The installed facade.
     *
     * <p>Safe to call from a mod constructor on either side. It does not touch a level.
     */
    static StatureApi get() {
        return StatureApiImpl.INSTANCE;
    }

    /**
     * The size this entity is at now, as {@code /stature get} reports it: the base of the game's
     * {@code minecraft:scale} attribute and the entity's box shape.
     *
     * <p>Reads are allowed on either side. The value is the server's truth on the server and the
     * last synced truth on the client.
     */
    ScaleState state(LivingEntity entity);

    /**
     * Sends the entity towards {@code scale} over {@code ticks}, keeping the current shape.
     *
     * <p>{@code ticks <= 0} applies the value now. A finite value outside the game's envelope is
     * clamped to {@code 0.0625..16}; a non-finite value is refused rather than silently turned into
     * a size. The entity is marked as manually chosen, so a later automatic frame assignment leaves
     * it alone.
     *
     * @throws IllegalArgumentException when {@code scale} is not finite
     * @throws IllegalStateException    when called on a client or off the server thread
     */
    void setScale(LivingEntity entity, double scale, int ticks, Easing easing);

    /**
     * Replaces the entity's box shape over {@code ticks}, keeping the uniform scale.
     *
     * <p>The shape is eased linearly. A named frame is cleared rather than edited in place, because
     * reporting a frame whose numbers are no longer on the entity would be a lie. {@code ticks <= 0}
     * applies the shape now.
     *
     * @throws IllegalStateException when called on a client or off the server thread
     */
    void setShape(LivingEntity entity, ScaleAxes axes, int ticks);

    /**
     * The explicit override on one channel, or empty when the channel is following its base or is
     * off.
     *
     * <p>This is the value a caller set, not the channel's resolved value. It is readable on either
     * side.
     *
     * @throws IllegalArgumentException when no channel with that id is declared
     */
    OptionalDouble channelOverride(LivingEntity entity, String channelId);

    /**
     * Sets one channel override over {@code ticks}, eased linearly.
     *
     * <p>{@code ticks <= 0} applies the value now. The value is the channel's absolute effective
     * value for an attribute channel, exactly as {@code /stature channel} defines it. The channel
     * must already be declared, either as a built-in or through {@link #registerChannel}.
     *
     * @throws IllegalArgumentException when the channel is unknown or the value is not finite
     * @throws IllegalStateException    when called on a client or off the server thread
     */
    void setChannel(LivingEntity entity, String channelId, double value, int ticks);

    /**
     * Removes one channel override, returning it to following its base or staying off.
     *
     * @throws IllegalArgumentException when no channel with that id is declared
     * @throws IllegalStateException    when called on a client or off the server thread
     */
    void clearChannel(LivingEntity entity, String channelId);

    /**
     * Claims this entity for an integration frame.
     *
     * <p>An integration frame outranks pack declarations but never outranks an operator's command
     * choice. This is the instant form; the frame is applied with no easing.
     *
     * @return true when the frame exists and the claim was applied, false when no such frame is
     *         loaded or an explicit operator choice owns the entity
     * @throws IllegalStateException when called on a client or off the server thread
     */
    boolean setIntegrationFrame(LivingEntity entity, Identifier frameId);

    /**
     * Withdraws an integration frame claim, returning the entity to vanilla so packs or nothing can
     * take it on the next tick.
     *
     * @return true when this entity was integration-claimed and the claim was released, false when
     *         it was owned by an operator or by nothing
     * @throws IllegalStateException when called on a client or off the server thread
     */
    boolean clearIntegrationFrame(LivingEntity entity);

    /**
     * Declares one channel for every entity in this process.
     *
     * <p>The table is server-side state; call this while the mod is being constructed, before any
     * entity can use the id. Ids outside the {@code stature} namespace are stored as experimental.
     * A duplicate id throws instead of replacing the first declaration: two mods claiming one id is
     * a bug in one of them.
     *
     * @throws IllegalArgumentException when the id is already declared
     */
    void registerChannel(ChannelSpec spec);
}
