package dev.ellipog.stature.common;

import dev.ellipog.stature.api.Easing;
import dev.ellipog.stature.api.ScaleAxes;
import dev.ellipog.stature.api.ScaleLimits;
import dev.ellipog.stature.api.ScaleState;
import dev.ellipog.stature.common.channel.ChannelService;
import dev.ellipog.stature.common.net.StatureNetworking;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * Sizing an entity: the uniform scale, the box's shape, and the attributes that travel with them
 * (plan §14.1 and §14.2).
 *
 * <p>The point of riding the game's own {@code scale} attribute rather than a private multiplier is
 * that vanilla already owns the awkward parts -- pathfinding, collision, camera height, step-up
 * logic. Stature's job is the layer around it.
 *
 * <p><b>Reach and step are bound so the model and the arms agree.</b> A player grown to twice their
 * size with a vanilla reach can see a block they cannot touch, because reach is an attribute in its
 * own right and does not follow {@code scale} on its own. So both interaction ranges and the step
 * height are written from <em>their own default</em> times the scale, never from their current
 * value: a value read back and multiplied compounds every tick, and a twenty-tick ease would leave
 * reach in a place nobody chose.
 *
 * <p><b>The shape travels separately.</b> Vanilla's attribute is one number and cannot describe a
 * box that is deeper than it is wide, so the shape lives on the entity and is sent to clients
 * (plan §16: the box is server-authoritative, and a client that disagrees is wrong). The uniform
 * scale needs no packet at all -- it is one of the game's own synced attributes.
 */
public final class ScaleService {

    private ScaleService() {
    }

    /**
     * Sends {@code entity} towards {@code target}, eased over {@code ticks}.
     *
     * <p>{@code ticks} of zero or less applies it now. Either way the value is clamped to the
     * envelope the game's attribute accepts: a caller asking for 100.0 gets 16.0, not a silent
     * no-op from {@code RangedAttribute}, and not a value that is out of range everywhere else.
     */
    public static void setSize(LivingEntity entity, ScaleState target, int ticks, Easing easing) {
        ((StatureScale) entity).stature$setTarget(clamped(target), ticks, easing);
    }

    /** The size the entity is at, from the game's own attribute and the shape on the entity. */
    public static ScaleState stateOf(LivingEntity entity) {
        AttributeInstance instance = entity.getAttribute(Attributes.SCALE);
        float scale = instance == null ? 1.0F : (float) instance.getValue();
        return new ScaleState(ScaleLimits.clamp(scale), ((StatureScale) entity).stature$axes());
    }

    /**
     * Writes a size onto the entity, then reshapes its box.
     *
     * <p>The dimension refresh is not left to the game's own poll for one tick: that poll runs
     * earlier in the tick than this does, so without it the box would always be one tick behind the
     * number in the attribute -- and "the model grew but the hitbox has not yet" is exactly the frame
     * a player would notice.
     */
    public static void apply(LivingEntity entity, ScaleState state) {
        float scale = state.scale();

        // Only the uniform rides a base write. Reach, step and the rest of the attribute
        // table belong to channels, whose modifiers compose onto the entity's own base:
        // writing both counts twice (a scale-2 reach lands at 18 blocks instead of 9).
        writeScale(entity, scale);

        ChannelService.applyAttributes(entity);

        StatureScale duck = (StatureScale) entity;
        boolean shapeChanged = !duck.stature$axes().equals(state.axes());
        AABB oldBox = entity.getBoundingBox();
        duck.stature$setAxes(state.axes());
        entity.refreshDimensions();

        if (!entity.level().isClientSide()) {
            displace(entity, oldBox);
        }

        if (shapeChanged && !entity.level().isClientSide()) {
            // Only when the shape moved: a uniform ease changes the attribute twenty times and the
            // shape not at all, and vanilla already syncs that attribute every tick.
            StatureNetworking.broadcast(entity, state);
        }
    }

    /**
     * Pushes a grown entity out of solid rock, or hurts it when there is nowhere to go.
     *
     * <p>Runs after every server-side size write, but only when the box grew: a smaller box
     * cannot newly intersect anything. Escape tries up first, then out the sides; each
     * candidate is the whole box moved, so the entity lands fully in air or not at all.
     * Failure hurts a little, every time, because applies repeat during eases: a tap for an
     * instant set, a real crushing for a full ease spent encased. Vanilla suffocation picks
     * up whatever remains.
     */
    private static void displace(LivingEntity entity, AABB oldBox) {
        AABB box = entity.getBoundingBox();
        if (box.getSize() <= oldBox.getSize()) {
            return;
        }
        Level level = entity.level();
        if (!level.getBlockCollisions(entity, box).iterator().hasNext()) {
            return;
        }

        double[] up = {1.0, 2.0, 3.0};
        double[] out = {1.0, 2.0};
        double[][] sides = {{1.0, 0.0}, {-1.0, 0.0}, {0.0, 1.0}, {0.0, -1.0}};
        for (double dy : up) {
            if (moveIfFree(entity, level, box, 0.0, dy, 0.0)) {
                return;
            }
        }
        for (double r : out) {
            for (double[] side : sides) {
                if (moveIfFree(entity, level, box, side[0] * r, 0.0, side[1] * r)) {
                    return;
                }
            }
        }
        entity.hurtOrSimulate(level.damageSources().inWall(), 1.0F);
    }

    private static boolean moveIfFree(
            LivingEntity entity, Level level, AABB box, double dx, double dy, double dz) {
        AABB moved = box.move(dx, dy, dz);
        if (level.getBlockCollisions(entity, moved).iterator().hasNext()) {
            return false;
        }
        entity.teleportTo((moved.minX + moved.maxX) / 2.0, moved.minY, (moved.minZ + moved.maxZ) / 2.0);
        entity.fallDistance = 0.0F;
        return true;
    }

    /**
     * Applies a size that arrived from the server, without writing attributes back.
     *
     * <p>The client's {@code scale} attribute is one of the game's own synced ones and has already
     * been updated by the time this runs; writing it here would put the client's copy against the
     * server's during an ease, which is the disagreement §16 exists to avoid. The eye height does
     * arrive here, because nothing else carries it and the camera reads it on the client.
     *
     * <p>A corrupt eye becomes no override rather than an exception on the network thread, the same
     * guarantee the rest of the packet makes.
     */
    public static void applyFromServer(LivingEntity entity, ScaleState state, float eyeHeight) {
        ((StatureScale) entity).stature$setAxes(state.axes());
        ((StatureScale) entity).stature$setEyeHeight(
                eyeHeight != StatureScale.NO_EYE_HEIGHT && eyeHeight > 0.0F
                        ? eyeHeight
                        : StatureScale.NO_EYE_HEIGHT);
        entity.refreshDimensions();
    }

    /** Pulls both halves into the envelope: the scale, and each axis of the box. */
    public static ScaleState clamped(ScaleState state) {
        return new ScaleState(
                ScaleLimits.clamp(state.scale()),
                new ScaleAxes(
                        ScaleLimits.clamp(state.axes().width()),
                        ScaleLimits.clamp(state.axes().height()),
                        ScaleLimits.clamp(state.axes().depth())));
    }

    /**
     * Writes the uniform scale, if this entity has the attribute at all.
     *
     * <p>Every other attribute belongs to a channel, whose multiplier modifiers compose onto
     * the entity's own base instead of replacing it. A missing attribute is skipped rather
     * than treated as an error.
     */
    private static void writeScale(LivingEntity entity, float scale) {
        AttributeInstance instance = entity.getAttribute(Attributes.SCALE);
        if (instance == null) {
            return;
        }

        if (instance.getBaseValue() != scale) {
            instance.setBaseValue(scale);
        }
    }
}
