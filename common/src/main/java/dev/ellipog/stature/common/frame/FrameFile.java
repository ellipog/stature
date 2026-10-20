package dev.ellipog.stature.common.frame;

import java.util.List;
import java.util.Map;

import dev.ellipog.stature.api.FrameSpec;

import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;

/**
 * One frame file: the size it declares, what it says it applies to, and what silhouette goes
 * with it (plan §14.8's precedence, §14.7's parts).
 *
 * <p>The size is {@link FrameSpec}, which is game-free; the assignment is here because it names
 * entity types and entity-type tags, and those are Minecraft's. Parts ride here too, as group
 * names with per-axis factors: resolution to model paths happens at apply time, so what travels
 * downstream is already paths. A frame id and the three lists are all that is kept: a pack that
 * says the same type twice is reported at load rather than resolved by accident at runtime.
 */
public record FrameFile(
        Identifier id,
        FrameSpec spec,
        List<Identifier> entities,
        List<TagKey<EntityType<?>>> entityTags,
        String rig,
        Map<String, List<Float>> parts,
        boolean hitboxFromParts,
        List<FrameRegion> regions,
        Map<String, Float> channels,
        Float thresholdMicro,
        Float thresholdMacro,
        Map<String, Boolean> thresholdFlags,
        Boolean firstPersonArms) {

    public FrameFile {
        entities = List.copyOf(entities);
        entityTags = List.copyOf(entityTags);
        parts = Map.copyOf(parts);
        regions = List.copyOf(regions);
        channels = Map.copyOf(channels);
        thresholdFlags = Map.copyOf(thresholdFlags);
    }

    /** Whether this frame says anything about where it belongs. */
    public boolean hasAssignments() {
        return !this.entities.isEmpty() || !this.entityTags.isEmpty();
    }

    /** Whether this frame carries a silhouette. */
    public boolean hasParts() {
        return !this.parts.isEmpty();
    }

    /** Whether this frame names anywhere a hit lands differently. */
    public boolean hasRegions() {
        return !this.regions.isEmpty();
    }
}
