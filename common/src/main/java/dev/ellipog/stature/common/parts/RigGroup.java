package dev.ellipog.stature.common.parts;

import java.util.List;

/**
 * One named group in a rig: the model paths it covers for the silhouette, and the fractional
 * box it covers for hit tests, in fractions of the entity's box.
 */
public record RigGroup(List<String> paths, List<Float> box) {

    public RigGroup {
        paths = List.copyOf(paths);
        box = List.copyOf(box);
    }
}
