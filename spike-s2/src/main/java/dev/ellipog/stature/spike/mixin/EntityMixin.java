package dev.ellipog.stature.spike.mixin;

import dev.ellipog.stature.spike.PerAxisDimensions;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Gives every entity a depth multiplier, and builds the box from it.
 *
 * <p>The injection point is the {@code Vec3} overload, not the no-arg method:
 *
 * <pre>
 * Entity.java:491   protected final AABB makeBoundingBox() {
 * Entity.java:492       return this.makeBoundingBox(this.position);
 * Entity.java:495   protected AABB makeBoundingBox(Vec3 position) {
 * Entity.java:496       return this.dimensions.makeBoundingBox(position);
 * </pre>
 *
 * <p>The no-arg one is {@code final} in 26.3, and the one that takes a position has the entity in
 * hand, which is why the depth lives on the entity rather than in the dimensions: {@code
 * EntityDimensions.makeBoundingBox(x, y, z)} cannot tell which entity it is for.
 *
 * <p>No {@code @Shadow} and no field initialiser, both deliberately. The mixin casts {@code this} to
 * {@code Entity} for the calls it needs, which keeps it out of the target's member list entirely; and
 * the sentinel for "no depth set" is {@code 0.0F} rather than a default of {@code 1.0F}, because a
 * unique field with an initialiser is merged into constructors and {@code makeBoundingBox} runs from
 * {@code setPos} during construction.
 */
@Mixin(Entity.class)
public abstract class EntityMixin implements PerAxisDimensions {

    @Unique
    private float stature$depthMultiplier;

    @Override
    public float stature$depthMultiplier() {
        return this.stature$depthMultiplier > 0.0F ? this.stature$depthMultiplier : 1.0F;
    }

    @Override
    public void stature$setDepthMultiplier(float multiplier) {
        this.stature$depthMultiplier = multiplier;
        // The box is derived state, so it is rebuilt through the entity's own path rather than written
        // to: setBoundingBox is protected, and the position is what makeBoundingBox reads.
        Entity self = (Entity) (Object) this;
        self.setPos(self.getX(), self.getY(), self.getZ());
    }

    @Inject(method = "makeBoundingBox(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/AABB;",
            at = @At("HEAD"), cancellable = true)
    private void stature$perAxisBox(Vec3 position, CallbackInfoReturnable<AABB> cir) {
        if (this.stature$depthMultiplier > 0.0F && this.stature$depthMultiplier != 1.0F) {
            Entity self = (Entity) (Object) this;
            cir.setReturnValue(PerAxisDimensions.box(
                    position, self.getBbWidth(), self.getBbHeight(), this.stature$depthMultiplier));
        }
    }
}
