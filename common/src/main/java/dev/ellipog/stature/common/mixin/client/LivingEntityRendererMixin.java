package dev.ellipog.stature.common.mixin.client;

import dev.ellipog.stature.api.ScaleAxes;
import dev.ellipog.stature.common.StatureScale;
import dev.ellipog.stature.common.client.StatureRenderState;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.LivingEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws a shaped entity shaped (plan §14.2's "render matrices" half).
 *
 * <p>Two injections, because 26.3 splits drawing in two. {@code extractRenderState} is where the
 * entity is still visible, so the tick, the scale and the shape are sampled there; {@code submit}
 * is where the matrix is written, so the blend between the last two samples is applied there:
 *
 * <pre>
 * LivingEntityRenderer.java:86   float scale = state.scale;
 * LivingEntityRenderer.java:87   poseStack.scale(scale, scale, scale);
 * LivingEntityRenderer.java:89   poseStack.scale(-1.0F, -1.0F, 1.0F);
 * </pre>
 *
 * <p>Line 89 is the model's facing flip and must be left alone. The handler tells the two apart by
 * their arguments rather than by order -- the entity scale is uniform and positive, the flip is not
 * -- so anything it does not recognise is passed straight through.
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
            render.stature$pushFrame(entity.tickCount, partialTicks, entity.getScale(), scaled.stature$axes());
        }
    }

    @Redirect(
            method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;scale(FFF)V"))
    private void stature$drawShaped(
            PoseStack poseStack,
            float x,
            float y,
            float z,
            LivingEntityRenderState state,
            PoseStack outerPoseStack,
            SubmitNodeCollector submitNodeCollector,
            CameraRenderState camera) {
        if (x != y || y != z || x <= 0.0F || !(state instanceof StatureRenderState render)) {
            poseStack.scale(x, y, z);
            return;
        }

        float scale = render.stature$renderScale();
        ScaleAxes shape = render.stature$renderShape();
        poseStack.scale(scale * shape.width(), scale * shape.height(), scale * shape.depth());
    }
}
