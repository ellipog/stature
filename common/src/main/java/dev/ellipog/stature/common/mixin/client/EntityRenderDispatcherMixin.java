package dev.ellipog.stature.common.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;

import dev.ellipog.stature.common.client.StatureRenderState;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hides entities the visibility channel claims.
 *
 * <p>One gate at the dispatcher's funnel rather than three scattered ones: cancelling here skips
 * the model, the nameplate and the shadow together, so a scale that should go unseen leaves no
 * floating plate or stray shadow behind. Anything not hidden passes through untouched, and
 * non-living states never implement the interface so they cannot trip the check.
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {

    @Inject(
            method = "submit(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lnet/minecraft/client/renderer/state/level/CameraRenderState;DDDLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;)V",
            at = @At("HEAD"),
            cancellable = true)
    private void stature$skipHidden(
            EntityRenderState renderState,
            CameraRenderState camera,
            double x,
            double y,
            double z,
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            CallbackInfo info) {
        if (renderState instanceof StatureRenderState render && render.stature$hidden()) {
            info.cancel();
        }
    }
}
