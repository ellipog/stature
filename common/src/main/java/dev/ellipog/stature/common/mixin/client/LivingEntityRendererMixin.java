package dev.ellipog.stature.common.mixin.client;

import dev.ellipog.stature.api.ScaleAxes;
import dev.ellipog.stature.common.StatureScale;
import dev.ellipog.stature.common.channel.ChannelService;
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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

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
            // The model channel replaces the attribute scale as the visual base: unset, the
            // override is absent and this is exactly entity.getScale(), so vanilla-sized drawing
            // is bit-for-bit what it was; set, the picture separates from the box on purpose.
            render.stature$pushFrame(entity.tickCount, partialTicks,
                    ChannelService.visualScale(entity, entity.getScale()), scaled.stature$axes());
            render.stature$pushChannels(scaled.stature$channelOverrides());
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
