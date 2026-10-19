package dev.ellipog.stature.common.mixin.client;

import java.util.IdentityHashMap;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;

import dev.ellipog.stature.api.ScaleAxes;
import dev.ellipog.stature.common.StatureScale;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Scales first-person arms with their part factors (plan §14.7).
 *
 * <p>Hands bypass model consumption entirely: the renderer submits the arm nodes directly,
 * so there is no model and no state in sight. The local player's own factors fill the
 * gap instead, resolved straight to the two arm nodes without any tree walk. Sleeves ride
 * along as children of the arms. A frame opting out keeps vanilla hands.
 */
@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin {

    @Inject(
            method = "renderHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;Lnet/minecraft/client/model/geom/ModelPart;Z)V",
            at = @At("HEAD"))
    private void stature$scaleFirstPersonArm(
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            int lightCoords,
            Identifier skinTexture,
            ModelPart arm,
            boolean hasSleeve,
            CallbackInfo info) {
        Player player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        StatureScale duck = (StatureScale) player;
        if (duck.stature$firstPersonVanilla()) {
            return;
        }
        Map<String, ScaleAxes> factors = duck.stature$partFactors();
        if (factors.isEmpty()) {
            return;
        }
        @SuppressWarnings("unchecked")
        PlayerModel model =
                ((LivingEntityRenderer<?, ?, PlayerModel>) (Object) this).getModel();
        String path = arm == model.rightArm ? "right_arm"
                : arm == model.leftArm ? "left_arm" : null;
        if (path == null) {
            return;
        }
        ScaleAxes factor = factors.get(path);
        if (factor == null || factor.isUniform()) {
            return;
        }
        Map<ModelPart, ScaleAxes> resolved = new IdentityHashMap<>();
        resolved.put(arm, factor);
        ModelFeatureRendererMixin.stature$setCurrent(resolved);
    }

    @Inject(
            method = "renderHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;Lnet/minecraft/client/model/geom/ModelPart;Z)V",
            at = @At("RETURN"))
    private void stature$clearFirstPersonArm(
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            int lightCoords,
            Identifier skinTexture,
            ModelPart arm,
            boolean hasSleeve,
            CallbackInfo info) {
        ModelFeatureRendererMixin.stature$clearCurrent();
    }
}
