package dev.ellipog.stature.common.mixin.client;

import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;

import dev.ellipog.stature.api.ScaleAxes;

import net.minecraft.client.model.geom.ModelPart;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Scales one model part where its pose is written (plan §14.7).
 *
 * <p>Vanilla applies each part's own scale factors at the tail of this same method, so landing
 * after it composes instead of replacing: pack-defined part scales, baby offsets and anything a
 * future version adds keep working underneath. Armor, elytra and held layers follow for free
 * because they traverse the same nodes, which is exactly why the seam is here rather than in any
 * one renderer.
 *
 * <p>Two scopes feed it. The prepare scope covers the body and everything submitted with a state;
 * the submit scope covers layers that pose parts during {@code submit}, when the prepare scope
 * is already gone -- {@code ItemInHandLayer.translateToHand} and {@code CustomHeadLayer} both
 * pass through here. The prepare scope wins when both are present, which only happens if a
 * submit nests inside a consumption.
 */
@Mixin(ModelPart.class)
public abstract class ModelPartMixin {

    @Inject(method = "translateAndRotate(Lcom/mojang/blaze3d/vertex/PoseStack;)V", at = @At("TAIL"))
    private void stature$scalePart(PoseStack poseStack, CallbackInfo info) {
        Map<ModelPart, ScaleAxes> current = ModelFeatureRendererMixin.stature$currentFactors();
        if (current == null) {
            current = ModelFeatureRendererMixin.stature$submitFactors();
        }
        if (current == null) {
            return;
        }
        ScaleAxes factors = current.get((ModelPart) (Object) this);
        if (factors != null && !factors.isUniform()) {
            poseStack.scale(factors.width(), factors.height(), factors.depth());
        }
    }
}
