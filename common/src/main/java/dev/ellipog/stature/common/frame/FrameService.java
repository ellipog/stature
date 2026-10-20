package dev.ellipog.stature.common.frame;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import dev.ellipog.stature.api.AxesTransition;
import dev.ellipog.stature.api.Easing;
import dev.ellipog.stature.api.FrameSpec;
import dev.ellipog.stature.api.ScaleAxes;
import dev.ellipog.stature.api.ScaleState;
import dev.ellipog.stature.common.ScaleService;
import dev.ellipog.stature.common.StatureConfig;
import dev.ellipog.stature.common.StatureScale;
import dev.ellipog.stature.common.net.StatureNetworking;
import dev.ellipog.stature.common.parts.PartService;
import dev.ellipog.stature.common.parts.RigRegistry;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EntityDimensions;

import org.jetbrains.annotations.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Puts a frame onto an entity and decides which frame wins (plan §14.8).
 *
 * <p>A frame is absolute and the entity is not, so the conversion happens here: the frame's size is
 * turned into multipliers against <em>this</em> entity's own base box, taken from its type rather
 * than from its current dimensions. That is what makes one 0.55 x 1.71 frame mean the same thing on
 * a player, a zombie and a modded creature, and it is also why a frame can be applied to an entity of
 * any size without the pack knowing anything about it.
 *
 * <p>The eye height is remembered on the entity and applied by the dimensions mixin, because there is
 * no attribute for it: it is a field of {@code EntityDimensions}, defaulting to 85% of the height,
 * and a frame that says otherwise is overriding exactly that default.
 *
 * <p>The precedence order is explicit choice, integration claim, pack tag, exact pack type,
 * and the vanilla default. Command, direct API size, and explicit frame choices mark an entity so
 * the automatic rule leaves it alone; integrations sit one level down, outranking packs but never
 * an operator; pack reloads re-evaluate only automatic and not-yet-decided entities.
 */
public final class FrameService {

    private static final Logger LOG = LoggerFactory.getLogger("stature");

    private FrameService() {
    }

    /**
     * Applies a frame, eased over {@code ticks}.
     *
     * @return false when no frame with that name is loaded, so the command can say so rather than
     *         reporting success
     */
    public static boolean apply(LivingEntity entity, Identifier frameId, int ticks) {
        return applyAs(entity, frameId, ticks, FrameSource.COMMAND);
    }

    /**
     * Applies a frame on another mod's behalf, eased over {@code ticks}.
     *
     * <p>This is the general integration path: any mod that sizes entities it does not own calls
     * this instead of reaching for the command path. An integration frame outranks every pack
     * declaration and re-asserts on its owner's schedule -- Stature never re-evaluates it the way
     * it re-evaluates TAG and TYPE -- but it never outranks an operator: with a MANUAL or COMMAND
     * choice on the entity this returns false and changes nothing, and the owner is expected to
     * back off until the operator's choice is cleared.
     *
     * @return false when no frame with that name is loaded, or when an explicit choice is in the
     *         way; true when the frame was applied
     */
    public static boolean setIntegrationFrame(LivingEntity entity, Identifier frameId, int ticks) {
        if (isExplicitlyOwned(((StatureScale) entity).stature$frameSource())) {
            return false;
        }
        return applyAs(entity, frameId, ticks, FrameSource.INTEGRATION);
    }

    /**
     * Withdraws an integration's claim, returning the entity to vanilla so packs or nothing can
     * take it on the next tick.
     *
     * <p>Only an INTEGRATION claim can be withdrawn this way: calling it on an operator-owned
     * entity returns false and changes nothing, for the same reason applying does. Freezing the
     * integration's last size in place would be worse than either alternative, so the box is reset
     * rather than left behind.
     *
     * @return false when this entity was not integration-claimed; true when the claim was released
     */
    public static boolean clearIntegrationFrame(LivingEntity entity) {
        StatureScale duck = (StatureScale) entity;
        if (duck.stature$frameSource() != FrameSource.INTEGRATION) {
            return false;
        }
        duck.stature$setFrame(null);
        duck.stature$setFrameSource(FrameSource.NONE);
        duck.stature$setEyeHeight(StatureScale.NO_EYE_HEIGHT);
        duck.stature$setFirstPersonVanilla(false);
        duck.stature$partFactors().clear();
        duck.stature$partTransitions().clear();
        ScaleService.setSize(entity, ScaleState.UNIFORM, 0, Easing.LINEAR);
        StatureNetworking.broadcastParts(entity, PartService.syncMap(entity));
        StatureConfig.pushThresholds(entity);
        return true;
    }

    /**
     * Whether an operator owns this entity's size right now: a hand size or an applied frame.
     * Integrations must leave these alone; packs never even get asked.
     */
    public static boolean isExplicitlyOwned(FrameSource source) {
        return source == FrameSource.MANUAL || source == FrameSource.COMMAND;
    }

    /**
     * Whether this source travels with the entity through death and save/load alike: explicit
     * choices and integration claims do, pack declarations re-resolve instead.
     */
    public static boolean survivesDeath(FrameSource source) {
        return source == FrameSource.MANUAL || source == FrameSource.COMMAND
                || source == FrameSource.INTEGRATION;
    }

    /** Whether there is anything a client could be wrong about: a non-vanilla size. */
    public static boolean isCustomized(LivingEntity entity) {
        ScaleState state = ScaleService.stateOf(entity);
        return !state.axes().isUniform()
                || state.scale() != 1.0F
                || ((StatureScale) entity).stature$eyeHeight() != StatureScale.NO_EYE_HEIGHT;
    }

    /**
     * Changes only the uniform scale for an explicit caller, keeping the current shape.
     *
     * <p>The shape is kept: "make it twice as big" must not silently make it square again. The entity
     * is marked as manually chosen so a later pack declaration does not override the operator's
     * number.
     */
    public static void setManualScale(LivingEntity entity, float scale, int ticks, Easing easing) {
        ((StatureScale) entity).stature$setFrameSource(FrameSource.MANUAL);
        ScaleService.setSize(entity, ScaleService.stateOf(entity).withScale(scale), ticks, easing);
    }

    /**
     * Changes only the proportions for an explicit caller.
     *
     * <p>A hand-chosen box replaces any named frame rather than editing it in place: reporting a
     * frame whose numbers are no longer on the entity would be worse than reporting no frame.
     */
    public static void setManualShape(LivingEntity entity, ScaleAxes axes, int ticks, Easing easing) {
        StatureScale duck = (StatureScale) entity;
        duck.stature$setFrame(null);
        duck.stature$setEyeHeight(StatureScale.NO_EYE_HEIGHT);
        duck.stature$setFirstPersonVanilla(false);
        duck.stature$setFrameSource(FrameSource.MANUAL);
        ScaleService.setSize(entity, ScaleService.stateOf(entity).withAxes(axes), ticks, easing);
        StatureConfig.pushThresholds(entity);
    }

    /** Forgets the frame and returns the entity to vanilla proportions. */
    public static void clear(LivingEntity entity) {
        StatureScale duck = (StatureScale) entity;
        duck.stature$setFrame(null);
        duck.stature$setFrameSource(FrameSource.MANUAL);
        duck.stature$setEyeHeight(StatureScale.NO_EYE_HEIGHT);
        duck.stature$setFirstPersonVanilla(false);
        duck.stature$partFactors().clear();
        duck.stature$partTransitions().clear();
        ScaleService.setSize(entity, ScaleState.UNIFORM, 0, Easing.LINEAR);
        StatureNetworking.broadcastParts(entity, PartService.syncMap(entity));
        StatureConfig.pushThresholds(entity);
    }

    /** Applies a pack declaration the first time an undecided server-side entity ticks. */
    public static boolean ensureAssigned(LivingEntity entity) {
        if (entity.level().isClientSide()) {
            return false;
        }

        StatureScale duck = (StatureScale) entity;
        FrameSource source = duck.stature$frameSource();
        if (source != FrameSource.NONE) {
            // Anything decided -- explicit, integrated, automatic, or deliberately vanilla -- stays
            // decided. Only unclaimed entities may be assigned automatically.
            return false;
        }

        if (duck.stature$frame() != null || !duck.stature$axes().isUniform()
                || duck.stature$eyeHeight() != StatureScale.NO_EYE_HEIGHT) {
            // Something already chose this entity's size outside the frame system, whether an older
            // save or a direct API call. Do not let an automatic rule stomp it. The flag still
            // needs deriving: this path applies no frame, so nothing else would set it.
            duck.stature$setFrameSource(FrameSource.MANUAL);
            refreshFirstPerson(entity);
            return false;
        }

        Optional<FrameAssignments.Selection> selection = StatureFrames.declaredFor(entity.getType());
        if (selection.isEmpty()) {
            duck.stature$setFrameSource(FrameSource.DEFAULT);
            return false;
        }

        return applyAs(entity, selection.get().frame(), 0, selection.get().source());
    }

    /**
     * Re-reads frames and rigs from these resources, replacing what was there.
     *
     * <p>Shared by {@code /stature reload} and the vanilla {@code /reload} listener, so a
     * datapack edit reaches entities the same way through both. The entity re-evaluation
     * stays with the caller: the command runs it inline, the listener schedules it on
     * the server thread after the barrier.
     *
     * @return how many frames loaded, which is what the command and the log both report
     */
    public static int reloadFrameData(ResourceManager resources) {
        int count = StatureFrames.load(resources);
        RigRegistry.instance().load(resources);
        return count;
    }

    /**
     * Re-evaluates pack declarations after a reload.
     *
     * <p>Explicit operator and API choices are left alone. Automatic frames are reapplied when their
     * winner changes, removed when their declaration disappears, and left untouched when nothing
     * changed.
     *
     * @return how many entities changed
     */
    public static int refreshAutomatic(MinecraftServer server) {
        int updated = 0;
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof LivingEntity living && refreshOne(living)) {
                    updated++;
                }
            }
        }
        return updated;
    }

    /** The frame on this entity, if any. */
    public static @Nullable Identifier frameOf(LivingEntity entity) {
        return ((StatureScale) entity).stature$frame();
    }

    /**
     * Re-derives the first-person flag from the entity's frame.
     *
     * <p>The flag is never saved: the frame is the source of truth, so a load and any
     * path that settles the source without applying a frame re-read it instead of
     * trusting a stored copy. An entity with no frame, or whose frame is gone, reads
     * vanilla hands.
     */
    public static void refreshFirstPerson(LivingEntity entity) {
        StatureScale duck = (StatureScale) entity;
        Identifier frameId = duck.stature$frame();
        boolean vanilla = false;
        if (frameId != null) {
            FrameFile file = StatureFrames.file(frameId);
            vanilla = file != null && file.firstPersonArms() != null && file.firstPersonArms();
        }
        duck.stature$setFirstPersonVanilla(vanilla);
    }

    /** Why the current frame or hand size won. */
    public static FrameSource sourceOf(LivingEntity entity) {
        return ((StatureScale) entity).stature$frameSource();
    }

    private static boolean applyAs(LivingEntity entity, Identifier frameId, int ticks, FrameSource source) {
        FrameFile file = StatureFrames.file(frameId);
        if (file == null) {
            return false;
        }
        FrameSpec frame = file.spec();

        EntityDimensions base = entity.getType().getDimensions();
        Map<String, ScaleAxes> parts = resolveParts(file);
        ScaleAxes axes = file.hitboxFromParts() && !parts.isEmpty()
                ? PartService.envelope(parts)
                : frame.axesFor(base.width(), base.height());

        StatureScale duck = (StatureScale) entity;
        duck.stature$setFrame(frameId);
        duck.stature$setFrameSource(source);
        duck.stature$setEyeHeight(frame.hasEyeHeight() ? frame.eyeHeight() : StatureScale.NO_EYE_HEIGHT);
        duck.stature$setFirstPersonVanilla(file.firstPersonArms() != null && file.firstPersonArms());
        duck.stature$partFactors().clear();
        duck.stature$partFactors().putAll(parts);
        duck.stature$partTransitions().clear();
        if (ticks > 0) {
            for (Map.Entry<String, ScaleAxes> entry : parts.entrySet()) {
                duck.stature$partTransitions().put(entry.getKey(), AxesTransition.between(
                        ScaleAxes.UNIFORM, entry.getValue(), ticks, frame.easing()));
            }
        }

        // The frame's own scale, not the entity's current one: applying a frame is choosing a size,
        // and a caller who wants a different scale can follow it with /stature set.
        ScaleService.setSize(entity, new ScaleState(frame.scale(), axes), ticks, frame.easing());
        StatureNetworking.broadcastParts(entity, PartService.syncMap(entity));
        StatureConfig.pushThresholds(entity);
        return true;
    }

    /**
     * Turns a frame's group-named parts into path-keyed factors.
     *
     * <p>Groups resolve through the frame's rig in sorted order so overlaps are deterministic;
     * unknown groups and bad triplets are warned about and skipped rather than failing the frame.
     * Transitions start from vanilla because a frame owns the whole silhouette: what was there
     * before belonged to whatever is being replaced.
     */
    private static Map<String, ScaleAxes> resolveParts(FrameFile file) {
        Map<String, ScaleAxes> resolved = new LinkedHashMap<>();
        List<String> groups = new ArrayList<>(file.parts().keySet());
        groups.sort(String::compareTo);
        for (String group : groups) {
            List<String> paths = RigRegistry.instance().resolve(file.rig(), group);
            if (paths.isEmpty()) {
                LOG.warn("frame {} names part group \"{}\" nothing resolves; skipping it", file.id(), group);
                continue;
            }
            List<Float> triplet = file.parts().get(group);
            ScaleAxes factors;
            try {
                factors = new ScaleAxes(triplet.get(0), triplet.get(1), triplet.get(2));
            } catch (RuntimeException e) {
                LOG.warn("frame {} part \"{}\" is not a positive size; skipping it", file.id(), group);
                continue;
            }
            for (String path : paths) {
                resolved.put(path, factors);
            }
        }
        return resolved;
    }

    private static boolean refreshOne(LivingEntity entity) {
        StatureScale duck = (StatureScale) entity;
        FrameSource source = duck.stature$frameSource();
        if (isExplicitlyOwned(source) || source == FrameSource.INTEGRATION) {
            // Operator choices are never re-evaluated, and neither are integrations: the owning
            // mod re-asserts on its own schedule, so a reload must not second-guess it.
            return false;
        }

        // A stale custom state without a recorded source is an explicit choice, not an invitation to
        // overwrite it on every reload.
        if ((source == FrameSource.NONE || source == FrameSource.DEFAULT)
                && (duck.stature$frame() != null || !duck.stature$axes().isUniform()
                        || duck.stature$eyeHeight() != StatureScale.NO_EYE_HEIGHT)) {
            duck.stature$setFrameSource(FrameSource.MANUAL);
            return false;
        }

        Optional<FrameAssignments.Selection> selection = StatureFrames.declaredFor(entity.getType());
        if (selection.isEmpty()) {
            if (source == FrameSource.TAG || source == FrameSource.TYPE) {
                resetAutomatic(entity);
                return true;
            }
            duck.stature$setFrameSource(FrameSource.DEFAULT);
            return false;
        }

        FrameAssignments.Selection winner = selection.get();
        if (winner.frame().equals(duck.stature$frame()) && winner.source() == source) {
            return false;
        }
        return applyAs(entity, winner.frame(), 0, winner.source());
    }

    private static void resetAutomatic(LivingEntity entity) {
        StatureScale duck = (StatureScale) entity;
        duck.stature$setFrame(null);
        duck.stature$setFrameSource(FrameSource.DEFAULT);
        duck.stature$setEyeHeight(StatureScale.NO_EYE_HEIGHT);
        duck.stature$setFirstPersonVanilla(false);
        duck.stature$partFactors().clear();
        duck.stature$partTransitions().clear();
        ScaleService.setSize(entity, ScaleState.UNIFORM, 0, Easing.LINEAR);
        StatureNetworking.broadcastParts(entity, PartService.syncMap(entity));
        StatureConfig.pushThresholds(entity);
    }

}
