package dev.ellipog.stature.common.frame;

/**
 * One hurt region on a frame: a hit landing inside the part's box deals scaled damage and is
 * tagged for downstream consumers (plan §14.7).
 *
 * <p>The part names a rig group, not a model path: the rig supplies both the paths for the
 * silhouette and the fractional box for the hit test. The multiplier is zero or positive; zero
 * means the region takes no damage.
 */
public record FrameRegion(String part, float multiplier, String tag) {

    public FrameRegion {
        if (part == null || part.isBlank()) {
            throw new IllegalArgumentException("a region needs a part group name");
        }
        if (!(multiplier >= 0.0F) || !Float.isFinite(multiplier)) {
            throw new IllegalArgumentException(
                    "region \"" + part + "\" needs a finite non-negative multiplier, got " + multiplier);
        }
        if (tag == null || tag.isBlank()) {
            throw new IllegalArgumentException("a region needs a tag");
        }
    }
}
