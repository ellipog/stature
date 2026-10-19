package dev.ellipog.stature.common.frame;

/**
 * A frame file that does not say what a frame has to say.
 *
 * <p>Checked rather than assumed, and the message names the field: a pack author's only feedback is
 * the log, and "invalid frame" sends them to read the schema while "size must be three numbers,
 * got 2" sends them to the line that is wrong.
 */
public class FrameFormatException extends Exception {

    private static final long serialVersionUID = 1L;

    public FrameFormatException(String message) {
        super(message);
    }

    public FrameFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
