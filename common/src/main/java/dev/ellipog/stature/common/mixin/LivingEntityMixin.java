package dev.ellipog.stature.common.mixin;

import dev.ellipog.stature.api.Easing;
import dev.ellipog.stature.api.ScaleAxes;
import dev.ellipog.stature.api.ScaleState;
import dev.ellipog.stature.api.ScaleTransition;
import dev.ellipog.stature.common.ScaleService;
import dev.ellipog.stature.common.StatureScale;
import dev.ellipog.stature.common.frame.FrameService;
import dev.ellipog.stature.common.frame.FrameSource;
import dev.ellipog.stature.common.net.StatureNetworking;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;

import org.jetbrains.annotations.Nullable;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Gives a living entity its own size transition, its own box shape and its own eye height.
 *
 * <p>Four injections and no overwrites. The transition, the shape and the frame are <em>not</em>
 * created eagerly: a unique field with an initialiser is merged into constructors, and one of these
 * would then be built before the entity's attribute map exists. They are made on first use instead.
 *
 * <p>The tick hook is at the tail of {@code LivingEntity.tick()}, which is where the value for this
 * tick is known and where the entity is already being ticked -- so a transition costs nothing on an
 * entity nobody has scaled, and no loader has to offer a player-tick event for it.
 *
 * <p>The dimensions hook multiplies width and height where the game already composes them, then
 * applies the frame's eye height on top. It runs after vanilla's own composition (type dimensions,
 * then age, then the attribute), so a shaped entity still respects all three, and a {@code fixed}
 * dimension ignores the shape for free because {@code EntityDimensions.scale} returns itself when
 * {@code fixed} is set. The eye height is the one field vanilla derives rather than scales -- 85% of
 * the height -- so a frame that names one is overriding exactly that default.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin implements StatureScale {

    @Unique
    private ScaleTransition stature$transition;

    @Unique
    private ScaleAxes stature$axes;

    @Unique
    private Identifier stature$frame;

    @Unique
    private FrameSource stature$frameSource = FrameSource.NONE;

    @Unique
    private boolean stature$needsSync;

    @Unique
    private float stature$eyeHeight = NO_EYE_HEIGHT;

    @Override
    public ScaleTransition stature$transition() {
        if (this.stature$transition == null) {
            this.stature$transition = ScaleTransition.at(this.stature$currentState());
        }
        return this.stature$transition;
    }

    @Override
    public ScaleState stature$currentState() {
        return ScaleService.stateOf((LivingEntity) (Object) this);
    }

    @Override
    public ScaleAxes stature$axes() {
        return this.stature$axes == null ? ScaleAxes.UNIFORM : this.stature$axes;
    }

    @Override
    public void stature$setAxes(ScaleAxes axes) {
        this.stature$axes = axes;
    }

    @Override
    public @Nullable Identifier stature$frame() {
        return this.stature$frame;
    }

    @Override
    public void stature$setFrame(@Nullable Identifier frame) {
        this.stature$frame = frame;
    }

    @Override
    public FrameSource stature$frameSource() {
        return this.stature$frameSource == null ? FrameSource.NONE : this.stature$frameSource;
    }

    @Override
    public void stature$setFrameSource(FrameSource source) {
        this.stature$frameSource = source == null ? FrameSource.NONE : source;
    }

    @Override
    public boolean stature$needsSync() {
        return this.stature$needsSync;
    }

    @Override
    public void stature$setNeedsSync(boolean needsSync) {
        this.stature$needsSync = needsSync;
    }

    @Override
    public float stature$eyeHeight() {
        return this.stature$eyeHeight;
    }

    @Override
    public void stature$setEyeHeight(float eyeHeight) {
        this.stature$eyeHeight = eyeHeight;
    }

    @Override
    public void stature$setTarget(ScaleState target, int ticks, Easing easing) {
        LivingEntity self = (LivingEntity) (Object) this;
        this.stature$transition = ScaleTransition.between(
                ScaleService.stateOf(self), ScaleService.clamped(target), ticks, easing);

        if (this.stature$transition.isDone()) {
            // A change with no ticks is not a special case with its own code path: it is a
            // transition that is already over, and it lands here so that the instant and the eased
            // command differ only in the numbers they pass.
            ScaleService.apply(self, ScaleService.clamped(target));
        }
    }

    @Inject(method = "tick()V", at = @At("TAIL"))
    private void stature$advanceSize(CallbackInfo info) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!self.level().isClientSide()) {
            FrameService.ensureAssigned(self);
            stature$syncSelf(self);
        }
        if (this.stature$transition == null || this.stature$transition.isDone()) {
            return;
        }
        ScaleService.apply(self, this.stature$transition.advance());
    }

    /**
     * Re-sends a customized player to themselves.
     *
     * <p>Broadcasts and StartTracking cover every viewer <em>except</em> the player themself: your
     * own entity never triggers tracking, so after a relog -- or a dimension hop, or a dropped
     * packet -- you would see yourself vanilla while the server has you shaped. The flag covers the
     * load itself; the twenty-tick cadence covers everything the flag can miss, for one small
     * packet a second per customized player.
     */
    @Unique
    private void stature$syncSelf(LivingEntity self) {
        if (!(self instanceof ServerPlayer player)
                || (!this.stature$needsSync && self.tickCount % 20 != 0)) {
            return;
        }
        this.stature$needsSync = false;
        if (FrameService.isCustomized(self)) {
            StatureNetworking.sendTo(player, self, ScaleService.stateOf(self));
        }
    }

    @Inject(method = "getDimensions", at = @At("RETURN"), cancellable = true)
    private void stature$shapeDimensions(Pose pose, CallbackInfoReturnable<EntityDimensions> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        EntityDimensions original = cir.getReturnValue();
        EntityDimensions shaped = original;

        ScaleAxes axes = this.stature$axes();
        if (!axes.isUniform()) {
            shaped = shaped.scale(axes.width(), axes.height());
        }
        if (this.stature$eyeHeight > 0.0F) {
            // Absolute at scale 1, so the uniform scale multiplies it exactly as it multiplies the
            // frame's size: a frame at 1.65x has its eye height at 1.65x too.
            shaped = shaped.withEyeHeight(this.stature$eyeHeight * self.getScale());
        }

        if (shaped != original) {
            cir.setReturnValue(shaped);
        }
    }
}
