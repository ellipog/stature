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
import dev.ellipog.stature.common.StatureConfig;
import dev.ellipog.stature.common.StatureScale;
import dev.ellipog.stature.common.channel.ChannelRegistry;
import dev.ellipog.stature.common.channel.ChannelService;
import dev.ellipog.stature.common.parts.PartService;
import dev.ellipog.stature.common.parts.RigRegistry;
import dev.ellipog.stature.common.frame.FrameService;
import dev.ellipog.stature.common.frame.StatureFrames;
import dev.ellipog.stature.common.net.StaturePackets;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import org.jetbrains.annotations.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@code /stature} command tree (plan §14.5 and §14.8).
 *
 * <pre>
 * /stature set    &lt;targets&gt; &lt;scale&gt;                        instantly
 * /stature set    &lt;targets&gt; &lt;scale&gt; &lt;ticks&gt; [easing]         eased
 * /stature set    &lt;targets&gt; &lt;scale&gt; --dangerous               past the envelope (suffix or prefix)
 * /stature set    --self &lt;scale&gt;                             the same, on the command's entity
 * /stature reset  [&lt;targets&gt;]                                scale, shape, channels and parts
 * /stature preset &lt;targets&gt; &lt;preset&gt; [ticks]                 a named size or shape
 * /stature preset --self &lt;preset&gt; [ticks]                    the same, on the command's entity
 * /stature preset list                                       what the config makes available
 * /stature grow   [&lt;targets&gt;] [factor]                       multiply the scale (default 2x)
 * /stature grow   --self [factor]                            the same, on the command's entity
 * /stature shrink [&lt;targets&gt;] [factor]                       multiply the scale (default 0.5x)
 * /stature shrink --self [factor]                            the same, on the command's entity
 * /stature size   &lt;targets&gt; &lt;width&gt; &lt;height&gt; &lt;depth&gt; [ticks]  the box's shape
 * /stature frame  apply &lt;targets&gt; &lt;frame&gt; [ticks]            a frame from a data pack
 * /stature frame  clear &lt;targets&gt;                            back to vanilla proportions
 * /stature frame  list                                       what the packs declared
 * /stature channel &lt;targets&gt; &lt;channel&gt; &lt;value&gt;           set one channel now
 * /stature channel &lt;targets&gt; &lt;channel&gt; reset               back to following the base
 * /stature channel &lt;targets&gt; list                          what each channel says
 * /stature get    [&lt;target&gt;]                                 the size and the resulting box
 * /stature reload                                            re-read the config and the packs
 * </pre>
 *
 * <p>{@code --self} is a marker rather than an omitted target on {@code set}, {@code preset} and the
 * relative commands because a bare number can be a player name: {@code /stature set 2} would be
 * ambiguous. A marker that a player name cannot contain keeps the tree unambiguous. {@code reset}
 * has nothing after the target, so it can simply default to the command's entity.
 *
 * <p>The scale argument is parsed across the dangerous range so the suffix can be seen at all;
 * {@link #set} then refuses a value outside {@link ScaleLimits} with a message that names
 * {@code --dangerous}, and the dangerous path clamps the box. That is also the limit of the guard:
 * the API behind this is unbound (§14.5), and the clamp is the command's job, not the service's
 * policy.
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

    private static final Logger LOG = LoggerFactory.getLogger("stature");

    /** Ten minutes is far longer than any transition worth watching, and keeps a typo bounded. */
    private static final int MAX_TICKS = 20 * 60;

    /** The dangerous envelope (plan Q9), narrowed from the old 0.0001..10000. */
    private static final double DANGEROUS_MIN = 0.001;

    private static final double DANGEROUS_MAX = 1000.0;

    /** Above this, dangerous mode is legal but the operator is warned. */
    private static final double DANGEROUS_WARN = 256.0;

    private StatureCommands() {
    }

    /** Registers the tree. Safe to call once per loader, at the loader's own moment. */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // The root is wider than level 2 only when the config deliberately lowers the self-only
        // level. Every branch that changes someone else still names the gamemaster check itself,
        // so lowering the self level can never open the old commands to players.
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("stature")
                .requires(source -> gamemaster(source) || selfAllowed(source));

        setBranch(root);
        resetBranch(root);
        presetBranch(root);
        relativeBranch(root, "grow", 2.0, true);
        relativeBranch(root, "shrink", 0.5, false);
        sizeBranch(root);
        frameBranch(root);
        channelBranch(root);
        partBranch(root);

        root.then(Commands.literal("get")
                .requires(source -> gamemaster(source) || selfAllowed(source))
                .executes(context -> get(context, context.getSource().getEntity()))
                .then(Commands.argument("target", EntityArgument.entity())
                        .requires(StatureCommands::gamemaster)
                        .executes(context -> get(context, EntityArgument.getEntity(context, "target")))));

        root.then(Commands.literal("reload")
                .requires(StatureCommands::gamemaster)
                .executes(StatureCommands::reload));

        dispatcher.register(root);
    }

    private static void setBranch(LiteralArgumentBuilder<CommandSourceStack> root) {
        RequiredArgumentBuilder<CommandSourceStack, ?> targets =
                Commands.argument("targets", EntityArgument.entities())
                        .requires(StatureCommands::gamemaster);
        // The argument accepts the wide range in both shapes. The plain execution rejects a
        // number outside the normal envelope with a message that names --dangerous; the suffix
        // and prefix forms call the dangerous path. A safe value is unchanged.
        targets.then(scaleChain(false));
        // The prefix form stays because the README documents it: set @s --dangerous 100.
        targets.then(Commands.literal("--dangerous").then(scaleChain(true)));

        LiteralArgumentBuilder<CommandSourceStack> self = Commands.literal("--self")
                .requires(StatureCommands::selfAllowed)
                .then(scaleChain(false))
                .then(Commands.literal("--dangerous").then(scaleChain(true)));

        root.then(Commands.literal("set").then(targets).then(self));
    }

    /**
     * The scale chain, twice: the postfix shape and the old prefix shape. Beyond the envelope
     * the attribute holds its nearest bound and the model channel shows the rest, so a 100x
     * looks 100x with a 16x box rather than freezing physics.
     */
    private static RequiredArgumentBuilder<CommandSourceStack, Double> scaleChain(boolean dangerous) {
        RequiredArgumentBuilder<CommandSourceStack, Double> scale = Commands.argument("scale",
                        DoubleArgumentType.doubleArg(DANGEROUS_MIN, DANGEROUS_MAX))
                // No ticks means now, and "now" is linear because there is no curve to walk:
                // the eased default only means something once a duration exists.
                .executes(context -> dangerous
                        ? setDangerous(context, 0, Easing.LINEAR)
                        : set(context, 0, Easing.LINEAR));
        if (!dangerous) {
            scale.then(Commands.literal("--dangerous")
                    .executes(context -> setDangerous(context, 0, Easing.LINEAR)));
        }

        RequiredArgumentBuilder<CommandSourceStack, Integer> ticks =
                Commands.argument("ticks", IntegerArgumentType.integer(0, MAX_TICKS))
                        .executes(context -> dangerous
                                ? setDangerous(context, IntegerArgumentType.getInteger(context, "ticks"),
                                        Easing.EASE_OUT_CUBIC)
                                : set(context, IntegerArgumentType.getInteger(context, "ticks"),
                                        Easing.EASE_OUT_CUBIC));
        if (!dangerous) {
            ticks.then(Commands.literal("--dangerous")
                    .executes(context -> setDangerous(context,
                            IntegerArgumentType.getInteger(context, "ticks"),
                            Easing.EASE_OUT_CUBIC)));
        }
        ticks.then(easing("linear", Easing.LINEAR, dangerous));
        ticks.then(easing("cubic", Easing.EASE_OUT_CUBIC, dangerous));
        ticks.then(easing("elastic", Easing.ELASTIC, dangerous));
        scale.then(ticks);
        return scale;
    }

    /**
     * One command that means "forget everything Stature did to this entity": scale, shape,
     * frame, channels and parts. Separate from {@code frame clear}, which only forgets a frame.
     */
    private static void resetBranch(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("reset")
                .executes(StatureCommands::selfReset)
                .then(Commands.argument("targets", EntityArgument.entities())
                        .requires(StatureCommands::gamemaster)
                        .executes(StatureCommands::reset)));
    }

    /** A named size or shape from the config, with a list of what is available. */
    private static void presetBranch(LiteralArgumentBuilder<CommandSourceStack> root) {
        LiteralArgumentBuilder<CommandSourceStack> preset = Commands.literal("preset");

        // List is a discovery aid: anyone who can use a self preset should be able to see the
        // names. The root already limits entry to a gamemaster or a self-level source.
        preset.then(Commands.literal("list").executes(StatureCommands::listPresets));

        preset.then(Commands.argument("targets", EntityArgument.entities())
                .requires(StatureCommands::gamemaster)
                .then(presetChain()));

        // The marker, not a bare preset name, because a preset name and a player name are both
        // one word: "preset giant" cannot mean "the player named giant" and "the preset giant".
        preset.then(Commands.literal("--self")
                .requires(StatureCommands::selfAllowed)
                .then(presetChain()));

        root.then(preset);
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> presetChain() {
        RequiredArgumentBuilder<CommandSourceStack, String> preset =
                Commands.argument("preset", StringArgumentType.word())
                        .suggests(StatureCommands::suggestPresets)
                        .executes(context -> applyPreset(context, 0));

        preset.then(Commands.argument("ticks", IntegerArgumentType.integer(0, MAX_TICKS))
                .executes(context -> applyPreset(
                        context, IntegerArgumentType.getInteger(context, "ticks"))));
        return preset;
    }

    /**
     * Grow and shrink multiply the current scale instead of replacing it. The result is pulled into
     * the normal envelope with the same {@link ScaleLimits#clamp} the game's attribute uses; a
     * dangerous model left by an earlier {@code set --dangerous} is cleared the same way a plain
     * {@code set} clears it.
     */
    private static void relativeBranch(
            LiteralArgumentBuilder<CommandSourceStack> root, String name, double defaultFactor,
            boolean growing) {
        LiteralArgumentBuilder<CommandSourceStack> relative = Commands.literal(name)
                .requires(source -> StatureConfig.relativeCommandsEnabled()
                        && (gamemaster(source) || selfAllowed(source)))
                .executes(context -> selfRelative(context, defaultFactor, growing));

        RequiredArgumentBuilder<CommandSourceStack, ?> targets =
                Commands.argument("targets", EntityArgument.entities())
                        .requires(StatureCommands::gamemaster)
                        .executes(context -> relative(context, defaultFactor, growing));
        targets.then(relativeFactor(growing));
        relative.then(targets);

        LiteralArgumentBuilder<CommandSourceStack> self = Commands.literal("--self")
                .requires(StatureCommands::selfAllowed)
                .executes(context -> relative(context, defaultFactor, growing));
        self.then(relativeFactor(growing));
        relative.then(self);

        root.then(relative);
    }

    private static RequiredArgumentBuilder<CommandSourceStack, Double> relativeFactor(boolean growing) {
        // Grow only grows and shrink only shrinks, so a typo in the wrong direction is refused by
        // the parser rather than quietly doing the opposite of the command's name.
        return Commands.argument("factor", growing
                        ? DoubleArgumentType.doubleArg(1.0, DANGEROUS_MAX)
                        : DoubleArgumentType.doubleArg(DANGEROUS_MIN, 1.0))
                .executes(context -> relative(
                        context, DoubleArgumentType.getDouble(context, "factor"), growing));
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
                .requires(StatureCommands::gamemaster)
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
                .requires(StatureCommands::gamemaster)
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
                .requires(StatureCommands::gamemaster)
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
                .requires(StatureCommands::gamemaster)
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
    private static LiteralArgumentBuilder<CommandSourceStack> easing(
            String name, Easing easing, boolean dangerous) {
        LiteralArgumentBuilder<CommandSourceStack> literal = Commands.literal(name)
                .executes(context -> dangerous
                        ? setDangerous(context, IntegerArgumentType.getInteger(context, "ticks"), easing)
                        : set(context, IntegerArgumentType.getInteger(context, "ticks"), easing));
        if (!dangerous) {
            literal.then(Commands.literal("--dangerous")
                    .executes(context -> setDangerous(context,
                            IntegerArgumentType.getInteger(context, "ticks"), easing)));
        }
        return literal;
    }

    private static int set(CommandContext<CommandSourceStack> context, int ticks, Easing easing)
            throws CommandSyntaxException {
        Collection<? extends Entity> targets = targetEntities(context);
        if (targets == null) {
            return 0;
        }
        float scale = (float) DoubleArgumentType.getDouble(context, "scale");
        if (!Float.isFinite(scale)) {
            context.getSource().sendFailure(
                    Component.translatable("stature.command.set.badvalue"));
            return 0;
        }
        if (scale < ScaleLimits.MIN || scale > ScaleLimits.MAX) {
            context.getSource().sendFailure(Component.translatable(
                    "stature.command.set.out_of_range",
                    number(scale), number(ScaleLimits.MIN), number(ScaleLimits.MAX)));
            return 0;
        }

        int changed = 0;
        for (LivingEntity living : living(targets)) {
            // The shape is kept: "make it twice as big" must not silently make it square again.
            FrameService.setManualScale(living, scale, ticks, easing);
            clearDangerousModel(living);
            changed++;
            context.getSource().sendSuccess(
                    () -> Component.translatable(
                            "stature.command.set.success", living.getDisplayName(), number(scale)),
                    true);
        }
        return report(context, changed);
    }

    /**
     * Sets past the envelope: the attribute holds its nearest bound and the model channel
     * shows the rest. The override stays until a plain set or a reset clears it, and the
     * entity is flagged so that plain set knows the model was the dangerous command's.
     */
    private static int setDangerous(CommandContext<CommandSourceStack> context, int ticks, Easing easing)
            throws CommandSyntaxException {
        Collection<? extends Entity> targets = targetEntities(context);
        if (targets == null) {
            return 0;
        }
        float scale = (float) DoubleArgumentType.getDouble(context, "scale");
        if (!Float.isFinite(scale)) {
            context.getSource().sendFailure(
                    Component.translatable("stature.command.set.badvalue"));
            return 0;
        }
        float clamped = ScaleLimits.clamp(scale);
        if (scale > DANGEROUS_WARN) {
            LOG.warn("dangerous scale {} is far outside the attribute envelope; the box holds {}x "
                    + "and only the model shows the rest", scale, clamped);
        }

        int changed = 0;
        for (LivingEntity living : living(targets)) {
            FrameService.setManualScale(living, clamped, ticks, easing);
            ChannelService.setChannel(living, ChannelService.MODEL, scale, ticks, easing);
            ((StatureScale) living).stature$setDangerousModel(true);
            changed++;
            context.getSource().sendSuccess(
                    () -> Component.translatable(
                            "stature.command.set.dangerous",
                            living.getDisplayName(), number(scale), number(clamped)),
                    true);
        }
        return report(context, changed);
    }

    /** The target-less form of {@code reset}: the command's own entity, when configured on. */
    private static int selfReset(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        if (!selfShortcutsEnabled(context)) {
            return 0;
        }
        return reset(context);
    }

    /** Forgets every part of Stature's state on these entities. */
    private static int reset(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Collection<? extends Entity> targets = targetEntities(context);
        if (targets == null) {
            return 0;
        }

        int changed = 0;
        for (LivingEntity living : living(targets)) {
            FrameService.clear(living);
            ChannelService.clearAll(living);
            ((StatureScale) living).stature$setDangerousModel(false);
            changed++;
            context.getSource().sendSuccess(
                    () -> Component.translatable(
                            "stature.command.reset.success", living.getDisplayName()),
                    true);
        }
        return report(context, changed);
    }

    /**
     * Drops a dangerous set's model channel, but only when it was the dangerous set that wrote
     * it. A model chosen by hand survives a plain size change.
     */
    private static void clearDangerousModel(LivingEntity living) {
        StatureScale duck = (StatureScale) living;
        if (!duck.stature$dangerousModel()) {
            return;
        }
        ChannelService.resetChannel(living, ChannelService.MODEL);
        duck.stature$setDangerousModel(false);
    }

    private static int applyPreset(CommandContext<CommandSourceStack> context, int ticks)
            throws CommandSyntaxException {
        Collection<? extends Entity> targets = targetEntities(context);
        if (targets == null) {
            return 0;
        }
        if (!StatureConfig.presetsEnabled()) {
            context.getSource().sendFailure(
                    Component.translatable("stature.command.preset.disabled"));
            return 0;
        }

        String name = StringArgumentType.getString(context, "preset");
        StatureConfig.Preset preset = StatureConfig.preset(name);
        if (preset == null) {
            context.getSource().sendFailure(
                    Component.translatable("stature.command.preset.unknown", name));
            return 0;
        }

        Easing easing = eased(ticks);
        int changed = 0;
        for (LivingEntity living : living(targets)) {
            if (preset.hasScale() && preset.hasShape()) {
                FrameService.setManual(living,
                        new ScaleState(preset.scale(), preset.shape()), ticks, easing);
            } else if (preset.hasScale()) {
                FrameService.setManualScale(living, preset.scale(), ticks, easing);
            } else {
                FrameService.setManualShape(living, preset.shape(), ticks, easing);
            }
            if (preset.hasScale()) {
                // A preset is a plain size choice, so it ends a dangerous set's visual leftover
                // exactly as /stature set does.
                clearDangerousModel(living);
            }
            changed++;
            context.getSource().sendSuccess(
                    () -> Component.translatable(
                            "stature.command.preset.success",
                            living.getDisplayName(), name, presetValue(preset)),
                    true);
        }
        return report(context, changed);
    }

    private static int listPresets(CommandContext<CommandSourceStack> context) {
        if (!StatureConfig.presetsEnabled()) {
            context.getSource().sendFailure(
                    Component.translatable("stature.command.preset.disabled"));
            return 0;
        }
        var presets = StatureConfig.presets();
        if (presets.isEmpty()) {
            context.getSource().sendSuccess(
                    () -> Component.translatable("stature.command.preset.list.empty"), false);
            return 0;
        }
        List<String> entries = new ArrayList<>();
        for (StatureConfig.Preset preset : presets) {
            entries.add(presetEntry(preset));
        }
        context.getSource().sendSuccess(
                () -> Component.translatable(
                        "stature.command.preset.list", presets.size(), String.join(", ", entries)),
                false);
        return presets.size();
    }

    /** The configured names, offered as completions. Empty when the feature is switched off. */
    private static CompletableFuture<Suggestions> suggestPresets(
            CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        if (!StatureConfig.presetsEnabled()) {
            return builder.buildFuture();
        }
        return SharedSuggestionProvider.suggest(
                StatureConfig.presets().stream()
                        .map(StatureConfig.Preset::name)
                        .sorted()
                        .toList(),
                builder);
    }

    private static int relative(
            CommandContext<CommandSourceStack> context, double factor, boolean growing)
            throws CommandSyntaxException {
        Collection<? extends Entity> targets = targetEntities(context);
        if (targets == null) {
            return 0;
        }
        if (!StatureConfig.relativeCommandsEnabled()) {
            context.getSource().sendFailure(
                    Component.translatable("stature.command.relative.disabled"));
            return 0;
        }
        if (!Double.isFinite(factor) || factor <= 0.0) {
            context.getSource().sendFailure(
                    Component.translatable("stature.command.relative.badfactor"));
            return 0;
        }

        int changed = 0;
        for (LivingEntity living : living(targets)) {
            float before = ScaleService.stateOf(living).scale();
            float requested = (float) (before * factor);
            float after = ScaleLimits.clamp(requested);
            FrameService.setManualScale(living, after, 0, Easing.LINEAR);
            clearDangerousModel(living);
            changed++;

            boolean capped = growing ? requested > ScaleLimits.MAX : requested < ScaleLimits.MIN;
            boolean unchanged = Math.abs(after - before) < 1e-6F;
            String from = shortNumber(before);
            String to = shortNumber(after);
            String key;
            Object[] args;
            if (unchanged) {
                key = capped
                        ? (growing ? "stature.command.grow.at_max" : "stature.command.shrink.at_min")
                        : "stature.command.relative.unchanged";
                args = new Object[] {living.getDisplayName(), to};
            } else if (capped) {
                key = growing ? "stature.command.grow.capped" : "stature.command.shrink.capped";
                args = new Object[] {living.getDisplayName(), from, to};
            } else {
                key = growing ? "stature.command.grow.success" : "stature.command.shrink.success";
                args = new Object[] {living.getDisplayName(), from, to};
            }
            Component message = Component.translatable(key, args);
            context.getSource().sendSuccess(() -> message, true);
        }
        return report(context, changed);
    }

    private static int selfRelative(
            CommandContext<CommandSourceStack> context, double factor, boolean growing)
            throws CommandSyntaxException {
        if (!selfShortcutsEnabled(context)) {
            return 0;
        }
        return relative(context, factor, growing);
    }

    /** The component form of a preset's numbers, for the success line. */
    private static Component presetValue(StatureConfig.Preset preset) {
        if (preset.hasScale() && preset.hasShape()) {
            ScaleAxes shape = preset.shape();
            return Component.translatable(
                    "stature.command.preset.value.both",
                    shortNumber(preset.scale()),
                    shortNumber(shape.width()), shortNumber(shape.height()), shortNumber(shape.depth()));
        }
        if (preset.hasScale()) {
            return Component.translatable(
                    "stature.command.preset.value.scale", shortNumber(preset.scale()));
        }
        ScaleAxes shape = preset.shape();
        return Component.translatable(
                "stature.command.preset.value.shape",
                shortNumber(shape.width()), shortNumber(shape.height()), shortNumber(shape.depth()));
    }

    /** The plain-string form for {@code preset list}, where one translation holds the whole line. */
    private static String presetEntry(StatureConfig.Preset preset) {
        String value;
        if (preset.hasScale() && preset.hasShape()) {
            ScaleAxes shape = preset.shape();
            value = shortNumber(preset.scale()) + "x, shape " + shapeText(shape);
        } else if (preset.hasScale()) {
            value = shortNumber(preset.scale()) + "x";
        } else {
            value = "shape " + shapeText(preset.shape());
        }
        return preset.name() + " (" + value + ")";
    }

    private static String shapeText(ScaleAxes shape) {
        return shortNumber(shape.width()) + " x " + shortNumber(shape.height())
                + " x " + shortNumber(shape.depth());
    }

    /** 0 ticks means now, which is linear because there is no curve to walk. */
    private static Easing eased(int ticks) {
        return ticks == 0 ? Easing.LINEAR : Easing.EASE_OUT_CUBIC;
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
        MinecraftServer server = context.getSource().getServer();
        // The config file is not datapack data, so vanilla /reload never re-reads it: this
        // alias does, first, so the shared refresh below resolves every overridden entity
        // against the new edges rather than the old ones.
        StatureConfig.reload();
        int count = FrameService.reloadFrameData(server.getResourceManager());
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            StaturePackets.sendConfigTo(player,
                    StatureConfig.microThreshold(), StatureConfig.macroThreshold(),
                    StatureConfig.flagSnapshot(), StatureConfig.tuningSnapshot());
        }
        int updated = FrameService.refreshAutomatic(server);
        StatureConfig.refreshAllThresholds(server);
        context.getSource().sendSuccess(
                () -> Component.translatable(
                        "stature.command.reload", count, StatureConfig.presetCount(), updated),
                false);
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

    /**
     * The targets of a command that also has a {@code --self} or target-less form.
     *
     * <p>When the parsed tree contains no {@code targets} node, the command's own entity is the
     * target. A console source has no entity, and rather than letting Brigadier's generic missing
     * entity error escape, the command says how to fix it.
     */
    private static @Nullable Collection<? extends Entity> targetEntities(
            CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        if (hasArgument(context, "targets")) {
            return EntityArgument.getEntities(context, "targets");
        }
        Entity source = context.getSource().getEntity();
        if (source == null) {
            context.getSource().sendFailure(
                    Component.translatable("stature.command.self.none"));
            return null;
        }
        return List.of(source);
    }

    private static boolean hasArgument(CommandContext<?> context, String name) {
        return context.getNodes().stream()
                .anyMatch(node -> name.equals(node.getNode().getName()));
    }

    /** A self shortcut was asked for while the config or the permission level says no. */
    private static boolean selfShortcutsEnabled(CommandContext<CommandSourceStack> context) {
        if (!StatureConfig.selfShortcutsEnabled()) {
            context.getSource().sendFailure(
                    Component.translatable("stature.command.self.disabled"));
            return false;
        }
        if (!selfAllowed(context.getSource())) {
            context.getSource().sendFailure(Component.translatable(
                    "stature.command.self.permission", StatureConfig.selfCommandPermissionLevel()));
            return false;
        }
        return true;
    }

    /** The old level-2 gate for every command that can change someone else. */
    private static boolean gamemaster(CommandSourceStack source) {
        return source.permissions().hasPermission(
                new Permission.HasCommandLevel(PermissionLevel.GAMEMASTERS));
    }

    /**
     * The configurable gate for the self-only forms. Defaults to gamemaster (2), so an unmodified
     * server behaves exactly as before; a pack can lower it without opening the target forms.
     */
    private static boolean selfAllowed(CommandSourceStack source) {
        if (!StatureConfig.selfShortcutsEnabled()) {
            return false;
        }
        return source.permissions().hasPermission(new Permission.HasCommandLevel(
                PermissionLevel.byId(StatureConfig.selfCommandPermissionLevel())));
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

    /** A plain decimal without trailing zeros, for the new player-facing lines. */
    private static String shortNumber(double value) {
        if (!Double.isFinite(value)) {
            return number(value);
        }
        if (Math.abs(value) < 1.0e15 && value == Math.rint(value)) {
            return Long.toString((long) value);
        }
        // Float multipliers carry binary noise (1.2F reads as 1.200000047...), so round before
        // trimming. Four decimals keeps the 0.0625 end of the envelope exact.
        String text = String.format(Locale.ROOT, "%.4f", value);
        int end = text.length();
        while (end > 0 && text.charAt(end - 1) == '0') {
            end--;
        }
        if (end > 0 && text.charAt(end - 1) == '.') {
            end--;
        }
        return text.substring(0, end);
    }
}
