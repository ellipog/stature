package dev.ellipog.stature.common.command;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import dev.ellipog.stature.api.Easing;
import dev.ellipog.stature.api.ScaleAxes;
import dev.ellipog.stature.api.ScaleLimits;
import dev.ellipog.stature.api.ScaleState;
import dev.ellipog.stature.common.ScaleService;
import dev.ellipog.stature.common.StatureScale;
import dev.ellipog.stature.common.channel.ChannelRegistry;
import dev.ellipog.stature.common.channel.ChannelService;
import dev.ellipog.stature.common.parts.PartService;
import dev.ellipog.stature.common.parts.RigRegistry;
import dev.ellipog.stature.common.parts.RigRegistry;
import dev.ellipog.stature.common.frame.FrameService;
import dev.ellipog.stature.common.frame.StatureFrames;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import org.jetbrains.annotations.Nullable;

/**
 * The {@code /stature} command tree (plan §14.5 and §14.8).
 *
 * <pre>
 * /stature set    &lt;targets&gt; &lt;scale&gt;                        instantly
 * /stature set    &lt;targets&gt; &lt;scale&gt; &lt;ticks&gt; [easing]         eased
 * /stature size   &lt;targets&gt; &lt;width&gt; &lt;height&gt; &lt;depth&gt; [ticks]  the box's shape
 * /stature frame  apply &lt;targets&gt; &lt;frame&gt; [ticks]            a frame from a data pack
 * /stature frame  clear &lt;targets&gt;                            back to vanilla proportions
 * /stature frame  list                                       what the packs declared
 * /stature channel &lt;targets&gt; &lt;channel&gt; &lt;value&gt;           set one channel now
 * /stature channel &lt;targets&gt; &lt;channel&gt; reset               back to following the base
 * /stature channel &lt;targets&gt; list                          what each channel says
 * /stature get    [&lt;target&gt;]                                 the size and the resulting box
 * /stature reload                                            re-read the packs
 * </pre>
 *
 * <p>The scale argument is bounded by Brigadier to {@link ScaleLimits}, so an out-of-range number is
 * refused before any code here runs and the message names the range rather than the caller's mistake.
 * That is also the limit of the guard: the API behind this is unbound (§14.5), and the clamp is the
 * command's job, not the service's policy.
 *
 * <p>{@code set}, {@code size} and {@code frame} are separate commands rather than one with optional
 * parts, because they are separate ideas: one changes how big the entity is, one changes its
 * proportions, and one hands the job to a pack. A command that did all three at once would make
 * "keep my proportions" impossible to ask for.
 *
 * <p>Registration itself is the loaders' business -- Fabric's {@code CommandRegistrationCallback} and
 * NeoForge's {@code RegisterCommandsEvent} -- so this class knows the tree and nothing about who
 * called it.
 */
public final class StatureCommands {

    /** Ten minutes is far longer than any transition worth watching, and keeps a typo bounded. */
    private static final int MAX_TICKS = 20 * 60;

    private StatureCommands() {
    }

    /** Registers the tree. Safe to call once per loader, at the loader's own moment. */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("stature")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS));

        setBranch(root);
        sizeBranch(root);
        frameBranch(root);
        channelBranch(root);
        partBranch(root);

        root.then(Commands.literal("get")
                .executes(context -> get(context, context.getSource().getEntity()))
                .then(Commands.argument("target", EntityArgument.entity())
                        .executes(context -> get(context, EntityArgument.getEntity(context, "target")))));

        root.then(Commands.literal("reload").executes(StatureCommands::reload));

        dispatcher.register(root);
    }

    private static void setBranch(LiteralArgumentBuilder<CommandSourceStack> root) {
        RequiredArgumentBuilder<CommandSourceStack, Double> scale =
                Commands.argument("scale", DoubleArgumentType.doubleArg(ScaleLimits.MIN, ScaleLimits.MAX))
                        // No ticks means now, and "now" is linear because there is no curve to walk:
                        // the eased default only means something once a duration exists.
                        .executes(context -> set(context, 0, Easing.LINEAR));

        scale.then(Commands.argument("ticks", IntegerArgumentType.integer(0, MAX_TICKS))
                .executes(context -> set(
                        context, IntegerArgumentType.getInteger(context, "ticks"), Easing.EASE_OUT_CUBIC))
                .then(easing("linear", Easing.LINEAR))
                .then(easing("cubic", Easing.EASE_OUT_CUBIC))
                .then(easing("elastic", Easing.ELASTIC)));

        root.then(Commands.literal("set")
                .then(Commands.argument("targets", EntityArgument.entities()).then(scale)));
    }

    private static void sizeBranch(LiteralArgumentBuilder<CommandSourceStack> root) {
        RequiredArgumentBuilder<CommandSourceStack, Double> depth =
                Commands.argument("depth", DoubleArgumentType.doubleArg(ScaleLimits.MIN, ScaleLimits.MAX))
                        .executes(context -> shape(context, 0));

        depth.then(Commands.argument("ticks", IntegerArgumentType.integer(0, MAX_TICKS))
                .executes(context -> shape(context, IntegerArgumentType.getInteger(context, "ticks"))));

        RequiredArgumentBuilder<CommandSourceStack, Double> height =
                Commands.argument("height", DoubleArgumentType.doubleArg(ScaleLimits.MIN, ScaleLimits.MAX))
                        .then(depth);

        RequiredArgumentBuilder<CommandSourceStack, Double> width =
                Commands.argument("width", DoubleArgumentType.doubleArg(ScaleLimits.MIN, ScaleLimits.MAX))
                        .then(height);

        root.then(Commands.literal("size")
                .then(Commands.argument("targets", EntityArgument.entities()).then(width)));
    }

    private static void frameBranch(LiteralArgumentBuilder<CommandSourceStack> root) {
        RequiredArgumentBuilder<CommandSourceStack, Identifier> frame =
                Commands.argument("frame", IdentifierArgument.id())
                        .suggests(StatureCommands::suggestFrames)
                        .executes(context -> applyFrame(context, 0));

        frame.then(Commands.argument("ticks", IntegerArgumentType.integer(0, MAX_TICKS))
                .executes(context -> applyFrame(context, IntegerArgumentType.getInteger(context, "ticks"))));

        root.then(Commands.literal("frame")
                .then(Commands.literal("apply")
                        .then(Commands.argument("targets", EntityArgument.entities()).then(frame)))
                .then(Commands.literal("clear")
                        .then(Commands.argument("targets", EntityArgument.entities())
                                .executes(StatureCommands::clearFrames)))
                .then(Commands.literal("list").executes(StatureCommands::listFrames)));
    }

    /**
     * One channel at a time, set now rather than eased: a channel override is a decision about a
     * number, and the service eases API-driven changes while the command states facts.
     *
     * <p>The value is deliberately unbounded -- §14.5 leaves numbers to the API, and the command
     * refuses only what is not a number at all. A pack or a future frame field that wants an
     * eased channel change calls the service, not this tree.
     */
    private static void channelBranch(LiteralArgumentBuilder<CommandSourceStack> root) {
        // Channel ids are namespace:path shaped, and StringArgumentType.string() is single-word:
        // it stops at the colon and the parse dies there. IdentifierArgument speaks the same shape
        // as the frame argument two branches up, so both id arguments behave the same way.
        RequiredArgumentBuilder<CommandSourceStack, Identifier> channel =
                Commands.argument("channel", IdentifierArgument.id())
                        .suggests(StatureCommands::suggestChannels);

        RequiredArgumentBuilder<CommandSourceStack, Double> value =
                Commands.argument("value", DoubleArgumentType.doubleArg())
                        .executes(StatureCommands::setChannel);

        root.then(Commands.literal("channel")
                .then(Commands.argument("targets", EntityArgument.entities())
                        .then(Commands.literal("list").executes(StatureCommands::listChannels))
                        .then(channel
                                .then(value)
                                .then(Commands.literal("reset").executes(StatureCommands::resetChannel)))));
    }

    /**
     * One part at a time, as a group name or a raw dot path. Groups resolve through the entity's
     * rig at set time and travel as paths after that, so a later rig change never reinterprets
     * what was set. Dots are legal here, so unlike channels this argument is a plain string.
     */
    private static void partBranch(LiteralArgumentBuilder<CommandSourceStack> root) {
        RequiredArgumentBuilder<CommandSourceStack, String> part =
                Commands.argument("part", StringArgumentType.string())
                        .suggests(StatureCommands::suggestParts);

        RequiredArgumentBuilder<CommandSourceStack, Double> depth =
                Commands.argument("depth", DoubleArgumentType.doubleArg(ScaleLimits.MIN, ScaleLimits.MAX))
                        .executes(context -> setPart(context, 0));

        depth.then(Commands.argument("ticks", IntegerArgumentType.integer(0, MAX_TICKS))
                .executes(context -> setPart(context, IntegerArgumentType.getInteger(context, "ticks"))));

        RequiredArgumentBuilder<CommandSourceStack, Double> height =
                Commands.argument("height", DoubleArgumentType.doubleArg(ScaleLimits.MIN, ScaleLimits.MAX))
                        .then(depth);

        RequiredArgumentBuilder<CommandSourceStack, Double> width =
                Commands.argument("width", DoubleArgumentType.doubleArg(ScaleLimits.MIN, ScaleLimits.MAX))
                        .then(height);

        root.then(Commands.literal("part")
                .then(Commands.argument("targets", EntityArgument.entities())
                        .then(Commands.literal("list").executes(StatureCommands::listParts))
                        .then(Commands.literal("reset").executes(StatureCommands::resetAllParts))
                        .then(part
                                .then(width)
                                .then(Commands.literal("reset").executes(StatureCommands::resetPart)))));
    }

    /**
     * One named easing, as a literal branch of the tree.
     *
     * <p>A literal per curve rather than a parsed string: the command tree then <em>is</em> the list
     * of curves, so tab-completion offers them, a typo is refused by Brigadier with the alternatives,
     * and adding a curve to {@link Easing} without adding it here is visible rather than silent.
     */
    private static LiteralArgumentBuilder<CommandSourceStack> easing(String name, Easing easing) {
        return Commands.literal(name)
                .executes(context -> set(context, IntegerArgumentType.getInteger(context, "ticks"), easing));
    }

    private static int set(CommandContext<CommandSourceStack> context, int ticks, Easing easing)
            throws CommandSyntaxException {
        Collection<? extends Entity> targets = EntityArgument.getEntities(context, "targets");
        float scale = (float) DoubleArgumentType.getDouble(context, "scale");

        int changed = 0;
        for (LivingEntity living : living(targets)) {
            // The shape is kept: "make it twice as big" must not silently make it square again.
            FrameService.setManualScale(living, scale, ticks, easing);
            changed++;
            context.getSource().sendSuccess(
                    () -> Component.translatable(
                            "stature.command.set.success", living.getDisplayName(), number(scale)),
                    true);
        }
        return report(context, changed);
    }

    private static int shape(CommandContext<CommandSourceStack> context, int ticks) throws CommandSyntaxException {
        Collection<? extends Entity> targets = EntityArgument.getEntities(context, "targets");
        float width = (float) DoubleArgumentType.getDouble(context, "width");
        float height = (float) DoubleArgumentType.getDouble(context, "height");
        float depth = (float) DoubleArgumentType.getDouble(context, "depth");
        ScaleAxes axes = new ScaleAxes(width, height, depth);

        int changed = 0;
        for (LivingEntity living : living(targets)) {
            FrameService.setManualShape(living, axes, ticks, Easing.EASE_OUT_CUBIC);
            changed++;
            context.getSource().sendSuccess(
                    () -> Component.translatable(
                            "stature.command.size.success",
                            living.getDisplayName(), number(width), number(height), number(depth)),
                    true);
        }
        return report(context, changed);
    }

    /**
     * The loaded frame names, offered as completions.
     *
     * <p>Empty when nothing is loaded, which is itself an answer: a pack that put its files in the
     * wrong directory gets no suggestions rather than a wrong one.
     */
    private static CompletableFuture<Suggestions> suggestFrames(
            CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(
                StatureFrames.ids().stream().map(Identifier::toString).sorted().toList(), builder);
    }

    /** The declared channel names, offered as completions. Never empty: built-ins ship. */
    private static CompletableFuture<Suggestions> suggestChannels(
            CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(ChannelRegistry.instance().ids(), builder);
    }

    private static int setChannel(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Collection<? extends Entity> targets = EntityArgument.getEntities(context, "targets");
        String id = IdentifierArgument.getId(context, "channel").toString();
        float value = (float) DoubleArgumentType.getDouble(context, "value");

        // Two different refusals, said differently: a name nobody declared, or a number that is
        // not a number. The service would throw the first too, but "try list" belongs to the
        // command, which is what knows the list exists.
        if (ChannelRegistry.instance().get(id) == null) {
            context.getSource().sendFailure(Component.translatable("stature.command.channel.unknown", id));
            return 0;
        }
        if (!Float.isFinite(value)) {
            context.getSource().sendFailure(Component.translatable("stature.command.channel.badvalue"));
            return 0;
        }

        int changed = 0;
        for (LivingEntity living : living(targets)) {
            ChannelService.setChannel(living, id, value, 0, Easing.LINEAR);
            changed++;
            context.getSource().sendSuccess(
                    () -> Component.translatable(
                            "stature.command.channel.set.success",
                            living.getDisplayName(), id, number(value)),
                    true);
        }
        return report(context, changed);
    }

    private static int resetChannel(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Collection<? extends Entity> targets = EntityArgument.getEntities(context, "targets");
        String id = IdentifierArgument.getId(context, "channel").toString();

        if (ChannelRegistry.instance().get(id) == null) {
            context.getSource().sendFailure(Component.translatable("stature.command.channel.unknown", id));
            return 0;
        }

        int changed = 0;
        for (LivingEntity living : living(targets)) {
            ChannelService.resetChannel(living, id);
            changed++;
            context.getSource().sendSuccess(
                    () -> Component.translatable(
                            "stature.command.channel.reset.success", living.getDisplayName(), id),
                    true);
        }
        return report(context, changed);
    }

    private static int listChannels(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Collection<? extends Entity> targets = EntityArgument.getEntities(context, "targets");

        int listed = 0;
        for (LivingEntity living : living(targets)) {
            Set<String> overrides =
                    ((StatureScale) living).stature$channelOverrides().keySet();
            List<String> entries = new ArrayList<>();
            for (String id : ChannelRegistry.instance().ids()) {
                String entry = id + "=" + number(ChannelService.resolvedValue(living, id));
                entries.add(overrides.contains(id) ? entry + " (override)" : entry);
            }
            context.getSource().sendSuccess(
                    () -> Component.translatable(
                            "stature.command.channel.list", living.getDisplayName(), String.join(", ", entries)),
                    false);
            listed++;
        }
        return report(context, listed);
    }

    /** The default rig's group names, offered as completions. Raw dot paths type blind. */
    private static CompletableFuture<Suggestions> suggestParts(
            CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(
                RigRegistry.instance().groups(PartService.DEFAULT_RIG), builder);
    }

    private static int setPart(CommandContext<CommandSourceStack> context, int ticks)
            throws CommandSyntaxException {
        Collection<? extends Entity> targets = EntityArgument.getEntities(context, "targets");
        String name = StringArgumentType.getString(context, "part");
        float width = (float) DoubleArgumentType.getDouble(context, "width");
        float height = (float) DoubleArgumentType.getDouble(context, "height");
        float depth = (float) DoubleArgumentType.getDouble(context, "depth");

        int changed = 0;
        for (LivingEntity living : living(targets)) {
            List<String> paths = RigRegistry.instance().resolve(PartService.rigOf(living), name);
            if (paths.isEmpty()) {
                continue;
            }
            ScaleAxes factors;
            try {
                factors = new ScaleAxes(width, height, depth);
            } catch (IllegalArgumentException bad) {
                context.getSource().sendFailure(
                        Component.translatable("stature.command.part.badvalue"));
                return 0;
            }
            for (String path : paths) {
                PartService.setPart(living, path, factors, ticks, Easing.EASE_OUT_CUBIC);
            }
            changed++;
            context.getSource().sendSuccess(
                    () -> Component.translatable(
                            "stature.command.part.set.success",
                            living.getDisplayName(), name, number(width), number(height), number(depth)),
                    true);
        }
        if (changed == 0) {
            context.getSource().sendFailure(
                    Component.translatable("stature.command.part.unknown", name));
            return 0;
        }
        return changed;
    }

    private static int resetPart(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Collection<? extends Entity> targets = EntityArgument.getEntities(context, "targets");
        String name = StringArgumentType.getString(context, "part");

        int changed = 0;
        for (LivingEntity living : living(targets)) {
            List<String> paths = RigRegistry.instance().resolve(PartService.rigOf(living), name);
            if (paths.isEmpty()) {
                continue;
            }
            for (String path : paths) {
                PartService.resetPart(living, path);
            }
            changed++;
            context.getSource().sendSuccess(
                    () -> Component.translatable(
                            "stature.command.part.reset.success", living.getDisplayName(), name),
                    true);
        }
        if (changed == 0) {
            context.getSource().sendFailure(
                    Component.translatable("stature.command.part.unknown", name));
            return 0;
        }
        return changed;
    }

    private static int resetAllParts(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Collection<? extends Entity> targets = EntityArgument.getEntities(context, "targets");

        int changed = 0;
        for (LivingEntity living : living(targets)) {
            PartService.resetAllParts(living);
            changed++;
            context.getSource().sendSuccess(
                    () -> Component.translatable(
                            "stature.command.part.reset.all.success", living.getDisplayName()),
                    true);
        }
        return report(context, changed);
    }

    private static int listParts(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Collection<? extends Entity> targets = EntityArgument.getEntities(context, "targets");

        int listed = 0;
        for (LivingEntity living : living(targets)) {
            Map<String, ScaleAxes> factors = ((StatureScale) living).stature$partFactors();
            if (factors.isEmpty()) {
                context.getSource().sendSuccess(
                        () -> Component.translatable(
                                "stature.command.part.list.empty", living.getDisplayName()),
                        false);
                listed++;
                continue;
            }
            List<String> entries = new ArrayList<>();
            for (Map.Entry<String, ScaleAxes> entry : factors.entrySet()) {
                ScaleAxes shape = entry.getValue();
                entries.add((entry.getKey().isEmpty() ? "all" : entry.getKey()) + "="
                        + number(shape.width()) + " x " + number(shape.height()) + " x " + number(shape.depth()));
            }
            context.getSource().sendSuccess(
                    () -> Component.translatable(
                            "stature.command.part.list", living.getDisplayName(), String.join(", ", entries)),
                    false);
            listed++;
        }
        return report(context, listed);
    }

    private static int applyFrame(CommandContext<CommandSourceStack> context, int ticks) throws CommandSyntaxException {
        Collection<? extends Entity> targets = EntityArgument.getEntities(context, "targets");
        Identifier frameId = IdentifierArgument.getId(context, "frame");

        int applied = 0;
        for (LivingEntity living : living(targets)) {
            if (FrameService.apply(living, frameId, ticks)) {
                applied++;
                context.getSource().sendSuccess(
                        () -> Component.translatable(
                                "stature.command.frame.applied", frameId.toString(), living.getDisplayName()),
                        true);
            }
        }

        if (applied == 0) {
            // Two different nothings, said differently: a name nobody has, or a selector that matched
            // nothing this command can size.
            boolean known = StatureFrames.get(frameId) != null;
            context.getSource().sendFailure(known
                    ? Component.translatable("stature.command.frame.none")
                    : Component.translatable("stature.command.frame.unknown", frameId.toString()));
            return 0;
        }
        return applied;
    }

    private static int clearFrames(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Collection<? extends Entity> targets = EntityArgument.getEntities(context, "targets");

        int cleared = 0;
        for (LivingEntity living : living(targets)) {
            FrameService.clear(living);
            cleared++;
            context.getSource().sendSuccess(
                    () -> Component.translatable("stature.command.frame.cleared", living.getDisplayName()), true);
        }
        return report(context, cleared);
    }

    private static int listFrames(CommandContext<CommandSourceStack> context) {
        var ids = StatureFrames.ids().stream().map(Identifier::toString).sorted().toList();

        if (ids.isEmpty()) {
            context.getSource().sendSuccess(
                    () -> Component.translatable("stature.command.frame.list.empty"), false);
            return 0;
        }

        context.getSource().sendSuccess(
                () -> Component.translatable("stature.command.frame.list", ids.size(), String.join(", ", ids)),
                false);
        return ids.size();
    }

    private static int reload(CommandContext<CommandSourceStack> context) {
        int count = StatureFrames.load(context.getSource().getServer().getResourceManager());
        RigRegistry.instance().load(context.getSource().getServer().getResourceManager());
        int updated = FrameService.refreshAutomatic(context.getSource().getServer());
        context.getSource().sendSuccess(
                () -> Component.translatable("stature.command.reload", count, updated), false);
        return count;
    }

    private static int get(CommandContext<CommandSourceStack> context, @Nullable Entity target) {
        if (!(target instanceof LivingEntity living)) {
            context.getSource().sendFailure(Component.translatable("stature.command.get.none"));
            return 0;
        }

        ScaleState state = ScaleService.stateOf(living);
        ScaleAxes axes = state.axes();
        Identifier frame = FrameService.frameOf(living);

        // The box is reported, not just the number, because the two are what can disagree: a scale
        // written into the attribute that never reached the dimensions looks identical from the
        // number alone, and a hitbox that has not caught up is the fault this command should show.
        String box = number(living.getBbWidth()) + " x " + number(living.getBbHeight())
                + " x " + number(living.getBoundingBox().getZsize());

        if (frame != null) {
            // A named frame whose file is gone is still the truth about where the numbers came
            // from, but reporting it straight would send the operator hunting a pack for a frame
            // that is not there. Say both halves: the name, and that it is gone.
            boolean known = StatureFrames.get(frame) != null;
            context.getSource().sendSuccess(
                    () -> Component.translatable(
                            known ? "stature.command.get.framed" : "stature.command.get.framed.unknown",
                            living.getDisplayName(), number(state.scale()), box, frame.toString()),
                    false);
        } else if (axes.isUniform()) {
            context.getSource().sendSuccess(
                    () -> Component.translatable(
                            "stature.command.get", living.getDisplayName(), number(state.scale()), box),
                    false);
        } else {
            context.getSource().sendSuccess(
                    () -> Component.translatable(
                            "stature.command.get.shaped",
                            living.getDisplayName(),
                            number(state.scale()),
                            box,
                            number(axes.width()),
                            number(axes.height()),
                            number(axes.depth())),
                    false);
        }
        return 1;
    }

    /** The living entities among a selector's matches, since only they can be scaled. */
    private static Iterable<LivingEntity> living(Collection<? extends Entity> targets) {
        return targets.stream().filter(LivingEntity.class::isInstance).map(LivingEntity.class::cast).toList();
    }

    private static int report(CommandContext<CommandSourceStack> context, int changed) {
        if (changed == 0) {
            // A selector that matched only non-living things is worth saying out loud: it looks
            // exactly like a command that did nothing because the size was already right.
            context.getSource().sendFailure(Component.translatable("stature.command.set.none"));
        }
        return changed;
    }

    /** Three decimals, in a fixed locale: this string is compared by people and by tests. */
    private static String number(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }
}
