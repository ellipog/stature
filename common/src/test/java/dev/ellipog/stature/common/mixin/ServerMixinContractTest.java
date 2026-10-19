package dev.ellipog.stature.common.mixin;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

import org.junit.jupiter.api.Test;

/**
 * The server-side twin of the render contract test: every method a server mixin injects into,
 * pinned by descriptor.
 *
 * <p>The mixin config is already {@code required} with {@code defaultRequire}, so a Mojang rename
 * would die loudly at launch; this dies one step earlier, at build time, and names the exact
 * signature that moved. {@code restoreFrom} is the load-bearing one here: it is the only thing
 * that carries an explicit size across death, and a silent miss would mean dying quietly
 * un-sizes you.
 */
class ServerMixinContractTest {

    @Test
    void saveAndLoadAreTheConcreteEntityMethods() throws Exception {
        assertEquals(void.class,
                Entity.class.getDeclaredMethod("saveWithoutId", ValueOutput.class).getReturnType());
        assertEquals(void.class,
                Entity.class.getDeclaredMethod("load", ValueInput.class).getReturnType());
    }

    @Test
    void depthLivesOnThePositionedBoxBuilder() throws Exception {
        assertEquals(net.minecraft.world.phys.AABB.class,
                Entity.class.getDeclaredMethod("makeBoundingBox", Vec3.class).getReturnType());
    }

    @Test
    void dimensionsAndTickAreWhereTheShapeHooksSit() throws Exception {
        assertEquals(net.minecraft.world.entity.EntityDimensions.class,
                LivingEntity.class.getDeclaredMethod("getDimensions", Pose.class).getReturnType());
        assertEquals(void.class, LivingEntity.class.getDeclaredMethod("tick").getReturnType());
    }

    @Test
    void respawnCarriesThroughRestoreFrom() throws Exception {
        assertEquals(void.class, ServerPlayer.class
                .getDeclaredMethod("restoreFrom", ServerPlayer.class, boolean.class).getReturnType());
    }

    @Test
    void knockbackHasOneFunnelEachWay() throws Exception {
        assertEquals(void.class, LivingEntity.class.getDeclaredMethod(
                "knockback", double.class, double.class, double.class,
                DamageSource.class, float.class, boolean.class).getReturnType());
        assertEquals(float.class, LivingEntity.class.getDeclaredMethod(
                "getKnockback", Entity.class, DamageSource.class).getReturnType());
    }

    @Test
    void hungerFunnelsThroughCauseFoodExhaustion() throws Exception {
        assertEquals(void.class, net.minecraft.world.entity.player.Player.class
                .getDeclaredMethod("causeFoodExhaustion", float.class).getReturnType());
    }

    @Test
    void everyProjectileDispatchesThroughOnHit() throws Exception {
        assertEquals(void.class, net.minecraft.world.entity.projectile.Projectile.class
                .getDeclaredMethod("onHit", net.minecraft.world.phys.HitResult.class).getReturnType());
    }

    @Test
    void damageLandsInHurtServerWithDamageThird() throws Exception {
        var method = LivingEntity.class.getDeclaredMethod(
                "hurtServer", net.minecraft.server.level.ServerLevel.class,
                DamageSource.class, float.class);
        assertEquals(boolean.class, method.getReturnType());
        var params = method.getParameterTypes();
        assertEquals(3, params.length);
        assertEquals(float.class, params[2]);
    }

    @Test
    void meleeStartsAtAttack() throws Exception {
        assertEquals(void.class, net.minecraft.world.entity.player.Player.class
                .getDeclaredMethod("attack", Entity.class).getReturnType());
    }

    @Test
    void climbingFlowsThroughOnClimbable() throws Exception {
        assertEquals(boolean.class, LivingEntity.class
                .getDeclaredMethod("onClimbable").getReturnType());
    }
}
