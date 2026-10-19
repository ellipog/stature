package dev.ellipog.stature.common.mixin.client;

import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

import dev.ellipog.stature.api.ScaleAxes;
import dev.ellipog.stature.common.client.StatureRenderState;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Resolves part factors to model nodes once per submit (plan §14.7's seam).
 *
 * <p>Each consumption sees one model and one state, so this is where the two meet: the state's
 * path-keyed factors are walked down from the model root into an identity map, which the pose
 * hook reads as the traversal passes each node. Nesting composes for free through the matrix
 * stack, and nothing is stored on the parts themselves, so vanilla's own part scales and the
 * next entity's render are never touched.
 */
@Mixin(ModelFeatureRenderer.class)
public abstract class ModelFeatureRendererMixin {

    private static final Logger LOG = LoggerFactory.getLogger("stature");

    private static final ThreadLocal<Map<ModelPart, ScaleAxes>> CURRENT = new ThreadLocal<>();

    private static final Set<String> WARNED = new HashSet<>();

    /** The factors for the model currently traversing, or null outside one. */
    @Unique
    static Map<ModelPart, ScaleAxes> stature$currentFactors() {
        return CURRENT.get();
    }

    /** Hands a pre-resolved node map to the pose hook. First-person rendering only. */
    @Unique
    static void stature$setCurrent(Map<ModelPart, ScaleAxes> factors) {
        CURRENT.set(factors);
    }

    /** Drops whatever a render handed over. First-person rendering only. */
    @Unique
    static void stature$clearCurrent() {
        CURRENT.remove();
    }

    @Inject(
            method = "prepareModel(Lnet/minecraft/client/renderer/feature/ModelFeatureRenderer$Submit;)V",
            at = @At("HEAD"))
    private void stature$resolveParts(ModelFeatureRenderer.Submit<?> submit, CallbackInfo info) {
        CURRENT.remove();
        if (!(submit.state() instanceof StatureRenderState render)) {
            return;
        }
        Map<String, ScaleAxes> factors = render.stature$partFactors();
        if (factors.isEmpty()) {
            return;
        }

        Map<ModelPart, ScaleAxes> resolved = new IdentityHashMap<>();
        String modelName = submit.model().getClass().getSimpleName();
        for (Map.Entry<String, ScaleAxes> entry : factors.entrySet()) {
            ModelPart node = walk(submit.model().root(), entry.getKey());
            if (node == null) {
                String key = modelName + "/" + entry.getKey();
                if (WARNED.add(key)) {
                    LOG.warn("part path \"{}\" is not on a {}; ignoring it", entry.getKey(), modelName);
                }
                continue;
            }
            resolved.put(node, entry.getValue());
        }
        if (!resolved.isEmpty()) {
            CURRENT.set(resolved);
        }
    }

    @Inject(
            method = "prepareModel(Lnet/minecraft/client/renderer/feature/ModelFeatureRenderer$Submit;)V",
            at = @At("RETURN"))
    private void stature$clearParts(ModelFeatureRenderer.Submit<?> submit, CallbackInfo info) {
        CURRENT.remove();
    }

    @Unique
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
