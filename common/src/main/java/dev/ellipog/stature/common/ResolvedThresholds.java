package dev.ellipog.stature.common;

import java.util.Map;

/**
 * One entity's threshold tiers and mechanic flags, resolved server-side.
 *
 * <p>Frames live in server datapacks, so the client can never resolve them itself:
 * the server unions the entity's frame overrides over the configured edges and flags
 * and sends the answer. Stored per entity on the {@link StatureScale} duck, where
 * {@link StatureConfig} prefers it over the file values on both sides.
 *
 * <p>Absent (null on the duck) means "no frame overrides this entity": the file
 * values apply, and nothing needs sending.
 */
public record ResolvedThresholds(float micro, float macro, Map<String, Boolean> flags) {

    public ResolvedThresholds {
        flags = Map.copyOf(flags);
    }
}
