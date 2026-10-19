package dev.ellipog.stature.spike;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.phys.AABB;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Spike S2: does per-axis scaling work on 26.3, and do the mixins apply at all in this toolchain?
 *
 * <p>Two ways in. {@code /s2 set <width> <height> <depth>} applies the three multipliers to whoever
 * runs it, which is the case a person checks by looking at the game. And the self-test below runs once
 * on a dedicated server, prints the numbers and stops the server, which is the case a build can check.
 *
 * <p>Depth is a multiplier of the entity's own width, because vanilla's footprint is square: {@code
 * d=1.0} is vanilla, {@code d=2.0} is twice as deep as it is wide, {@code d=0.5} is half. Width and
 * height multiply the dimensions the game already composed, so all three stack with age and with
 * {@code generic.scale} rather than replacing either.
 */
public final class SpikeS2 implements ModInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger("stature-spike-s2");

    /** 0.6 x 1.95 is a vanilla zombie (EntityTypes.ZOMBIE). The case below multiplies out to 0.3 x 2.34. */
    private static final float CASE_WIDTH = 0.5F;

    private static final float CASE_HEIGHT = 1.2F;

    private boolean selfTestRan;

    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(Commands.literal("s2")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.literal("set")
                                .then(Commands.argument("width", DoubleArgumentType.doubleArg(0.01))
                                        .then(Commands.argument("height", DoubleArgumentType.doubleArg(0.01))
                                                .then(Commands.argument("depth", DoubleArgumentType.doubleArg(0.01))
                                                        .executes(SpikeS2::set)))))));

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (!this.selfTestRan && server.isDedicatedServer()) {
                this.selfTestRan = true;
                this.selfTest(server);
                server.halt(false);
            }
        });
    }

    private static int set(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        Entity entity = source.getEntityOrException();

        if (!(entity instanceof LivingEntity living)) {
            source.sendFailure(Component.literal("S2: an entity with dimensions is required"));
            return 0;
        }

        float width = (float) DoubleArgumentType.getDouble(context, "width");
        float height = (float) DoubleArgumentType.getDouble(context, "height");
        float depth = (float) DoubleArgumentType.getDouble(context, "depth");

        ((PerAxisDimensions.Size) living).stature$setSize(width, height);
        ((PerAxisDimensions) living).stature$setDepthMultiplier(depth);
        living.refreshDimensions();

        report("command ", living);
        source.sendSuccess(() -> Component.literal(
                "S2: width x%.2f height x%.2f depth x%.2f".formatted(width, height, depth)), false);
        return 1;
    }

    // ------------------------------------------------------------------
    // The self-test
    // ------------------------------------------------------------------

    private void selfTest(MinecraftServer server) {
        LOGGER.info("[S2] ---- Stature spike S2 ----");
        LOGGER.info("[S2] minecraft {}, java {}", server.getServerVersion(), Runtime.version());

        ServerLevel level = server.overworld();
        // Created rather than spawned: the box and the dimensions are what is being measured, and
        // create() needs no loaded chunk and no spawn position, both of which 26.3 moved.
        Zombie zombie = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);

        if (zombie == null) {
            LOGGER.error("[S2] FAIL: could not create a zombie");
            return;
        }

        zombie.setPos(0.0, 100.0, 0.0);

        boolean entityMixin = zombie instanceof PerAxisDimensions;
        boolean livingMixin = zombie instanceof PerAxisDimensions.Size;
        LOGGER.info("[S2] mixin applied: Entity={} LivingEntity={}", entityMixin, livingMixin);

        if (!entityMixin || !livingMixin) {
            LOGGER.error("[S2] VERDICT: FAIL (the mixins did not apply, so nothing else is measured)");
            return;
        }

        report("vanilla ", zombie);

        // 2a: width and height apart, depth left vanilla. This is the half the launch roster needs.
        ((PerAxisDimensions.Size) zombie).stature$setSize(CASE_WIDTH, CASE_HEIGHT);
        zombie.refreshDimensions();
        report("2a      ", zombie);
        boolean level2a = matches(zombie.getBoundingBox(), 0.3, 2.34, 0.3);

        // 2b: the same entity, twice as deep as it is wide. Nothing in vanilla can express this.
        ((PerAxisDimensions) zombie).stature$setDepthMultiplier(2.0F);
        zombie.refreshDimensions();
        report("2b      ", zombie);
        AABB box = zombie.getBoundingBox();
        boolean rectangular = Math.abs(box.getXsize() - box.getZsize()) > 1.0E-6;
        boolean level2b = matches(box, 0.3, 2.34, 0.6);

        // And it survives being ticked, which is the part arithmetic cannot promise.
        zombie.setDeltaMovement(0.1, 0.0, 0.1);

        try {
            for (int i = 0; i < 5; i++) {
                zombie.tick();
            }

            LOGGER.info("[S2] after 5 ticks: alive={} pos=({}, {}, {}) box=[{} x {} x {}]",
                    zombie.isAlive(),
                    "%.3f".formatted(zombie.getX()), "%.3f".formatted(zombie.getY()), "%.3f".formatted(zombie.getZ()),
                    "%.3f".formatted(zombie.getBoundingBox().getXsize()),
                    "%.3f".formatted(zombie.getBoundingBox().getYsize()),
                    "%.3f".formatted(zombie.getBoundingBox().getZsize()));
        } catch (Throwable t) {
            LOGGER.error("[S2] ticking a rectangular entity threw", t);
        }

        LOGGER.info("[S2] 2a width != height (0.300 x 2.340 x 0.300): {}", level2a);
        LOGGER.info("[S2] 2b width != depth  (0.300 x 2.340 x 0.600): {}", level2b);
        LOGGER.info("[S2] rectangular footprint: {}", rectangular);
        LOGGER.info("[S2] VERDICT: {}", level2a && level2b && rectangular ? "PASS" : "FAIL");
    }

    private static void report(String label, LivingEntity entity) {
        AABB box = entity.getBoundingBox();
        LOGGER.info("[S2] {} getBbWidth={} getBbHeight={} box=[{} x {} x {}]",
                label,
                "%.3f".formatted(entity.getBbWidth()),
                "%.3f".formatted(entity.getBbHeight()),
                "%.3f".formatted(box.getXsize()),
                "%.3f".formatted(box.getYsize()),
                "%.3f".formatted(box.getZsize()));
    }

    private static boolean matches(AABB box, double x, double y, double z) {
        return close(box.getXsize(), x) && close(box.getYsize(), y) && close(box.getZsize(), z);
    }

    private static boolean close(double actual, double expected) {
        return Math.abs(actual - expected) < 1.0E-4;
    }
}
