package dev.ellipog.stature.common.mixin;

import dev.ellipog.stature.api.Easing;
import dev.ellipog.stature.api.ScaleAxes;
import dev.ellipog.stature.api.ScaleState;
import dev.ellipog.stature.api.ScaleTransition;
import dev.ellipog.stature.api.ChannelTransition;
import dev.ellipog.stature.api.AxesTransition;
import dev.ellipog.stature.common.parts.HitRegion;
import dev.ellipog.stature.common.ResolvedThresholds;
import dev.ellipog.stature.common.ScaleService;
import dev.ellipog.stature.common.StatureConfig;
import dev.ellipog.stature.common.StatureScale;
import dev.ellipog.stature.common.Thresholds;
import dev.ellipog.stature.common.channel.ChannelService;
import dev.ellipog.stature.common.client.RenderSampler;
import dev.ellipog.stature.common.parts.PartService;
import dev.ellipog.stature.common.frame.FrameService;
import dev.ellipog.stature.common.frame.FrameSource;
import dev.ellipog.stature.common.net.StatureNetworking;

import java.util.LinkedHashMap;
import java.util.Map;

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
import org.spongepowered.asm.mixin.injection.ModifyVariable;
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
    private Map<String, Float> stature$channelOverrides;

    @Unique
    private Map<String, ChannelTransition> stature$channelTransitions;

    @Unique
    private Map<String, ScaleAxes> stature$partFactors;

    @Unique
    private Map<String, AxesTransition> stature$partTransitions;

    @Unique
    private boolean stature$firstPersonVanilla;

    @Unique
    private boolean stature$dangerousModel;

    @Unique
    private boolean stature$displacementHurt;

    @Unique
    private HitRegion stature$incomingRegion;

    @Unique
    private HitRegion stature$lastHit;

    @Unique
    private float stature$eyeHeight = NO_EYE_HEIGHT;

    @Unique
    private RenderSampler stature$sampler;

    @Unique
    @Nullable
    private ResolvedThresholds stature$thresholds;

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
    public Map<String, Float> stature$channelOverrides() {
        if (this.stature$channelOverrides == null) {
            this.stature$channelOverrides = new LinkedHashMap<>();
        }
        return this.stature$channelOverrides;
    }

    @Override
    public Map<String, ChannelTransition> stature$channelTransitions() {
        if (this.stature$channelTransitions == null) {
            this.stature$channelTransitions = new LinkedHashMap<>();
        }
        return this.stature$channelTransitions;
    }

    @Override
    public Map<String, ScaleAxes> stature$partFactors() {
        if (this.stature$partFactors == null) {
            this.stature$partFactors = new LinkedHashMap<>();
        }
        return this.stature$partFactors;
    }

    @Override
    public Map<String, AxesTransition> stature$partTransitions() {
        if (this.stature$partTransitions == null) {
            this.stature$partTransitions = new LinkedHashMap<>();
        }
        return this.stature$partTransitions;
    }

    @Override
    public boolean stature$firstPersonVanilla() {
        return this.stature$firstPersonVanilla;
    }

    @Override
    public void stature$setFirstPersonVanilla(boolean vanilla) {
        this.stature$firstPersonVanilla = vanilla;
    }

    @Override
    public boolean stature$dangerousModel() {
        return this.stature$dangerousModel;
    }

    @Override
    public void stature$setDangerousModel(boolean dangerousModel) {
        this.stature$dangerousModel = dangerousModel;
    }

    @Override
    public boolean stature$displacementHurt() {
        return this.stature$displacementHurt;
    }

    @Override
    public void stature$setDisplacementHurt(boolean hurt) {
        this.stature$displacementHurt = hurt;
    }

    @Override
    public HitRegion stature$incomingRegion() {
        return this.stature$incomingRegion;
    }

    @Override
    public void stature$setIncomingRegion(HitRegion region) {
        this.stature$incomingRegion = region;
    }

    @Override
    public HitRegion stature$lastHit() {
        return this.stature$lastHit;
    }

    @Override
    public void stature$setLastHit(HitRegion hit) {
        this.stature$lastHit = hit;
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
    public @Nullable ResolvedThresholds stature$thresholds() {
        return this.stature$thresholds;
    }

    @Override
    public void stature$setThresholds(@Nullable ResolvedThresholds thresholds) {
        this.stature$thresholds = thresholds;
    }

    @Override
    public RenderSampler stature$renderSampler() {
        if (this.stature$sampler == null) {
            this.stature$sampler = new RenderSampler();
        }
        return this.stature$sampler;
    }

    @Override
    public void stature$setTarget(ScaleState target, int ticks, Easing easing) {
        LivingEntity self = (LivingEntity) (Object) this;
        this.stature$displacementHurt = false;
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
            ChannelService.tick(self);
            PartService.tick(self);
            Thresholds.tick(self);
            stature$syncSelf(self);
        } else {
            // Render history lives on the entity because the state is rebuilt every frame:
            // sampling the visual scale and shape here lets extractRenderState blend them
            // at the frame's partial tick instead of stepping at 20 Hz. Pure math, so the
            // server carrying this field costs nothing but the object itself.
            stature$renderSampler().tick(ChannelService.visualScale(self, self.getScale()), stature$axes());
        }
        if (this.stature$transition == null || this.stature$transition.isDone()) {
            return;
        }
        // Displacement is paid only once the target box is reached: during an ease the box
        // passes through shapes nobody asked for, and searching for a way out on every one of
        // them is both the bulk of the cost and a jitter the player would see.
        ScaleService.apply(self, this.stature$transition.advance(), this.stature$transition.isDone());
    }

    /**
     * Spends a stashed hurt region on incoming damage.
     *
     * <p>The stash is honored only in its own tick: anything later is a different hit, and
     * anything else ticking damage in between (fire, lava) must not inherit it. Spending
     * records the last hit and clears the stash, in that order. The multiplier lands before
     * armor and effects, so a weak point stays weak through plate.
     *
     * <p>Index 3 is the damage float, counted from the pinned signature: slot 0 is this,
     * then level, source, damage in order. The contract test pins that order, so a Mojang
     * reorder fails the build instead of scaling the wrong slot.
     */
    @ModifyVariable(
            method = "hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z",
            at = @At("HEAD"),
            index = 3)
    private float stature$scaleRegionDamage(float damage) {
        LivingEntity self = (LivingEntity) (Object) this;
        HitRegion region = this.stature$incomingRegion;
        if (region == null || region.tick() != self.tickCount) {
            return damage;
        }
        this.stature$incomingRegion = null;
        this.stature$lastHit = region;
        return damage * region.multiplier();
    }

    /**
     * Lets micro entities climb any wall they are pressed against. Runs on both sides so
     * prediction agrees; the client gates on the synced config with frame fallback.
     */
    @Inject(method = "onClimbable()Z", at = @At("RETURN"), cancellable = true)
    private void stature$climbWalls(CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue() && Thresholds.canClimbWalls((LivingEntity) (Object) this)) {
            cir.setReturnValue(true);
        }
    }

    /**
     * Re-sends a customized player to themselves.
     *
     * <p>Broadcasts and StartTracking cover every viewer <em>except</em> the player themself: your
     * own entity never triggers tracking, so after a relog -- or a dimension hop, or a dropped
     * packet -- you would see yourself vanilla while the server has you shaped. The flag covers the
     * load itself; the twenty-tick cadence covers everything the flag can miss, for one small
     * packet a second per customized player. Channel and part state ride along whenever there
     * is any.
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
        if (ChannelService.hasSyncedState(self)) {
            StatureNetworking.sendTo(player, self, ChannelService.syncMap(self));
        }
        if (PartService.hasParts(self) || this.stature$firstPersonVanilla()) {
            StatureNetworking.sendPartsTo(player, self, PartService.syncMap(self));
        }
        if (FrameService.frameOf(self) != null) {
            StatureConfig.sendThresholds(player, self);
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
        if (this.stature$eyeHeight != StatureScale.NO_EYE_HEIGHT && pose != Pose.SLEEPING) {
            // Absolute at scale 1, so the uniform scale multiplies it exactly as it multiplies the
            // frame's size: a frame at 1.65x has its eye height at 1.65x too.
            float frameEye = this.stature$eyeHeight;
            if (pose == Pose.STANDING) {
                shaped = shaped.withEyeHeight(frameEye * self.getScale());
            } else {
                float standingVanilla = self.getType().getDimensions().eyeHeight();
                if (standingVanilla > 0.0F) {
                    // Any other posture keeps its own posture: vanilla's pose eye scaled by the
                    // same ratio the frame applies at standing, so a crawl stays a crawl and
                    // the eye never leaves the box it belongs to.
                    shaped = shaped.withEyeHeight(original.eyeHeight() * (frameEye / standingVanilla));
                } else {
                    shaped = shaped.withEyeHeight(frameEye * self.getScale());
                }
            }
        }

        if (shaped != original) {
            cir.setReturnValue(shaped);
        }
    }
}
