package dev.ellipog.stature.common.frame;

/**
 * Why an entity has the frame it has.
 *
 * <p>The order here is the precedence rule as well as a list of states: an explicit choice always
 * beats a pack declaration, and a pack declaration always beats the vanilla default. A mod that
 * sizes other mods' entities gets its own level, INTEGRATION: below an operator's explicit choice,
 * above every pack declaration, and named for what it is rather than for any one consumer.
 */
public enum FrameSource {
    /** Nothing has been decided yet. Only unclaimed entities may be assigned automatically. */
    NONE,

    /** A direct size or clear choice by an operator or API caller. Never overridden automatically. */
    MANUAL,

    /** A named frame applied by an operator or an API caller. Never overridden. */
    COMMAND,

    /**
     * A named frame applied by another mod through the integration API. Outranks pack declarations,
     * never outranks an operator, and is re-asserted by its owner rather than re-evaluated.
     */
    INTEGRATION,

    /** A frame that won through an entity-type tag. Re-evaluated when packs reload. */
    TAG,

    /** A frame that won through an exact entity type. Re-evaluated when packs reload. */
    TYPE,

    /** Checked against the loaded packs and deliberately left vanilla. Re-evaluated when packs reload. */
    DEFAULT
}
