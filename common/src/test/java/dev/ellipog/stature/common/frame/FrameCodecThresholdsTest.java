package dev.ellipog.stature.common.frame;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.util.Map;

import net.minecraft.resources.Identifier;

import org.junit.jupiter.api.Test;

class FrameCodecThresholdsTest {

    private static FrameFile parse(String json) throws Exception {
        return FrameCodec.parse("test", Identifier.parse("test:frame"), new StringReader(json));
    }

    @Test
    void absentThresholdsLeaveNulls() throws Exception {
        FrameFile file = parse("{\"size\": [1.0, 1.0]}");
        assertNull(file.thresholdMicro());
        assertNull(file.thresholdMacro());
        assertTrue(file.thresholdFlags().isEmpty());
    }

    @Test
    void edgesAndFlagsParse() throws Exception {
        FrameFile file = parse("{\"size\": [1.0, 1.0],"
                + " \"thresholds\": {\"micro\": 0.5, \"macro\": 1.5, \"trampling\": false}}");
        assertEquals(0.5F, file.thresholdMicro());
        assertEquals(1.5F, file.thresholdMacro());
        assertEquals(Map.of("trampling", false), file.thresholdFlags());
    }

    @Test
    void invertedEdgesFailTheFile() {
        assertThrows(FrameFormatException.class,
                () -> parse("{\"size\": [1.0, 1.0], \"thresholds\": {\"micro\": 2.0, \"macro\": 1.0}}"));
    }

    @Test
    void nonBooleanFlagsFailTheFile() {
        assertThrows(FrameFormatException.class,
                () -> parse("{\"size\": [1.0, 1.0], \"thresholds\": {\"trampling\": \"sometimes\"}}"));
    }

    @Test
    void firstPersonArmsDefaultsToUnset() throws Exception {
        assertNull(parse("{\"size\": [1.0, 1.0]}").firstPersonArms());
    }

    @Test
    void firstPersonArmsParses() throws Exception {
        assertEquals(Boolean.FALSE,
                parse("{\"size\": [1.0, 1.0], \"first_person_arms\": false}").firstPersonArms());
        assertEquals(Boolean.TRUE,
                parse("{\"size\": [1.0, 1.0], \"first_person_arms\": true}").firstPersonArms());
    }

    @Test
    void nonBooleanFirstPersonArmsFailsTheFile() {
        assertThrows(FrameFormatException.class,
                () -> parse("{\"size\": [1.0, 1.0], \"first_person_arms\": \"sometimes\"}"));
    }
}
