package dev.ellipog.stature.common.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import dev.ellipog.stature.common.client.CameraTrauma;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Shakes the camera with footstep trauma.
 *
 * <p>Rides the walk-bob pass so the existing view-bob toggle governs shaking too: players
 * who turn bobbing off for comfort get no shake either. Two sines keep it rolling rather
 * than vibrating, capped where a giant next door reads as weather rather than damage.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    @Inject(
            method = "bobView(Lnet/minecraft/client/renderer/state/level/CameraRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;)V",
            at = @At("TAIL"))
    private void stature$shakeCamera(CameraRenderState cameraState, PoseStack poseStack, CallbackInfo info) {
        float trauma = CameraTrauma.sample();
        if (trauma < 0.01F) {
            return;
        }
        double time = System.nanoTime() / 1.0E9;
        float roll = (float) (Math.sin(time * 47.0) + Math.sin(time * 31.0 + 1.3)) * 0.5F * trauma * 3.0F;
        float pitch = (float) (Math.sin(time * 39.0 + 0.7) + Math.sin(time * 53.0 + 2.1)) * 0.5F * trauma * 2.0F;
        poseStack.rotateDegrees(Axis.ZP, roll);
        poseStack.rotateDegrees(Axis.XP, pitch);
    }
}
