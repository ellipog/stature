package dev.ellipog.stature.common.mixin.client;

import java.util.Map;

import dev.ellipog.stature.api.ScaleAxes;
import dev.ellipog.stature.common.StatureScale;
import dev.ellipog.stature.common.client.PartPoseScope;
import dev.ellipog.stature.common.client.RenderSampler;
import dev.ellipog.stature.common.client.StatureRenderState;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.LivingEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Draws a shaped entity shaped (plan §14.2's "render matrices" half).
 *
 * <p>Two injections, because 26.3 splits drawing in two. {@code extractRenderState} is where the
 * entity is still visible, so the blended visual scale and shape are sampled there from the
 * entity's {@code RenderSampler}; {@code submit} is where the matrix is written, so the incoming
 * uniform is bent there.
 *
 * <pre>
 * LivingEntityRenderer.java:85   float scale = state.scale;
 * LivingEntityRenderer.java:86   poseStack.scale(scale, scale, scale);
 * LivingEntityRenderer.java:88   poseStack.scale(-1.0F, -1.0F, 1.0F);
 * </pre>
 *
 * <p>The second scale is the model's facing flip and must be left alone. The handler tells the
 * two apart by their arguments rather than by order -- the entity scale is uniform and positive,
 * the flip is not -- so anything it does not recognise is passed straight through. A
 * {@code WrapOperation} rather than a redirect, so another mod wrapping the same call composes
 * instead of conflicting.
 *
 * <p>The incoming uniform is authoritative: the draw is
 * {@code incoming × (visual / attribute) × shape}. Outside the inventory the incoming uniform
 * <em>is</em> the attribute scale, so the ratio cancels and the picture is the blended visual
 * times the shape. The inventory forces the incoming uniform to 1.0, so a 16× player's paperdoll
 * stays 1× and only the shape -- and any {@code stature:model} ratio -- shows.
 *
 * <p>Only the picture is blended. The hitbox lives on the server at whole-tick values, and a
 * render that glides between them while the box steps is exactly vanilla's own split between
 * interpolated positions and exact collision.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {

    @Inject(
            method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
            at = @At("TAIL"))
    private void stature$copyShapeOntoState(
            LivingEntity entity, LivingEntityRenderState state, float partialTicks, CallbackInfo info) {
        if (entity instanceof StatureScale scaled && state instanceof StatureRenderState render) {
            // The model channel replaces the attribute scale as the visual base: unset, the
            // override is absent and this is exactly entity.getScale(), so vanilla-sized drawing
            // is bit-for-bit what it was; set, the picture separates from the box on purpose.
            // The sampler blends the last two client ticks at this frame's partial tick, because
            // the state itself is rebuilt every frame and cannot remember the previous one.
            RenderSampler sampler = scaled.stature$renderSampler();
            render.stature$pushVisual(sampler.scale(partialTicks), entity.getScale(), sampler.axes(partialTicks));
            render.stature$pushChannels(scaled.stature$channelOverrides());
            render.stature$pushParts(scaled.stature$partFactors());
        }
    }

    @WrapOperation(
            method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;scale(FFF)V"))
    private void stature$drawShaped(
            PoseStack instance,
            float x,
            float y,
            float z,
            Operation<Void> original,
            LivingEntityRenderState state,
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            CameraRenderState camera) {
        if (x != y || y != z || x <= 0.0F || !(state instanceof StatureRenderState render)) {
            original.call(instance, x, y, z);
            return;
        }

        float visual = render.stature$visualScale();
        float attribute = render.stature$attributeScale();
        ScaleAxes shape = render.stature$visualShape();
        float ratio = attribute != 0.0F ? visual / attribute : 1.0F;
        if (!Float.isFinite(ratio)) {
            ratio = 1.0F;
        }
        original.call(
                instance, x * ratio * shape.width(), x * ratio * shape.height(), x * ratio * shape.depth());
    }

    /**
     * Opens the submit-phase part scope: layers pose parts during {@code submit}, long after
     * {@code prepareModel} ran, so the node map they need is handed over here and dropped at
     * {@code RETURN}. Empty factors set nothing, keeping the vanilla fast path allocation-free.
     */
    @Inject(
            method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At("HEAD"))
    private void stature$openSubmitScope(
            LivingEntityRenderState state,
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            CameraRenderState camera,
            CallbackInfo info) {
        if (state instanceof StatureRenderState render) {
            Map<String, ScaleAxes> factors = render.stature$partFactors();
            if (!factors.isEmpty()) {
                @SuppressWarnings({"rawtypes", "unchecked"})
                EntityModel<?> model = ((LivingEntityRenderer) (Object) this).getModel();
                Map<ModelPart, ScaleAxes> resolved = PartPoseScope.resolveNodes(
                        model.getClass().getSimpleName(), model.root(), factors);
                if (!resolved.isEmpty()) {
                    PartPoseScope.setSubmit(resolved);
                }
            }
        }
    }

    /** Drops whatever the HEAD hook handed over. Layers never outlive the submit they ran in. */
    @Inject(
            method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At("RETURN"))
    private void stature$closeSubmitScope(
            LivingEntityRenderState state,
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            CameraRenderState camera,
            CallbackInfo info) {
        PartPoseScope.clearSubmit();
    }

    @Inject(
            method = "getShadowRadius(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;)F",
            at = @At("RETURN"),
            cancellable = true)
    private void stature$scaleShadow(
            LivingEntityRenderState state, CallbackInfoReturnable<Float> cir) {
        // The radius already follows the uniform scale through vanilla's own multiply, so this
        // only bends it further when asked. Subclasses that override and call super pass through
        // here on the way, which is what makes one hook cover every living renderer.
        if (state instanceof StatureRenderState render) {
            float factor = render.stature$shadowFactor();
            if (factor != 1.0F) {
                cir.setReturnValue(cir.getReturnValue() * factor);
            }
        }
    }
}
