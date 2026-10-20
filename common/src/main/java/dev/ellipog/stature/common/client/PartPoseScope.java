package dev.ellipog.stature.common.client;

import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

import dev.ellipog.stature.api.ScaleAxes;

import net.minecraft.client.model.geom.ModelPart;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The part-pose scopes, kept out of the mixin that opens them.
 *
 * <p>This was five static helpers on {@code ModelFeatureRendererMixin}, and Mixin refuses a
 * non-private static method on a mixin: the client failed to start with
 * "contains non-private static method stature$currentFactors". The helpers are called from three
 * mixins, so they cannot be private either. A plain class owns the state and the walk, and each
 * mixin asks it, which is also why the walk is testable apart from a transform.
 *
 * <p>These run only on the client: the mixins that call this class are in the client section of
 * {@code stature.mixins.json}, so a dedicated server never loads it.
 */
public final class PartPoseScope {

    private static final Logger LOG = LoggerFactory.getLogger("stature");

    private static final ThreadLocal<Map<ModelPart, ScaleAxes>> CURRENT = new ThreadLocal<>();

    private static final ThreadLocal<Map<ModelPart, ScaleAxes>> SUBMIT = new ThreadLocal<>();

    private static final Set<String> WARNED = new HashSet<>();

    private PartPoseScope() {
    }

    /** The factors for the model currently traversing, or null outside one. */
    public static Map<ModelPart, ScaleAxes> current() {
        return CURRENT.get();
    }

    /** Holds the factors a prepare hook resolved. */
    public static void setCurrent(Map<ModelPart, ScaleAxes> factors) {
        CURRENT.set(factors);
    }

    /** Drops whatever a prepare hook handed over. */
    public static void clearCurrent() {
        CURRENT.remove();
    }

    /** The factors for the submit currently layering, or null outside one. */
    public static Map<ModelPart, ScaleAxes> submit() {
        return SUBMIT.get();
    }

    /** Holds a resolved node map for the layers of the submit now running. */
    public static void setSubmit(Map<ModelPart, ScaleAxes> factors) {
        SUBMIT.set(factors);
    }

    /** Drops whatever a submit handed over. */
    public static void clearSubmit() {
        SUBMIT.remove();
    }

    /**
     * Walks path-keyed factors down from a model root into nodes.
     *
     * <p>Shared by the prepare hook and the submit hook, because both resolve the same map and
     * only differ in where the model comes from. A path the model does not have is warned once and
     * skipped: a quadruped has no arms, and a kin file must not fail over it.
     */
    public static Map<ModelPart, ScaleAxes> resolveNodes(
            String modelName, ModelPart root, Map<String, ScaleAxes> factors) {
        Map<ModelPart, ScaleAxes> resolved = new IdentityHashMap<>();
        for (Map.Entry<String, ScaleAxes> entry : factors.entrySet()) {
            ModelPart node = walk(root, entry.getKey());
            if (node == null) {
                String key = modelName + "/" + entry.getKey();
                if (WARNED.add(key)) {
                    LOG.warn("part path \"{}\" is not on a {}; ignoring it", entry.getKey(), modelName);
                }
                continue;
            }
            resolved.put(node, entry.getValue());
        }
        return resolved;
    }

    private static ModelPart walk(ModelPart root, String path) {
        ModelPart node = root;
        if (!path.isEmpty()) {
            for (String segment : path.split("\\.")) {
                if (!node.hasChild(segment)) {
                    return null;
                }
                node = node.getChild(segment);
            }
        }
        return node;
    }
}
