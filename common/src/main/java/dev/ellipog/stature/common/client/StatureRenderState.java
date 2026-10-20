package dev.ellipog.stature.common.client;

import java.util.Map;

import dev.ellipog.stature.api.ScaleAxes;

/**
 * What a render state can be asked about the shape of the thing it is about to draw.
 *
 * <p>26.3 draws a state, not an entity: {@code extractRenderState} fills one and {@code submit}
 * draws it, and {@code submit} never sees the entity. {@code LivingEntityRenderState} carries a
 * single {@code scale} float and no room for three, so the shape is copied onto the state on the way
 * in and read back on the way out.
 *
 * <p>The game ticks at 20 Hz but draws at display rate. Each client entity therefore keeps a
 * {@link RenderSampler} with the last two ticks' samples, and each frame pushes the blend at its
 * partial tick here: the hitbox stays exactly on the server's numbers while the picture glides
 * between them, which is the same split vanilla itself uses for positions. The state itself keeps
 * no history -- it is rebuilt every frame, so anything remembered on it would never be read.
 *
 * <p>Both scales travel because the inventory paperdoll overwrites {@code state.scale} with 1.
 * The submit hook draws {@code incoming × (visual / attribute) × shape}, so a forced 1 stays 1
 * and only the shape -- and any {@code stature:model} ratio -- applies. Outside the inventory
 * the incoming uniform <em>is</em> the attribute scale, so the ratio cancels and the picture is
 * the blended visual times the shape.
 *
 * <p>Channel overrides ride the same way, one step simpler: they change rarely and nobody blends
 * them, so each frame replaces the whole map rather than versioning it. An empty map copies for
 * free, and a small one costs less than the frame already spends.
 *
 * <p>Implemented by a mixin, hence the {@code stature$} prefix. This interface names no client class
 * itself, so it is safe to load on a dedicated server even though only a client ever uses it.
 */
public interface StatureRenderState {

    /**
     * Records this frame's blended sample. Called from {@code extractRenderState}, which always
     * runs before {@code submit} for the same frame.
     *
     * @param visualScale    the blended visual scale ({@code stature:model} or the base)
     * @param attributeScale the entity's base scale this tick, for the inventory ratio
     * @param shape          the blended shape
     */
    void stature$pushVisual(float visualScale, float attributeScale, ScaleAxes shape);

    /** The blended visual scale, or 1.0 when nothing has been pushed yet. */
    float stature$visualScale();

    /** The base scale sampled alongside the visual, or 1.0 when nothing is pushed yet. */
    float stature$attributeScale();

    /** The blended shape, or {@link ScaleAxes#UNIFORM} when nothing is pushed yet. */
    ScaleAxes stature$visualShape();

    /** The channel overrides known this frame. Replaced whole, never merged. */
    void stature$pushChannels(Map<String, Float> channels);

    /**
     * The part factors known this frame, keyed by model path. Same replace-whole rule as
     * channels: the server owns the map and this frame either has the latest copy or nothing.
     */
    void stature$pushParts(Map<String, ScaleAxes> parts);

    /** The pushed part factors, or an empty map when no frame pushed any. */
    Map<String, ScaleAxes> stature$partFactors();
    /** Whether the visibility channel hides this entity: set below one half, shown otherwise. */
    boolean stature$hidden();

    /** The held-item factor: the override, or exactly 1 when unset or corrupt. */
    float stature$heldFactor();

    /** The shadow factor: the override, or exactly 1 when unset or corrupt. */
    float stature$shadowFactor();
}
