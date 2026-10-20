package dev.ellipog.stature.gametest;

import dev.ellipog.stature.api.Easing;
import dev.ellipog.stature.api.ScaleAxes;
import dev.ellipog.stature.api.ScaleState;
import dev.ellipog.stature.common.ScaleService;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.server.level.ServerPlayer;

/**
 * Fabric client gametests (improvement plan M4): the render half that a
 * headless server run can never see.
 *
 * <p>One test for now: grow the joined player to 16x, open the inventory,
 * and screenshot the paperdoll. The M3 fix draws it at unit size no matter
 * the world scale; the screenshot lands under
 * {@code build/run/clientGameTest/screenshots/} for a human to approve
 * against the baseline (screenshot <em>comparison</em> comes once a baseline
 * is approved, per the plan).
 *
 * <p>Needs a display: {@code :fabric:runClientGameTest} on a logged-in
 * session locally, {@code xvfb-run} on CI. Never part of a headless
 * {@code build}.
 */
public final class StatureFabricClientGameTests implements FabricClientGameTest {

    @Override
    public void runTest(ClientGameTestContext context) {
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
        }
    }
}
