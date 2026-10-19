package dev.ellipog.stature.common.mixin.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.LivingEntity;

import org.junit.jupiter.api.Test;

/**
 * The client mixins are the one part of Stature no headless run can execute: a dedicated server
 * never loads them, and there is no headless client here. What this file does instead is pin down
 * everything about them that <em>can</em> be checked without rendering -- that every method and
 * every field their descriptors name exists, with the exact signature, in the 26.3 classes they
 * target.
 *
 * <p>If Mojang renames any of these, {@code runClient} would die at class load with a mixin error
 * naming a method that is right there in the wrong version. This test dies at build time instead,
 * which is the same failure one step earlier and much cheaper.
 */
class RenderMixinContractTest {

    @Test
    void extractRenderStateHasTheShapeTheMixinCopiesFrom() throws Exception {
        var method = LivingEntityRenderer.class.getDeclaredMethod(
                "extractRenderState", LivingEntity.class, LivingEntityRenderState.class, float.class);
        assertEquals(void.class, method.getReturnType());
    }

    @Test
    void submitHasTheShapeTheMixinRedirects() throws Exception {
        var method = LivingEntityRenderer.class.getDeclaredMethod(
                "submit",
                LivingEntityRenderState.class,
                PoseStack.class,
                SubmitNodeCollector.class,
                CameraRenderState.class);
        assertEquals(void.class, method.getReturnType());
    }

    @Test
    void poseStackStillScalesThreeFloats() throws Exception {
        var method = PoseStack.class.getDeclaredMethod("scale", float.class, float.class, float.class);
        assertEquals(void.class, method.getReturnType());
    }

    @Test
    void theRenderStateStillCarriesOneUniformScale() throws Exception {
        var field = LivingEntityRenderState.class.getDeclaredField("scale");
        assertEquals(float.class, field.getType());
        assertTrue(java.lang.reflect.Modifier.isPublic(field.getModifiers()));
    }

    @Test
    void visibilityGatesTheDispatcherFunnel() throws Exception {
        var method = net.minecraft.client.renderer.entity.EntityRenderDispatcher.class.getDeclaredMethod(
                "submit",
                net.minecraft.client.renderer.entity.state.EntityRenderState.class,
                CameraRenderState.class,
                double.class,
                double.class,
                double.class,
                PoseStack.class,
                SubmitNodeCollector.class);
        assertEquals(void.class, method.getReturnType());
    }

    @Test
    void shadowBendsAtGetShadowRadius() throws Exception {
        var method = LivingEntityRenderer.class.getDeclaredMethod(
                "getShadowRadius", LivingEntityRenderState.class);
        assertEquals(float.class, method.getReturnType());
    }

    @Test
    void heldItemsSubmitPerArm() throws Exception {
        var method = net.minecraft.client.renderer.entity.layers.ItemInHandLayer.class.getDeclaredMethod(
                "submitArmWithItem",
                net.minecraft.client.renderer.entity.state.ArmedEntityRenderState.class,
                net.minecraft.client.renderer.item.ItemStackRenderState.class,
                net.minecraft.world.item.ItemStack.class,
                net.minecraft.world.entity.HumanoidArm.class,
                PoseStack.class,
                SubmitNodeCollector.class,
                int.class);
        assertEquals(void.class, method.getReturnType());
    }

    @Test
    void partFactorsResolveFromModelRoots() throws Exception {
        var root = net.minecraft.client.model.Model.class.getDeclaredMethod("root");
        assertEquals(net.minecraft.client.model.geom.ModelPart.class, root.getReturnType());
        var child = net.minecraft.client.model.geom.ModelPart.class.getDeclaredMethod(
                "getChild", String.class);
        assertEquals(net.minecraft.client.model.geom.ModelPart.class, child.getReturnType());
    }

    @Test
    void modelConsumptionSeesModelAndStateTogether() throws Exception {
        var method = net.minecraft.client.renderer.feature.ModelFeatureRenderer.class.getDeclaredMethod(
                "prepareModel",
                Class.forName("net.minecraft.client.renderer.feature.ModelFeatureRenderer$Submit"));
        assertEquals(void.class, method.getReturnType());
    }

    @Test
    void posesLandInTranslateAndRotate() throws Exception {
        var method = net.minecraft.client.model.geom.ModelPart.class.getDeclaredMethod(
                "translateAndRotate", PoseStack.class);
        assertEquals(void.class, method.getReturnType());
    }

    @Test
    void firstPersonHandsSubmitArmNodesDirectly() throws Exception {
        var method = net.minecraft.client.renderer.entity.player.AvatarRenderer.class.getDeclaredMethod(
                "renderHand",
                PoseStack.class,
                SubmitNodeCollector.class,
                int.class,
                net.minecraft.resources.Identifier.class,
                net.minecraft.client.model.geom.ModelPart.class,
                boolean.class);
        assertEquals(void.class, method.getReturnType());
    }

    @Test
    void shakingRidesTheWalkBobPass() throws Exception {
        var method = net.minecraft.client.renderer.GameRenderer.class.getDeclaredMethod(
                "bobView",
                CameraRenderState.class,
                PoseStack.class);
        assertEquals(void.class, method.getReturnType());
    }
}
