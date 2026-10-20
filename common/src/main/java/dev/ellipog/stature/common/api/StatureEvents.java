package dev.ellipog.stature.common.api;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import dev.ellipog.stature.api.ScaleState;
import dev.ellipog.stature.common.ScaleService;
import dev.ellipog.stature.common.StatureConfig;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stature's public change notifications (improvement plan M9).
 *
 * <p>Each list is a plain listener list. Register by adding a lambda and unregister by removing the
 * same instance; the lists are {@link CopyOnWriteArrayList}s, so adding from one thread never
 * disturbs an event already being delivered.
 *
 * <p><b>Threads.</b> A listener runs on the thread that committed the change. Server changes commit
 * on the server thread; the client-side copies that arrive in the axes, channel and threshold
 * payloads are applied on the client's main thread and reported there. A listener is never called
 * on a network thread.
 *
 * <p><b>Ordering.</b> Listeners run in registration order, over the snapshot that was current when
 * the event was queued. Adding or removing a listener during delivery takes effect on the next
 * event, not the current one. Independent commits are delivered in commit order. When one operation
 * commits several things (a frame apply, for example), the events are queued together and delivered
 * after the last commit in a stable order. A frame apply or clear reports its size and tier changes
 * before {@code FRAME_CHANGED}, so a listener reacting to the frame sees the size the frame
 * produced.
 *
 * <p><b>Re-entry.</b> A listener may call {@link StatureApi} again. That call commits immediately,
 * because the state is already consistent, but its events are queued behind the event currently
 * being delivered. They run after every listener of that event has finished, so no event delivery
 * is ever nested inside another event's listener loop. The facade's mutators still require the
 * server thread, so re-entry from a server listener is allowed and re-entry from a client listener
 * is refused.
 *
 * <p><b>Failures.</b> A listener that throws is logged and skipped; the remaining listeners still
 * run and the state change is not rolled back. A listener cannot break a command or a tick.
 *
 * <p>Everything in the package is the supported surface. The firing methods on this class are
 * internal: Stature calls them after it has committed the state they describe.
 */
public final class StatureEvents {

    /**
     * Why a change happened.
     *
     * <p>The cause travels unchanged through an ease, so every tick of a size transition reports the
     * same reason.
     */
    public enum Cause {
        /** A {@code /stature} command. */
        COMMAND,
        /** A call through {@link StatureApi}. */
        API,
        /** A named frame loaded from a datapack and applied automatically or on reload. */
        FRAME,
        /** Another mod's integration frame claim. */
        INTEGRATION,
        /** State restored from a save. */
        LOAD,
        /** State re-sent to a viewer that had not received it yet. */
        SYNC,
        /** State received from the server by a client payload. */
        CLIENT
    }

    /** Which side of the micro and macro edges an entity is on. */
    public enum ThresholdTier {
        /** Below the entity's micro edge. */
        MICRO,
        /** Between the two edges. */
        NORMAL,
        /** Above the entity's macro edge. */
        MACRO
    }

    /** A size or shape commit. */
    @FunctionalInterface
    public interface ScaleChangedListener {
        void onScaleChanged(
                LivingEntity entity, ScaleState from, ScaleState to, Cause cause);
    }

    /** A frame apply, replacement or clear. */
    @FunctionalInterface
    public interface FrameChangedListener {
        void onFrameChanged(
                LivingEntity entity,
                @Nullable Identifier from,
                @Nullable Identifier to,
                Cause cause);
    }

    /** A channel override set, reset or eased to a new value. */
    @FunctionalInterface
    public interface ChannelChangedListener {
        void onChannelChanged(
                LivingEntity entity,
                String channelId,
                @Nullable Float from,
                @Nullable Float to,
                Cause cause);
    }

    /** An entity crossing the micro or macro edge. */
    @FunctionalInterface
    public interface ThresholdTierChangedListener {
        void onThresholdTierChanged(
                LivingEntity entity, ThresholdTier from, ThresholdTier to, Cause cause);
    }

    /** Size and shape commits, in commit order. */
    public static final List<ScaleChangedListener> SCALE_CHANGED = new CopyOnWriteArrayList<>();

    /** Frame identity changes, in commit order. */
    public static final List<FrameChangedListener> FRAME_CHANGED = new CopyOnWriteArrayList<>();

    /** Channel override changes, in commit order. */
    public static final List<ChannelChangedListener> CHANNEL_CHANGED = new CopyOnWriteArrayList<>();

    /** Threshold-tier crossings, after the scale or thresholds that caused them are stored. */
    public static final List<ThresholdTierChangedListener> THRESHOLD_TIER_CHANGED =
            new CopyOnWriteArrayList<>();

    private static final Logger LOG = LoggerFactory.getLogger("stature");

    /** Events queued by the current operation, drained when the outermost batch exits. */
    private static final ThreadLocal<ArrayDeque<Runnable>> PENDING =
            ThreadLocal.withInitial(ArrayDeque::new);

    /** How many batches deep this thread is. */
    private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);

    /** Whether this thread is currently inside a listener loop. */
    private static final ThreadLocal<Boolean> DISPATCHING = ThreadLocal.withInitial(() -> false);

    private StatureEvents() {
    }

    /**
     * Runs one operation and delivers everything it queued when the outermost body returns.
     *
     * <p>Internal: Stature's services use this so that a frame apply reports its scale, tier and
     * frame changes together, after all three have been committed, instead of letting a listener
     * observe a half-applied operation. A body that throws still drains what it queued; the
     * exception is the caller's to handle.
     */
    @ApiStatus.Internal
    public static void batch(Runnable body) {
        Objects.requireNonNull(body, "body");
        int depth = DEPTH.get();
        DEPTH.set(depth + 1);
        try {
            body.run();
        } finally {
            DEPTH.set(depth);
            if (depth == 0) {
                DEPTH.remove();
                drainIfIdle();
            }
        }
    }

    /** The tier this entity is on now, using its resolved or configured edges. */
    @ApiStatus.Internal
    public static ThresholdTier tierOf(LivingEntity entity) {
        float scale = ScaleService.stateOf(entity).scale();
        if (scale < StatureConfig.microOf(entity)) {
            return ThresholdTier.MICRO;
        }
        if (scale > StatureConfig.macroOf(entity)) {
            return ThresholdTier.MACRO;
        }
        return ThresholdTier.NORMAL;
    }

    /** Reports a committed size or shape change. */
    @ApiStatus.Internal
    public static void fireScaleChanged(
            LivingEntity entity, ScaleState from, ScaleState to, Cause cause) {
        if (Objects.equals(from, to)) {
            return;
        }
        Cause reason = cause == null ? Cause.API : cause;
        enqueue(() -> deliver("SCALE_CHANGED", SCALE_CHANGED,
                listener -> listener.onScaleChanged(entity, from, to, reason)));
    }

    /** Reports a committed frame identity change. */
    @ApiStatus.Internal
    public static void fireFrameChanged(
            LivingEntity entity,
            @Nullable Identifier from,
            @Nullable Identifier to,
            Cause cause) {
        if (Objects.equals(from, to)) {
            return;
        }
        Cause reason = cause == null ? Cause.API : cause;
        enqueue(() -> deliver("FRAME_CHANGED", FRAME_CHANGED,
                listener -> listener.onFrameChanged(entity, from, to, reason)));
    }

    /** Reports a committed channel override change. */
    @ApiStatus.Internal
    public static void fireChannelChanged(
            LivingEntity entity,
            String channelId,
            @Nullable Float from,
            @Nullable Float to,
            Cause cause) {
        if (Objects.equals(from, to)) {
            return;
        }
        Cause reason = cause == null ? Cause.API : cause;
        enqueue(() -> deliver("CHANNEL_CHANGED", CHANNEL_CHANGED,
                listener -> listener.onChannelChanged(entity, channelId, from, to, reason)));
    }

    /**
     * Reports a tier crossing, given the tier the entity was on before the commit.
     *
     * <p>Internal: callers capture {@link #tierOf} before they change the scale or the resolved
     * thresholds, then call this after. An unchanged tier emits nothing.
     */
    @ApiStatus.Internal
    public static void fireTierChanged(LivingEntity entity, ThresholdTier before, Cause cause) {
        if (before == null) {
            return;
        }
        ThresholdTier after = tierOf(entity);
        if (before == after) {
            return;
        }
        Cause reason = cause == null ? Cause.API : cause;
        enqueue(() -> deliver("THRESHOLD_TIER_CHANGED", THRESHOLD_TIER_CHANGED,
                listener -> listener.onThresholdTierChanged(entity, before, after, reason)));
    }

    /** Queues one event, then drains if this thread is neither batching nor already delivering. */
    private static void enqueue(Runnable event) {
        PENDING.get().add(event);
        drainIfIdle();
    }

    /**
     * Delivers queued events, unless an outer batch or listener loop owns the drain.
     *
     * <p>Listeners queued during delivery simply land at the tail and are picked up by the same
     * loop after the current event finishes. That is what keeps re-entry from nesting.
     */
    private static void drainIfIdle() {
        if (DEPTH.get() > 0 || DISPATCHING.get()) {
            return;
        }
        DISPATCHING.set(true);
        try {
            ArrayDeque<Runnable> queue = PENDING.get();
            Runnable event;
            while ((event = queue.poll()) != null) {
                event.run();
            }
        } finally {
            DISPATCHING.set(false);
            if (PENDING.get().isEmpty()) {
                PENDING.remove();
            }
        }
    }

    /** Calls every listener in order, isolating one listener's failure from the rest. */
    private static <L> void deliver(String name, List<L> listeners, Consumer<L> call) {
        for (L listener : listeners) {
            if (listener == null) {
                LOG.error("Stature {} has a null listener; skipping it", name);
                continue;
            }
            try {
                call.accept(listener);
            } catch (Throwable failure) {
                if (failure instanceof VirtualMachineError fatal) {
                    throw fatal;
                }
                LOG.error("Stature {} listener {} failed; the change stands and the remaining "
                                + "listeners continue",
                        name, listener.getClass().getName(), failure);
            }
        }
    }
}
