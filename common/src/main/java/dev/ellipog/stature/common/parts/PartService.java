package dev.ellipog.stature.common.parts;

import java.util.LinkedHashMap;
import java.util.Map;

import dev.ellipog.stature.api.AxesTransition;
import dev.ellipog.stature.api.Easing;
import dev.ellipog.stature.api.ScaleAxes;
import dev.ellipog.stature.common.StatureScale;
import dev.ellipog.stature.common.frame.FrameFile;
import dev.ellipog.stature.common.frame.FrameService;
import dev.ellipog.stature.common.frame.StatureFrames;
import dev.ellipog.stature.common.net.StaturePackets;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Owns per-part silhouette factors (plan §14.7).
 *
 * <p>Factors are keyed by model path, already resolved through a rig, so everything downstream
 * speaks paths and nothing downstream knows groups exist. The render side blends them per frame;
 * the box never reads them except through {@code hitbox_from_parts}, which derives one presence
 * box at frame time. Empty maps cost two emptiness checks per tick and nothing else.
 *
 * <p>Server-side only for writes, like everything else that sizes.
 */
public final class PartService {

    private static final Logger LOG = LoggerFactory.getLogger("stature");

    private static final String KEY_PARTS = "stature_parts";

    /** The rig every entity falls back to: the built-in humanoid, always present. */
    public static final String DEFAULT_RIG = "stature:humanoid";

    private PartService() {
    }

    /**
     * The rig id governing one entity: its frame's, when that rig is still loaded, else the
     * default. A deleted rig file degrades to humanoid rather than stranding the entity.
     */
    public static String rigOf(LivingEntity entity) {
        Identifier frameId = FrameService.frameOf(entity);
        if (frameId != null) {
            FrameFile file = StatureFrames.file(frameId);
            if (file != null && !RigRegistry.instance().groups(file.rig()).isEmpty()) {
                return file.rig();
            }
        }
        return DEFAULT_RIG;
    }

    /**
     * Sets one part's factors, eased over {@code ticks}.
     *
     * @throws IllegalArgumentException for a blank path (factors validate themselves)
     * @throws IllegalStateException    when called anywhere but the server
     */
    public static void setPart(LivingEntity entity, String path, ScaleAxes factors, int ticks, Easing easing) {
        requireServer(entity);
        requirePath(path);

        StatureScale duck = (StatureScale) entity;
        ScaleAxes from = duck.stature$partFactors().getOrDefault(path, ScaleAxes.UNIFORM);
        duck.stature$partFactors().put(path, factors);
        if (ticks <= 0) {
            duck.stature$partTransitions().remove(path);
        } else {
            duck.stature$partTransitions().put(path, AxesTransition.between(from, factors, ticks, easing));
        }
        StaturePackets.broadcastParts(entity, syncMap(entity));
    }

    /** Drops one part's factors: the part draws vanilla again. */
    public static void resetPart(LivingEntity entity, String path) {
        requireServer(entity);

        StatureScale duck = (StatureScale) entity;
        duck.stature$partFactors().remove(path);
        duck.stature$partTransitions().remove(path);
        StaturePackets.broadcastParts(entity, syncMap(entity));
    }

    /** Drops every part factor: the whole silhouette draws vanilla again. */
    public static void resetAllParts(LivingEntity entity) {
        requireServer(entity);

        StatureScale duck = (StatureScale) entity;
        duck.stature$partFactors().clear();
        duck.stature$partTransitions().clear();
        StaturePackets.broadcastParts(entity, syncMap(entity));
    }

    /** Whether this entity carries any part state worth saving or syncing. */
    public static boolean hasParts(LivingEntity entity) {
        StatureScale duck = (StatureScale) entity;
        return !duck.stature$partFactors().isEmpty() || !duck.stature$partTransitions().isEmpty();
    }

    /** A copy of the factors, for packets: what is set is what travels, nothing else. */
    public static Map<String, ScaleAxes> syncMap(LivingEntity entity) {
        return Map.copyOf(((StatureScale) entity).stature$partFactors());
    }

    /** Advances in-flight part changes. Server-only, called from the entity tick hook. */
    public static void tick(LivingEntity entity) {
        StatureScale duck = (StatureScale) entity;
        Map<String, AxesTransition> transitions = duck.stature$partTransitions();
        if (transitions.isEmpty()) {
            return;
        }

        boolean changed = false;
        for (Map.Entry<String, AxesTransition> entry
                : new LinkedHashMap<>(transitions).entrySet()) {
            duck.stature$partFactors().put(entry.getKey(), entry.getValue().advance());
            changed = true;
            if (entry.getValue().isDone()) {
                transitions.remove(entry.getKey());
            }
        }

        if (changed) {
            StaturePackets.broadcastParts(entity, syncMap(entity));
        }
    }

    /**
     * One presence box from a set of part factors: the max per axis, defaulting to uniform.
     *
     * <p>Deliberately approximate. Exact envelopes need posed geometry the server never sees;
     * the plan asks for a larger or smaller presence rather than a differently shaped one, and
     * parts may extend past it either way.
     */
    public static ScaleAxes envelope(Map<String, ScaleAxes> factors) {
        float width = 1.0F;
        float height = 1.0F;
        float depth = 1.0F;
        for (ScaleAxes factorsOf : factors.values()) {
            width = Math.max(width, factorsOf.width());
            height = Math.max(height, factorsOf.height());
            depth = Math.max(depth, factorsOf.depth());
        }
        return new ScaleAxes(width, height, depth);
    }

    /**
     * Restores factors and lands on them: a load lands, it never resumes a glide.
     *
     * <p>Takes the duck rather than the entity: a load touches stored maps only, never the
     * level, which is what makes this honestly unit-testable.
     */
    public static boolean load(StatureScale duck, ValueInput input) {
        boolean[] seen = new boolean[1];

        input.childrenListOrEmpty(KEY_PARTS).forEach(child -> {
            seen[0] = true;
            String path = child.getString("part").orElse(null);
            float x = child.getFloatOr("x", Float.NaN);
            float y = child.getFloatOr("y", Float.NaN);
            float z = child.getFloatOr("z", Float.NaN);
            if (path == null || !RigRegistry.validPath(path)) {
                LOG.warn("ignoring a saved part that names no path; it stays vanilla");
                return;
            }
            try {
                duck.stature$partFactors().put(path, new ScaleAxes(x, y, z));
            } catch (IllegalArgumentException e) {
                LOG.warn("ignoring a saved part size that is not a size ({}); it stays vanilla", e.getMessage());
            }
        });
        return seen[0];
    }

    /** Saves factors only: easing progress is transient, targets travel. */
    public static void save(StatureScale duck, ValueOutput output) {
        Map<String, ScaleAxes> factors = duck.stature$partFactors();
        if (factors.isEmpty()) {
            return;
        }

        ValueOutput.ValueOutputList list = output.childrenList(KEY_PARTS);
        for (Map.Entry<String, ScaleAxes> entry : factors.entrySet()) {
            ValueOutput child = list.addChild();
            child.putString("part", entry.getKey());
            child.putFloat("x", entry.getValue().width());
            child.putFloat("y", entry.getValue().height());
            child.putFloat("z", entry.getValue().depth());
        }
    }

    private static void requirePath(String path) {
        if (path == null || !RigRegistry.validPath(path)) {
            throw new IllegalArgumentException("a part needs a model path, got '" + path + "'");
        }
    }

    private static void requireServer(LivingEntity entity) {
        if (entity.level().isClientSide()) {
            throw new IllegalStateException("part sizes are server-authoritative");
        }
    }
}
