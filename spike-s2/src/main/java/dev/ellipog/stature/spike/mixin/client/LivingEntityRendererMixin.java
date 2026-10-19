package dev.ellipog.stature.spike.mixin.client;

import dev.ellipog.stature.spike.PerAxisDimensions;

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
 * The render half: three factors where vanilla passes one.
 *
 * <p>26.3 renders a state rather than an entity, so this takes two injections. {@code
 * extractRenderState} copies the entity's multipliers onto the state, and {@code submit} uses them
 * where the source has, at lines 86 and 87:
 *
 * <pre>
 * float scale = state.scale;
 * poseStack.scale(scale, scale, scale);
 * </pre>
 *
 * <p>There is a second {@code scale(FFF)} call two lines later, {@code poseStack.scale(-1, -1, 1)},
 * which is the model's facing flip and must not be touched. The handler tells them apart by their
 * arguments rather than by position: the entity scale is uniform and positive, the flip is not.
 * Anything it does not recognise is passed straight through.
 *
 * <p>Not verified by the server run, which cannot reach a client renderer. What proves this one is a
 * client run and a pair of eyes, which is what {@code /s2} is for.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {

    @Inject(
            method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
            at = @At("TAIL"))
    private void stature$copyPerAxisToState(
            LivingEntity entity, LivingEntityRenderState state, float partialTicks, CallbackInfo ci) {
        if (entity instanceof PerAxisDimensions.Size size) {
            float depth = ((PerAxisDimensions) entity).stature$depthMultiplier();
            ((PerAxisDimensions.Render) state)
                    .stature$setRenderScale(size.stature$widthMultiplier(), size.stature$heightMultiplier(), depth);
        }
    }

    @Redirect(
            method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;scale(FFF)V"))
    private void stature$perAxisRenderScale(
            PoseStack poseStack,
            float x,
            float y,
            float z,
            LivingEntityRenderState state,
            PoseStack outerPoseStack,
            SubmitNodeCollector submitNodeCollector,
            CameraRenderState camera) {
        boolean uniformPositive = x == y && y == z && x > 0.0F;

        if (uniformPositive && state instanceof PerAxisDimensions.Render render) {
            poseStack.scale(
                    x * render.stature$renderWidth(),
                    y * render.stature$renderHeight(),
                    z * render.stature$renderDepth());
        } else {
            poseStack.scale(x, y, z);
        }
    }
}
