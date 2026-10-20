package dev.ellipog.stature.common;

import dev.ellipog.stature.api.ScaleAxes;
import dev.ellipog.stature.common.frame.FrameSource;

import net.minecraft.resources.Identifier;

import org.jetbrains.annotations.Nullable;

/**
 * Whether an entity carries any Stature state worth writing to its save.
 *
 * <p>Nothing is written for an entity that is still vanilla in every respect, so a
 * frame-less world does not carry Stature's keys on every mob in it. The predicate is
 * static and game-free so tests can drive it directly: every early return in the save
 * path must go through here, and every case here is pinned by {@code SaveGateTest}.
 *
 * <p>Parts count the same as channels: an entity shaped only by {@code /stature part}
 * sets no frame source, so without the parts leg a parts-only entity would hit the
 * early return and lose its silhouette on unload.
 */
public final class SaveGate {

    private SaveGate() {
    }

    /**
     * True when any third travels: a non-uniform box, an eye override, a frame, a
     * non-default source, channel overrides, or part factors.
     */
    public static boolean shouldWrite(
            ScaleAxes axes,
            float eyeHeight,
            @Nullable Identifier frame,
            FrameSource source,
            boolean hasChannels,
            boolean hasParts) {
        if (hasChannels || hasParts) {
            return true;
        }
        if (!axes.isUniform() || eyeHeight != StatureScale.NO_EYE_HEIGHT || frame != null) {
            return true;
        }
        return source != FrameSource.NONE && source != FrameSource.DEFAULT;
    }
}
