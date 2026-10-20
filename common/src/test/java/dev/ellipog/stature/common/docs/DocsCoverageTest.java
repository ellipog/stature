package dev.ellipog.stature.common.docs;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.ellipog.stature.common.channel.ChannelRegistry;

import org.junit.jupiter.api.Test;

/**
 * Pins the two tables a reader checks first (plan M7): every command literal in
 * {@code StatureCommands} is in {@code docs/commands.md}, and every built-in channel is in
 * {@code docs/channels.md}.
 *
 * <p>The command side reads the source's literal calls rather than a hand-written list. Building
 * the Brigadier tree in a unit test would bootstrap the game's registries, and these tests are
 * deliberately game-free; the literals are the same strings either way.
 */
class DocsCoverageTest {

    private static final Pattern COMMAND_LITERAL =
            Pattern.compile("Commands\\.literal\\(\"([^\"]+)\"\\)");

    /** The easing branches are built from a variable name, so read the call sites. */
    private static final Pattern EASING_LITERAL =
            Pattern.compile("easing\\(\"([^\"]+)\"");

    @Test
    void everyCommandLiteralIsDocumented() throws IOException {
        String source = readRepoFile(
                "common/src/main/java/dev/ellipog/stature/common/command/StatureCommands.java");

        Set<String> literals = new TreeSet<>();
        Matcher commands = COMMAND_LITERAL.matcher(source);
        while (commands.find()) {
            literals.add(commands.group(1));
        }
        Matcher easings = EASING_LITERAL.matcher(source);
        while (easings.find()) {
            literals.add(easings.group(1));
        }
        assertFalse(literals.isEmpty(), "StatureCommands declares no literals");

        String commandsPage = readRepoFile("docs/commands.md");
        for (String literal : literals) {
            assertTrue(mentions(commandsPage, literal),
                    "docs/commands.md does not mention the command literal \"" + literal + "\"");
        }
    }

    @Test
    void everyChannelIdIsDocumented() throws IOException {
        String channels = readRepoFile("docs/channels.md");
        for (String id : ChannelRegistry.instance().ids()) {
            assertTrue(mentions(channels, id),
                    "docs/channels.md does not mention the " + id + " channel");
        }
    }

    /** A word boundary that treats an id's colon, dot and underscore as part of the word. */
    private static boolean mentions(String markdown, String word) {
        return Pattern.compile("(?<![A-Za-z0-9_-])" + Pattern.quote(word) + "(?![A-Za-z0-9_-])")
                .matcher(markdown)
                .find();
    }

    /**
     * A repository file from wherever the test runs. Gradle runs this module from {@code common/}
     * and an IDE may run it from the repository root, so walk up instead of assuming either.
     */
    private static String readRepoFile(String relative) throws IOException {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve(relative);
            if (Files.isRegularFile(candidate)) {
                return Files.readString(candidate, StandardCharsets.UTF_8);
            }
            current = current.getParent();
        }
        throw new AssertionError(relative + " not found above "
                + Path.of(System.getProperty("user.dir")).toAbsolutePath());
    }
}
