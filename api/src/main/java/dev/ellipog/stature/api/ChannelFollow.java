package dev.ellipog.stature.api;

/**
 * What a channel value follows when nobody overrides it (plan §14.9).
 *
 * <p>Game-free like everything else in this module: the basis is passed in as plain numbers, so
 * resolving a channel never needs the game.
 */
public enum ChannelFollow {
    /** The uniform scale: reach, step, motion and the rest of the table's linear followers. */
    BASE,

    /** The box's width multiplier, for channels that track girth rather than size. */
    WIDTH,

    /** The box's height multiplier, for channels that track stature. */
    HEIGHT,

    /** The box's depth multiplier, for channels that track bulk. */
    DEPTH,

    /** Follows nothing: the channel is off unless it carries an explicit value. */
    NONE
}
