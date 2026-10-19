package dev.ellipog.stature.common.frame;

/**
 * Why an entity has the frame it has.
 *
 * <p>The order here is the precedence rule as well as a list of states: an explicit choice always
 * beats a pack declaration, and a pack declaration always beats the vanilla default. Integrations
 * that choose a size for another mod's entity, including a future Kindred hook, belong on the
 * explicit path rather than inventing a new level.
 */
public enum FrameSource {
    /** Nothing has been decided yet. Only unclaimed entities may be assigned automatically. */
    NONE,

    /** A direct size or clear choice by an operator or API caller. Never overridden automatically. */
    MANUAL,

    /** A named frame applied by an operator, an API caller, or a future integration. Never overridden. */
    COMMAND,

    /** A frame that won through an entity-type tag. Re-evaluated when packs reload. */
    TAG,

    /** A frame that won through an exact entity type. Re-evaluated when packs reload. */
    TYPE,

    /** Checked against the loaded packs and deliberately left vanilla. Re-evaluated when packs reload. */
    DEFAULT
}
