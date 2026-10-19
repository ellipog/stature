package dev.ellipog.stature.common.parts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

class RigRegistryTest {

    private static Map<String, RigGroup> build(String json) throws Exception {
        return RigRegistry.build(
                JsonParser.parseString(json).getAsJsonObject(), "test", name -> null);
    }

    @Test
    void theDefaultHumanoidNamesAnatomy() {
        RigRegistry registry = new RigRegistry();
        assertEquals(List.of("head", "hat"), registry.resolve("stature:humanoid", "head"));
        assertEquals(List.of("left_arm"), registry.resolve("stature:humanoid", "arm.left"));
        assertEquals(List.of(""), registry.resolve("stature:humanoid", "all"));
        assertTrue(registry.hasGroup("stature:humanoid", "torso"));
        assertFalse(registry.hasGroup("stature:humanoid", "wing.left"));
    }

    @Test
    void rawPathsPassThroughWhileUnknownRigsResolveNothing() {
        RigRegistry registry = new RigRegistry();
        assertEquals(List.of("body.left_arm"), registry.resolve("stature:humanoid", "body.left_arm"));
        assertTrue(registry.resolve("pack:missing", "head").isEmpty());
    }

    @Test
    void theDefaultHeadCoversTheTopOfTheBox() {
        RigRegistry registry = new RigRegistry();
        List<Float> box = registry.boxOf("stature:humanoid", "head");
        assertEquals(6, box.size());
        assertTrue(box.get(4) <= 1.0F && box.get(1) >= 0.5F);
        assertNull(registry.boxOf("stature:humanoid", "wing.left"));
        assertNull(registry.boxOf("pack:missing", "head"));
    }

    @Test
    void groupsMergeOverTheParent() throws Exception {
        Map<String, RigGroup> child = RigRegistry.build(
                JsonParser.parseString("{\"parent\": \"stature:humanoid\","
                        + " \"groups\": {\"head\": [\"skull\"], \"wing.left\": [\"left_wing\"]}}").getAsJsonObject(),
                "test",
                name -> "stature:humanoid".equals(name)
                        ? Map.of("head", new RigGroup(List.of("head"), RigRegistry.FULL_BOX),
                                "torso", new RigGroup(List.of("body"), RigRegistry.FULL_BOX))
                        : null);
        assertEquals(List.of("skull"), child.get("head").paths());
        assertEquals(List.of("body"), child.get("torso").paths());
        assertEquals(List.of("left_wing"), child.get("wing.left").paths());
        assertEquals(RigRegistry.FULL_BOX, child.get("wing.left").box());
    }

    @Test
    void aGroupCanCarryItsOwnBox() throws Exception {
        Map<String, RigGroup> rig = build(
                "{\"groups\": {\"head\": {\"paths\": [\"skull\"], \"box\": [0, 0.7, 0, 1, 1, 1]}}}");
        assertEquals(List.of("skull"), rig.get("head").paths());
        assertEquals(List.of(0.0F, 0.7F, 0.0F, 1.0F, 1.0F, 1.0F), rig.get("head").box());
    }

    @Test
    void anUnknownParentIsReportedAsAbsent() throws Exception {
        assertNull(RigRegistry.build(
                JsonParser.parseString("{\"parent\": \"pack:missing\", \"groups\": {}}").getAsJsonObject(),
                "test",
                name -> null));
    }

    @Test
    void malformedFilesAreRejected() {
        assertThrows(Exception.class, () -> build("{\"groups\": {\"head\": \"skull\"}}"));
        assertThrows(Exception.class, () -> build("{\"groups\": {\"head\": [\"a..b\"]}}"));
        assertThrows(Exception.class, () -> build("{\"groups\": [\"head\"]}"));
        assertThrows(Exception.class, () -> build("[\"head\"]"));
        assertThrows(Exception.class, () -> build("{\"groups\": {\"head\": {\"paths\": [\"skull\"], \"box\": [0, 1]}}}"));
        assertThrows(Exception.class, () -> build("{\"groups\": {\"head\": {\"box\": [0, 0, 0, 1, 1, 1]}}}"));
        assertThrows(Exception.class,
                () -> build("{\"groups\": {\"head\": {\"paths\": [\"skull\"], \"box\": [0, 0.9, 0, 1, 0.5, 1]}}}"));
    }

    @Test
    void pathSyntax() {
        assertTrue(RigRegistry.validPath(""));
        assertTrue(RigRegistry.validPath("head"));
        assertTrue(RigRegistry.validPath("body.left_arm"));
        assertFalse(RigRegistry.validPath(".head"));
        assertFalse(RigRegistry.validPath("head."));
        assertFalse(RigRegistry.validPath("a..b"));
        assertFalse(RigRegistry.validPath(null));
    }
}
