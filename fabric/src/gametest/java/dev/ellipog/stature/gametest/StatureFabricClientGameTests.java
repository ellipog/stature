package dev.ellipog.stature.gametest;

import dev.ellipog.stature.api.Easing;
import dev.ellipog.stature.api.ScaleAxes;
import dev.ellipog.stature.api.ScaleState;
import dev.ellipog.stature.common.ScaleService;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.server.level.ServerPlayer;

/**
 * Fabric client gametests (improvement plan M4): the render half that a
 * headless server run can never see.
 *
 * <p>Grow the joined player to 16x, open the inventory, and screenshot the
 * paperdoll. The M3 fix draws it at unit size no matter the world scale; the
 * screenshot lands under {@code build/run/clientGameTest/screenshots/} for a
 * human to approve against the baseline (screenshot <em>comparison</em> comes
 * once a baseline is approved, per the plan).
 *
 * <p>Then save a non-uniform box on the player and join the world again. That
 * second join is the regression test for the load-time self broadcast: the
 * player entity is loaded before its connection exists.
 *
 * <p>Needs a display: {@code :fabric:runClientGameTest} on a logged-in
 * session locally, {@code xvfb-run} on CI. Never part of a headless
 * {@code build}.
 */
public final class StatureFabricClientGameTests implements FabricClientGameTest {

    @Override
    public void runTest(ClientGameTestContext context) {
        TestWorldSave save;
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getServer().runOnServer(server -> {
                ServerPlayer player = world.getConnection().getServerPlayer();
                ScaleService.setSize(player, new ScaleState(16.0F, ScaleAxes.UNIFORM), 0, Easing.LINEAR);
            });
            context.waitTicks(40);
            context.runOnClient(client -> {
                if (client.player != null) {
                    client.setScreenAndShow(new InventoryScreen(client.player));
                }
            });
            context.waitTicks(10);
            context.takeScreenshot("inventory_16x");

            // A non-uniform box is saved on the entity, unlike the uniform scale above, which
            // rides the game's own scale attribute. That saved shape is what makes the join path
            // load Stature state before the connection exists.
            world.getServer().runOnServer(server -> {
                ServerPlayer player = world.getConnection().getServerPlayer();
                ScaleService.setSize(player,
                        new ScaleState(16.0F, new ScaleAxes(16.0F, 16.0F, 8.0F)),
                        0, Easing.LINEAR);
            });
            context.waitTicks(2);
            save = world.getWorldSave();
        }

        // A player carrying a saved shape loads it before the connection is attached. That is the
        // join path the plain "create a world" test never reaches, and it used to broadcast to the
        // loading player, whose connection is still null, killing the integrated server.
        ScaleState[] reloaded = {null};
        try (TestSingleplayerContext reopened = save.open()) {
            reopened.getServer().runOnServer(server -> {
                ServerPlayer player = reopened.getConnection().getServerPlayer();
                reloaded[0] = ScaleService.stateOf(player);
            });
            context.waitTicks(5);
            reopened.getConnection().waitForClientboundPackets();
            float clientDepth = context.computeOnClient(client -> client.player == null
                    ? -1.0F
                    : ScaleService.stateOf(client.player).axes().depth());
            if (clientDepth != 8.0F) {
                throw new AssertionError("the rejoined client did not receive the saved shape: "
                        + clientDepth + " deep");
            }
        }
        if (reloaded[0] == null
                || reloaded[0].scale() != 16.0F
                || reloaded[0].axes().depth() != 8.0F) {
            throw new AssertionError("a saved 16x/8-deep player reloaded as " + reloaded[0]);
        }
    }
}
