package dev.ellipog.stature.common.mixin;

import java.util.Map;
import java.util.Objects;

import com.mojang.serialization.Codec;

import dev.ellipog.stature.api.ScaleAxes;
import dev.ellipog.stature.api.ScaleState;
import dev.ellipog.stature.common.SaveGate;
import dev.ellipog.stature.common.ScaleService;
import dev.ellipog.stature.common.StatureProjectile;
import dev.ellipog.stature.common.StatureScale;
import dev.ellipog.stature.common.StatureConfig;
import dev.ellipog.stature.common.Thresholds;
import dev.ellipog.stature.common.api.StatureEvents;
import dev.ellipog.stature.common.channel.ChannelService;
import dev.ellipog.stature.common.frame.FrameService;
import dev.ellipog.stature.common.frame.FrameSource;
import dev.ellipog.stature.common.net.StaturePackets;
import dev.ellipog.stature.common.parts.PartService;

import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Two things vanilla's dimensions cannot express: a depth (plan §14.2) and a size that survives a
 * save (plan §14.8's "the winner is saved with the entity and travels with it").
 *
 * <p><b>The box.</b> The injection point is the {@code Vec3} overload, not the no-arg method:
 *
 * <pre>
 * Entity.java   protected final AABB makeBoundingBox() { return this.makeBoundingBox(this.position); }
 * Entity.java   protected AABB makeBoundingBox(Vec3 position) { return this.dimensions.makeBoundingBox(position); }
 * </pre>
 *
 * <p>The no-arg one is {@code final} in 26.3, and the one that takes a position has the entity in
 * hand, which is why the depth lives on the entity rather than in the dimensions:
 * {@code EntityDimensions.makeBoundingBox} uses one half-width for both X and Z and cannot tell which
 * entity it is for. Width and height are already shaped by {@code LivingEntity.getDimensions}; this
 * only changes Z, by the ratio between the depth multiplier and the width one, so a box shaped
 * {@code 0.5 wide} and {@code 2.0 deep} comes out {@code 4x} deeper than it is wide.
 *
 * <p><b>The save.</b> Both hooks are on methods {@code Entity} implements rather than on the abstract
 * {@code addAdditionalSaveData}/{@code readAdditionalSaveData} pair, which a mixin cannot inject into
 * because there is no body to inject into. Nothing is written for an entity that is still vanilla in
 * every respect, so a frame-less world does not carry Stature's keys on every mob in it. Corrupt
 * values are reported and ignored rather than thrown: a bad number in a save file must not be a
 * world that will not load.
 */
@Mixin(Entity.class)
public abstract class EntityMixin {

    private static final Logger STATURE_LOG = LoggerFactory.getLogger("stature");

    private static final String KEY_WIDTH = "stature_width";

    private static final String KEY_HEIGHT = "stature_height";

    private static final String KEY_DEPTH = "stature_depth";

    private static final String KEY_EYE = "stature_eye";

    private static final String KEY_FRAME = "stature_frame";

    private static final String KEY_SOURCE = "stature_frame_source";

    private static final String KEY_DANGEROUS_MODEL = "stature_dangerous_model";

    private static final String KEY_FRAME_CHANNELS = "stature_frame_channels";

    private static final Codec<Map<String, Float>> FRAME_CHANNEL_CODEC =
            Codec.unboundedMap(Codec.STRING, Codec.FLOAT);

    private static final String KEY_FORMAT = "stature_format";

    /**
     * The save format's own version, bumped the day attribute channels stopped writing bases.
     * Loads from before it get their reach and step bases restored (see below); anything at or
     * past it was written under the modifier regime and needs no repair.
     */
    private static final int SAVE_FORMAT = 2;

    /**
     * Gives macro entities heavier footsteps: the same terminal sound call vanilla ends at,
     * with volume up and pitch down by scale. Reimplemented rather than argument-tweaked
     * because the call between here and there differs per loader; this shape is vanilla on
     * both. Carpet-muffled steps bypass this method and stay vanilla-pitched.
     *
     * <p>{@code BlockState.getSoundType()} is deprecated in 26.3, but vanilla's own
     * {@code Entity.playStepSound} still calls it and there is no public replacement. NeoForge's
     * {@code IBlockStateExtension} adds one, but it is not on :common's NeoForm classpath, so the
     * call stays and the warning is suppressed with this reason rather than left to hide others.
     */
    @SuppressWarnings("deprecation")
    @Inject(
            method = "playStepSound(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)V",
            at = @At("HEAD"),
            cancellable = true)
    private void stature$stepSeismic(BlockPos pos, BlockState blockState, CallbackInfo info) {
        if ((Object) this instanceof LivingEntity living
                && StatureConfig.enabled(living, StatureConfig.SEISMIC_FOOTSTEPS)
                && StatureConfig.isMacro(living)) {
            SoundType soundType = blockState.getSoundType();
            float volume = soundType.getVolume() * Thresholds.stepVolume(living, 0.15F);
            living.playSound(soundType.getStepSound(),
                    volume,
                    soundType.getPitch() * Thresholds.stepPitch(living, 1.0F));
            info.cancel();
            if (!living.level().isClientSide()) {
                StaturePackets.broadcastFootstep(living, volume);
            }
        }
    }

    /**
     * Scales a projectile's box from the size its shooter gave it.
     *
     * <p>This is the server and client hitbox: the enlarged dimensions are what entity movement,
     * projectile collision and pickup use, so hit detection cannot disagree with the drawn size.
     * Living entities have their own, final implementation of this method; a projectile inherits
     * this one.
     */
    @Inject(method = "getDimensions", at = @At("RETURN"), cancellable = true)
    private void stature$projectileDimensions(Pose pose, CallbackInfoReturnable<EntityDimensions> cir) {
        if ((Object) this instanceof StatureProjectile projectile
                && projectile.stature$projectileScaled()
                && projectile.stature$projectileScale() != 1.0F) {
            cir.setReturnValue(cir.getReturnValue().scale(projectile.stature$projectileScale()));
        }
    }

    @Inject(method = "makeBoundingBox(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/AABB;",
            at = @At("HEAD"), cancellable = true)
    private void stature$shapedBox(Vec3 position, CallbackInfoReturnable<AABB> cir) {
        if (!((Object) this instanceof StatureScale scaled)) {
            return;
        }

        ScaleAxes axes = scaled.stature$axes();
        if (axes.isUniform()) {
            return;
        }

        Entity self = (Entity) (Object) this;
        double halfWidth = self.getBbWidth() / 2.0;
        double halfDepth = halfWidth * axes.zRatio();

        cir.setReturnValue(new AABB(
                position.x - halfWidth, position.y, position.z - halfDepth,
                position.x + halfWidth, position.y + self.getBbHeight(), position.z + halfDepth));
    }

    @Inject(method = "saveWithoutId", at = @At("TAIL"))
    private void stature$saveSize(ValueOutput output, CallbackInfo info) {
        if (!((Object) this instanceof StatureScale scaled)) {
            return;
        }

        ScaleAxes axes = scaled.stature$axes();
        float eyeHeight = scaled.stature$eyeHeight();
        Identifier frame = scaled.stature$frame();
        FrameSource source = scaled.stature$frameSource();

        if (!SaveGate.shouldWrite(axes, eyeHeight, frame, source,
                !scaled.stature$channelOverrides().isEmpty(),
                !scaled.stature$partFactors().isEmpty())) {
            return;
        }

        output.putFloat(KEY_WIDTH, axes.width());
        output.putFloat(KEY_HEIGHT, axes.height());
        output.putFloat(KEY_DEPTH, axes.depth());

        if (eyeHeight != StatureScale.NO_EYE_HEIGHT) {
            output.putFloat(KEY_EYE, eyeHeight);
        }
        if (frame != null) {
            output.putString(KEY_FRAME, frame.toString());
        }
        if (source == FrameSource.MANUAL || source == FrameSource.COMMAND
                || source == FrameSource.INTEGRATION
                || source == FrameSource.TAG || source == FrameSource.TYPE) {
            output.putString(KEY_SOURCE, source.name());
        }
        if (scaled.stature$dangerousModel()) {
            // A dangerous model is a channel override, so the channels leg above already makes
            // this entity save. The flag travels too: without it a relog would make a later
            // plain /stature set keep the old dangerous model.
            output.putBoolean(KEY_DANGEROUS_MODEL, true);
        }
        if (scaled instanceof LivingEntity frameChannels
                && !((StatureScale) frameChannels).stature$frameChannelValues().isEmpty()) {
            output.store(KEY_FRAME_CHANNELS, FRAME_CHANNEL_CODEC,
                    ((StatureScale) frameChannels).stature$frameChannelValues());
        }
        output.putInt(KEY_FORMAT, SAVE_FORMAT);

        // Overrides travel; easing progress does not. A load lands, it never resumes a glide.
        if ((Object) this instanceof LivingEntity living) {
            ChannelService.save(living, output);
        }
        PartService.save(scaled, output);
    }

    @Inject(method = "load", at = @At("TAIL"))
    private void stature$loadSize(ValueInput input, CallbackInfo info) {
        if ((Object) this instanceof StatureScale scaled) {
            // One batch so the channel, size, frame and threshold events drain together, after the
            // load has settled, in the order they were committed.
            StatureEvents.batch(() -> stature$loadSizeNow(input, scaled));
        }
    }

    private void stature$loadSizeNow(ValueInput input, StatureScale scaled) {
        ScaleState beforeState = scaled instanceof LivingEntity beforeLiving
                ? ScaleService.stateOf(beforeLiving)
                : null;
        Identifier beforeFrame = scaled.stature$frame();

        float width = input.getFloatOr(KEY_WIDTH, 1.0F);
        float height = input.getFloatOr(KEY_HEIGHT, 1.0F);
        float depth = input.getFloatOr(KEY_DEPTH, 1.0F);
        float eyeHeight = input.getFloatOr(KEY_EYE, StatureScale.NO_EYE_HEIGHT);
        if (eyeHeight != StatureScale.NO_EYE_HEIGHT && !(eyeHeight > 0.0F)) {
            // Zero and negative heights are never a camera: a corrupt save must not park
            // the eye at the feet, so it degrades to no override like an absent key.
            STATURE_LOG.warn("ignoring a saved eye height that is not a height; this entity loads vanilla-eyed");
            eyeHeight = StatureScale.NO_EYE_HEIGHT;
        }
        Identifier frame = input.getString(KEY_FRAME).map(Identifier::tryParse).orElse(null);
        FrameSource source = input.getString(KEY_SOURCE).map(name -> {
            try {
                return FrameSource.valueOf(name);
            } catch (IllegalArgumentException unknown) {
                STATURE_LOG.warn("ignoring a saved frame source that is not a source ({}); this entity loads vanilla", name);
                return null;
            }
        }).orElse(null);

        // Channels ride separately from the box: an entity can carry overrides with a vanilla
        // box, and that alone must keep it out of the early return below. Parts ride the same
        // way, on their own keys.
        boolean channels = scaled instanceof LivingEntity living
                ? ChannelService.load(living, input)
                : false;
        boolean parts = PartService.load(scaled, input);
        Map<String, Float> frameChannels = input.read(KEY_FRAME_CHANNELS, FRAME_CHANNEL_CODEC)
                .filter(values -> !values.isEmpty())
                .orElse(Map.of());
        if (scaled instanceof LivingEntity livingWithFrameChannels) {
            ((StatureScale) livingWithFrameChannels).stature$frameChannelValues().clear();
            ((StatureScale) livingWithFrameChannels).stature$frameChannelValues().putAll(frameChannels);
        }

        if (width == 1.0F && height == 1.0F && depth == 1.0F
                && eyeHeight == StatureScale.NO_EYE_HEIGHT && frame == null && source == null
                && !channels && !parts && frameChannels.isEmpty()) {
            return;
        }

        if (source == null) {
            // Saves written before sources were tracked still need their meaning back: a named frame
            // came from an explicit choice, while stray nonuniform numbers came from a hand choice.
            source = frame != null ? FrameSource.COMMAND : FrameSource.MANUAL;
        }

        if (input.getIntOr(KEY_FORMAT, 1) < SAVE_FORMAT
                && !((Entity) (Object) this).level().isClientSide()
                && scaled instanceof LivingEntity living) {
            // One release wrote reach and step straight into attribute bases, multiplying the
            // registered default instead of the entity's own base -- and a base, once written, is
            // what the save faithfully carries back. Anything stamped before the modifier regime
            // gets those three bases restored to the registered defaults, guarded so a pristine
            // base is never touched: without the repair the stale base would double-count against
            // the new modifiers forever, and the custom it could destroy was already unrecoverable
            // (the old code replaced it on every apply without ever storing it).
            resetBase(living, Attributes.BLOCK_INTERACTION_RANGE);
            resetBase(living, Attributes.ENTITY_INTERACTION_RANGE);
            resetBase(living, Attributes.STEP_HEIGHT);
        }

        try {
            scaled.stature$setAxes(new ScaleAxes(width, height, depth));
        } catch (IllegalArgumentException e) {
            STATURE_LOG.warn("ignoring a saved size that is not a size ({}); this entity loads vanilla", e.getMessage());
            return;
        }

        scaled.stature$setEyeHeight(eyeHeight);
        scaled.stature$setFrame(frame);
        scaled.stature$setFrameSource(source);
        scaled.stature$setDangerousModel(input.getBooleanOr(KEY_DANGEROUS_MODEL, false));
        if (scaled instanceof LivingEntity livingForHands) {
            // The flag is never saved: the frame is the source of truth, so a load re-reads
            // it. On the client the registry is empty and this reads false until the parts
            // packet -- which carries the flag -- replaces it.
            FrameService.refreshFirstPerson(livingForHands);
        }

        // The dimensions field was built from the entity's type before this ran, so without a
        // refresh the box would stay vanilla until something else happened to reshape it.
        Entity self = (Entity) (Object) this;
        self.refreshDimensions();

        // The box and the frame are committed. The events are queued by the batch in the injection
        // above, so a listener cannot make the broadcasts below send a stale value.
        if (scaled instanceof LivingEntity loaded) {
            ScaleState afterState = ScaleService.stateOf(loaded);
            if (beforeState != null && !beforeState.equals(afterState)) {
                StatureEvents.fireScaleChanged(
                        loaded, beforeState, afterState, StatureEvents.Cause.LOAD);
            }
            if (!Objects.equals(beforeFrame, scaled.stature$frame())) {
                StatureEvents.fireFrameChanged(
                        loaded, beforeFrame, scaled.stature$frame(), StatureEvents.Cause.LOAD);
            }
        }

        // A load restores the server's truth, but nobody tells the clients: broadcasts only fire
        // on change, and StartTracking never fires for your own player entity. Flag players for
        // the tick-hook re-send, and broadcast now for the mob case, where tracking picks it up.
        // Each third travels only when it has something to say: a parts-only entity sends no
        // box packet and no channel packet.
        boolean custom = frame != null || eyeHeight != StatureScale.NO_EYE_HEIGHT
                || width != 1.0F || height != 1.0F || depth != 1.0F;
        boolean partsOrHands = parts || scaled.stature$firstPersonVanilla();
        if (!self.level().isClientSide() && scaled instanceof LivingEntity living) {
            if (scaled instanceof ServerPlayer) {
                // A player loads in PrepareSpawnTask, before its connection exists. Sending here
                // would reach the self leg of sendToTracking with a null connection and kill the
                // join. The flag makes the tick hook send to the player, and StartTracking tells
                // everyone else once the entity is in the level, so nothing is lost by waiting.
                if (custom || channels || partsOrHands) {
                    scaled.stature$setNeedsSync(true);
                }
            } else {
                if (custom) {
                    StaturePackets.broadcast(living, ScaleService.stateOf(living));
                }
                StatureConfig.pushThresholds(living, StatureEvents.Cause.LOAD);
                if (channels) {
                    StaturePackets.broadcast(living, ChannelService.syncMap(living));
                }
                if (partsOrHands) {
                    // The hands flag rides the parts packet, so a framed entity whose silhouette
                    // is otherwise vanilla still has to send it: without this a
                    // first_person_arms frame with no parts would read vanilla on the server
                    // after a load and vanilla everywhere on the clients tracking it.
                    StaturePackets.broadcastParts(living, PartService.syncMap(living));
                }
            }
        }
    }

    /** Restores one attribute base to its registered default, touching nothing already there. */
    private static void resetBase(LivingEntity entity, Holder<Attribute> attribute) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        double def = attribute.value().getDefaultValue();
        if (instance.getBaseValue() != def) {
            instance.setBaseValue(def);
        }
    }
}
