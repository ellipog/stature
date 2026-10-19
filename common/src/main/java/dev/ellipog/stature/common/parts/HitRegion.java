package dev.ellipog.stature.common.parts;

/**
 * A hit that landed inside a region: which part, under what tag, scaled by how much, and when.
 *
 * <p>Written by the hit hooks, read by damage application in the same tick, and left in place
 * afterwards as the last-hit record for downstream consumers.
 */
public record HitRegion(String part, String tag, float multiplier, int tick) {
}
