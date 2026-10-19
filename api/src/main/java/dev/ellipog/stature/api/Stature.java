package dev.ellipog.stature.api;

/**
 * Stature's entry vocabulary: the constants every module agrees on.
 *
 * <p>Deliberately tiny. This module is pure Java with no Minecraft dependency, so a third-party mod
 * compiles against it without touching a game artifact. Anything naming a Minecraft class lives in
 * {@code stature-common} instead, and plan §14 says which is which.
 */
public final class Stature {

    /** The mod id, and the namespace frames, rigs and commands live under. */
    public static final String MOD_ID = "stature";

    private Stature() {
    }
}
