package dev.ellipog.stature.common.parts;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import dev.ellipog.stature.api.ScaleAxes;
import dev.ellipog.stature.common.StatureScale;
import dev.ellipog.stature.common.frame.FrameFile;
import dev.ellipog.stature.common.frame.FrameRegion;
import dev.ellipog.stature.common.frame.StatureFrames;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Resolves hurt regions: a hit location inside a part's box deals that region's damage and is
 * tagged (plan §14.7).
 *
 * <p>Regions are frame data, so there is nothing per entity to save or sync: the frame id
 * already travels, and the box is recomputed from the live box at hit time. Matching is pure
 * geometry over the entity's current box, which is what makes it unit-testable; the hit hooks
 * only find the location and spend the answer.
 */
public final class RegionService {

    private RegionService() {
    }

    /**
     * The region a hit location falls in, if any. First match in file order wins, which makes
     * one entry at a time a pack-authoring choice rather than an engine rule.
     */
    public static Optional<HitRegion> resolveHit(LivingEntity victim, Vec3 location) {
        StatureScale duck = (StatureScale) victim;
        Identifier frameId = duck.stature$frame();
        if (frameId == null) {
            return Optional.empty();
        }
        FrameFile file = StatureFrames.file(frameId);
        if (file == null || file.regions().isEmpty()) {
            return Optional.empty();
        }

        String rigId = PartService.rigOf(victim);
        AABB box = victim.getBoundingBox();
        Map<String, ScaleAxes> factors = duck.stature$partFactors();
        return match(file.regions(), rigId, box, factors, location, victim.tickCount);
    }

    /** The last region hit on this entity, for downstream consumers. */
    public static Optional<HitRegion> lastHit(LivingEntity entity) {
        return Optional.ofNullable(((StatureScale) entity).stature$lastHit());
    }

    /**
     * The region a location falls in, if any. First match in list order wins. Pure geometry,
     * no entity in sight.
     *
     * <p>Each box is breathed out by a hair before testing: impact points land exactly on
     * faces by construction, and containment is max-exclusive, so without this a dead-center
     * shot to the top of the head would count as a miss.
     */
    static Optional<HitRegion> match(List<FrameRegion> regions, String rigId, AABB box,
            Map<String, ScaleAxes> factors, Vec3 location, int tick) {
        for (FrameRegion region : regions) {
            if (!RigRegistry.instance().hasGroup(rigId, region.part())) {
                continue;
            }
            if (regionBox(rigId, region, box, factors).inflate(1.0E-3).contains(location)) {
                return Optional.of(new HitRegion(region.part(), region.tag(), region.multiplier(), tick));
            }
        }
        return Optional.empty();
    }

    /**
     * One region's world box: its rig fractions of the live box, breathed out by its part
     * factor about its own center. Unfactored parts land exactly on their fractions; a doubled
     * head covers double height around the same middle.
     */
    static AABB regionBox(String rigId, FrameRegion region, AABB box, Map<String, ScaleAxes> factors) {
        List<Float> fractions = RigRegistry.instance().boxOf(rigId, region.part());
        if (fractions == null) {
            fractions = RigRegistry.FULL_BOX;
        }
        List<String> paths = RigRegistry.instance().resolve(rigId, region.part());
        ScaleAxes factor = ScaleAxes.UNIFORM;
        if (!paths.isEmpty()) {
            factor = factors.getOrDefault(paths.get(0), ScaleAxes.UNIFORM);
        }

        double sizeX = box.getXsize();
        double sizeY = box.getYsize();
        double sizeZ = box.getZsize();
        double[] lo = {
                box.minX + fractions.get(0) * sizeX,
                box.minY + fractions.get(1) * sizeY,
                box.minZ + fractions.get(2) * sizeZ};
        double[] hi = {
                box.minX + fractions.get(3) * sizeX,
                box.minY + fractions.get(4) * sizeY,
                box.minZ + fractions.get(5) * sizeZ};
        float[] f = {factor.width(), factor.height(), factor.depth()};
        double[] min = new double[3];
        double[] max = new double[3];
        for (int i = 0; i < 3; i++) {
            double center = (lo[i] + hi[i]) / 2.0;
            double half = (hi[i] - lo[i]) / 2.0 * f[i];
            min[i] = center - half;
            max[i] = center + half;
        }
        return new AABB(min[0], min[1], min[2], max[0], max[1], max[2]);
    }
}
