package dev.ellipog.stature.spike;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The state the spike attaches to an entity, and the whole of its "API".
 *
 * <p>Two interfaces rather than one because the two halves are attached to different classes. The
 * footprint's depth is a property of every {@code Entity}, because the box is built in
 * {@code Entity.makeBoundingBox}. The width and height multipliers only mean anything on a
 * {@code LivingEntity}, because they compose where {@code LivingEntity.getDimensions(Pose)} applies
 * the scale attribute, and no other class has that method.
 *
 * <p>Both are implemented by mixins. The names carry the {@code stature$} prefix because a mixin adds
 * these members to a Minecraft class, and Mixin refuses a member without the configured prefix.
 */
public interface PerAxisDimensions {

    /**
     * Depth (Z) as a multiplier of the vanilla footprint, which is square.
     *
     * <p>Vanilla gives an entity one width, used for both X and Z, so a depth multiplier of {@code
     * 2.0} means "twice as deep as I am wide" and {@code 1.0} means vanilla. Below {@code 1.0} is a
     * thinner entity, which is the case the launch roster actually wants.
     */
    float stature$depthMultiplier();

    void stature$setDepthMultiplier(float multiplier);

    /** Width (X) and height (Y) multipliers, applied where {@code getDimensions} composes. */
    interface Size {

        float stature$widthMultiplier();

        float stature$heightMultiplier();

        void stature$setSize(float widthMultiplier, float heightMultiplier);
    }

    /**
     * The three multipliers as carried on a client render state.
     *
     * <p>26.3 does not render an entity, it renders a state: {@code extractRenderState} fills one and
     * {@code submit} draws it, and {@code submit} never sees the entity. So the factors have to travel
     * on the state, which holds one {@code scale} float and no room for three.
     */
    interface Render {

        void stature$setRenderScale(float widthMultiplier, float heightMultiplier, float depthMultiplier);

        float stature$renderWidth();

        float stature$renderHeight();

        float stature$renderDepth();
    }

    /** Builds the box for a position, honouring the depth multiplier. */
    static AABB box(Vec3 position, float width, float height, float depthMultiplier) {
        double halfX = width / 2.0;
        double halfZ = halfX * depthMultiplier;
        return new AABB(
                position.x - halfX, position.y, position.z - halfZ,
                position.x + halfX, position.y + height, position.z + halfZ);
    }
}
