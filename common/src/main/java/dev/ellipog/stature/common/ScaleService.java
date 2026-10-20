package dev.ellipog.stature.common;

import dev.ellipog.stature.api.Easing;
import dev.ellipog.stature.api.ScaleAxes;
import dev.ellipog.stature.api.ScaleLimits;
import dev.ellipog.stature.api.ScaleState;
import dev.ellipog.stature.common.channel.ChannelService;
import dev.ellipog.stature.common.net.StatureNetworking;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.server.level.ServerLevel;
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

    /**
     * The size the entity is at, from the base of the game's own scale attribute and the shape
     * on the entity.
     *
     * <p>Stature owns the <em>base</em>, not the final value: another mod's or a potion's
     * {@code minecraft:scale} modifier multiplies on top and is deliberately not folded into the
     * number Stature reads back. Reading the final value and writing it into the base is how a
     * read-modify-write compounds somebody else's modifier into the entity a little more on every
     * apply (the plan's §3.2-K drift). A base written outside the attribute's envelope is clamped
     * for the same reason every other scale is.
     */
    public static ScaleState stateOf(LivingEntity entity) {
        AttributeInstance instance = entity.getAttribute(Attributes.SCALE);
        float scale = instance == null ? 1.0F : (float) instance.getBaseValue();
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
        apply(entity, state, true);
    }

    /**
     * Writes a size onto the entity, optionally settling the box.
     *
     * <p>{@code finalTarget} is false for the intermediate ticks of an ease and true for an instant
     * set or the last tick. Displacement runs only on the final box: the eased path is the bulk of
     * the cost, and a search on every intermediate box would also move an entity through shapes
     * nobody asked for.
     */
    public static void apply(LivingEntity entity, ScaleState state, boolean finalTarget) {
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

        if (finalTarget && !entity.level().isClientSide()) {
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
     * <p>Runs after the final server-side size write, and only when the box grew: a smaller box
     * cannot newly intersect anything. Escape searches outward in growing shells, up first, with a
     * hard cap of {@value #MAX_DISPLACEMENT_CANDIDATES} whole-box positions. Each candidate is the
     * complete box moved, so the entity lands fully in air or not at all. A transition that ends
     * with nowhere to go hurts once, not once per eased tick; vanilla suffocation picks up whatever
     * remains.
     */
    private static void displace(LivingEntity entity, AABB oldBox) {
        AABB box = entity.getBoundingBox();
        if (box.getSize() <= oldBox.getSize()) {
            return;
        }
        Level level = entity.level();
        if (level.noCollision(entity, box)) {
            return;
        }

        for (Offset offset : candidateOffsets(box)) {
            if (moveIfFree(entity, level, box, offset.x(), offset.y(), offset.z())) {
                return;
            }
        }

        StatureScale duck = (StatureScale) entity;
        if (duck.stature$displacementHurt()) {
            return;
        }
        duck.stature$setDisplacementHurt(true);
        if (level instanceof ServerLevel serverLevel) {
            entity.hurtOrSimulate(StatureDamageTypes.compression(serverLevel), 2.0F);
        }
    }

    /** One whole-box displacement, in blocks. */
    record Offset(int x, int y, int z) {
    }

    /** At most this many candidate boxes are collision-tested per settled transition. */
    static final int MAX_DISPLACEMENT_CANDIDATES = 64;

    /**
     * The whole-box offsets to try, in search order: up first, then shells growing by one block.
     *
     * <p>A sparse shell rather than every integer triple: a dense shell at 16x has tens of
     * thousands of points and the useful escapes are the six faces, the horizontal diagonals and
     * the vertical diagonals. Within each shell the six face directions come first, then the
     * diagonals, which is the "prefer up, then the nearest shell" order the plan asks for.
     */
    static List<Offset> candidateOffsets(AABB box) {
        int radius = (int) Math.ceil(Math.max(box.getXsize(), Math.max(box.getYsize(), box.getZsize())));
        radius = Math.max(1, Math.min(48, radius));

        Set<Offset> offsets = new LinkedHashSet<>();
        for (int up = 1; up <= Math.min(4, radius) && offsets.size() < MAX_DISPLACEMENT_CANDIDATES; up++) {
            offsets.add(new Offset(0, up, 0));
        }
        for (int r = 1; r <= radius && offsets.size() < MAX_DISPLACEMENT_CANDIDATES; r++) {
            add(offsets, 0, r, 0);
            add(offsets, 0, -r, 0);
            add(offsets, r, 0, 0);
            add(offsets, -r, 0, 0);
            add(offsets, 0, 0, r);
            add(offsets, 0, 0, -r);
            add(offsets, r, 0, r);
            add(offsets, r, 0, -r);
            add(offsets, -r, 0, r);
            add(offsets, -r, 0, -r);
        }
        // Vertical diagonals only when the face and horizontal shell budget left room: they are
        // the least likely escape and would otherwise eat the radius a tunnel needs.
        for (int r = 1; r <= radius && offsets.size() < MAX_DISPLACEMENT_CANDIDATES; r++) {
            add(offsets, r, r, 0);
            add(offsets, -r, r, 0);
            add(offsets, 0, r, r);
            add(offsets, 0, r, -r);
            add(offsets, r, -r, 0);
            add(offsets, -r, -r, 0);
            add(offsets, 0, -r, r);
            add(offsets, 0, -r, -r);
        }
        return new ArrayList<>(offsets);
    }

    private static void add(Set<Offset> offsets, int x, int y, int z) {
        if (offsets.size() >= MAX_DISPLACEMENT_CANDIDATES) {
            return;
        }
        offsets.add(new Offset(x, y, z));
    }

    private static boolean moveIfFree(
            LivingEntity entity, Level level, AABB box, double dx, double dy, double dz) {
        AABB moved = box.move(dx, dy, dz);
        if (!level.noCollision(entity, moved)) {
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
