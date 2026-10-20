package dev.ellipog.stature.common.mixin.client;

import java.util.Map;

import dev.ellipog.stature.api.ScaleAxes;
import dev.ellipog.stature.common.client.PartPoseScope;
import dev.ellipog.stature.common.client.StatureRenderState;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;

import org.spongepowered.asm.mixin.Mixin;
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
 *
 * <p>Two scopes, because 26.3 poses in two places. {@code prepareModel} runs deferred with the
 * model and its state together, which covers the body and everything submitted with it. Layers
 * like {@code ItemInHandLayer} and {@code CustomHeadLayer} pose parts <em>during</em>
 * {@code submit} instead, when the prepare scope is already gone: {@code LivingEntityRenderer}'s
 * submit hook resolves the same map up front and holds it until {@code RETURN}. The pose hook
 * prefers the prepare scope and falls back to the submit one. {@link PartPoseScope} owns both
 * scopes and the walk; this class only opens and closes them.
 */
@Mixin(ModelFeatureRenderer.class)
public abstract class ModelFeatureRendererMixin {

    @Inject(
            method = "prepareModel(Lnet/minecraft/client/renderer/feature/ModelFeatureRenderer$Submit;)V",
            at = @At("HEAD"))
    private void stature$resolveParts(ModelFeatureRenderer.Submit<?> submit, CallbackInfo info) {
        PartPoseScope.clearCurrent();
        if (!(submit.state() instanceof StatureRenderState render)) {
            return;
        }
        Map<String, ScaleAxes> factors = render.stature$partFactors();
        if (factors.isEmpty()) {
            return;
        }

        Map<ModelPart, ScaleAxes> resolved = PartPoseScope.resolveNodes(
                submit.model().getClass().getSimpleName(), submit.model().root(), factors);
        if (!resolved.isEmpty()) {
            PartPoseScope.setCurrent(resolved);
        }
    }

    @Inject(
            method = "prepareModel(Lnet/minecraft/client/renderer/feature/ModelFeatureRenderer$Submit;)V",
            at = @At("RETURN"))
    private void stature$clearParts(ModelFeatureRenderer.Submit<?> submit, CallbackInfo info) {
        PartPoseScope.clearCurrent();
    }
}
