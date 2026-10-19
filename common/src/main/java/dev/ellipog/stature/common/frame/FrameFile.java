package dev.ellipog.stature.common.frame;

import java.util.List;

import dev.ellipog.stature.api.FrameSpec;

import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;

/**
 * One frame file: the size it declares, and what it says it applies to (plan §14.8's precedence).
 *
 * <p>The size is {@link FrameSpec}, which is game-free; the assignment is here because it names
 * entity types and entity-type tags, and those are Minecraft's. A frame id and the two lists are all
 * that is kept: a pack that says the same type twice is reported at load rather than resolved by
 * accident at runtime.
 */
public record FrameFile(
        Identifier id,
        FrameSpec spec,
        List<Identifier> entities,
        List<TagKey<EntityType<?>>> entityTags) {

    public FrameFile {
        entities = List.copyOf(entities);
        entityTags = List.copyOf(entityTags);
    }

    /** Whether this frame says anything about where it belongs. */
    public boolean hasAssignments() {
        return !this.entities.isEmpty() || !this.entityTags.isEmpty();
    }
}
