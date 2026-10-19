package dev.ellipog.stature.fabric;

import dev.ellipog.stature.common.ScaleService;
import dev.ellipog.stature.common.StatureConfig;
import dev.ellipog.stature.common.StatureScale;
import dev.ellipog.stature.common.net.AxesPayload;
import dev.ellipog.stature.common.net.ChannelsPayload;
import dev.ellipog.stature.common.client.CameraTrauma;
import dev.ellipog.stature.common.client.ClientViews;
import dev.ellipog.stature.common.net.ConfigPayload;
import dev.ellipog.stature.common.net.FootstepPayload;
import dev.ellipog.stature.common.net.HandshakePayload;
import dev.ellipog.stature.common.net.PartsPayload;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * The client receiver, in a client entry point so a dedicated server never loads it.
 *
 * <p>The entity is looked up by id in the client level rather than trusted from the packet: an id
 * for an entity this client has not received yet is a packet that arrived before its spawn, and the
 * right answer there is to ignore it once the next broadcast carries the same size.
 */
public final class StatureFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(AxesPayload.TYPE, (payload, context) ->
                context.client().execute(() -> {
                    if (context.client().level == null) {
                        return;
                    }
                    Entity entity = context.client().level.getEntity(payload.entityId());
                    if (entity instanceof LivingEntity living) {
                        ScaleService.applyFromServer(living, payload.state(), payload.eyeHeight());
                    }
                }));
        ClientPlayNetworking.registerGlobalReceiver(ChannelsPayload.TYPE, (payload, context) ->
                context.client().execute(() -> {
                    if (context.client().level == null) {
                        return;
                    }
                    Entity entity = context.client().level.getEntity(payload.entityId());
                    if (entity instanceof LivingEntity living) {
                        // Full replace, never merge: a key missing here was removed there, and a
                        // merge would keep the dead key forever.
                        StatureScale duck = (StatureScale) living;
                        duck.stature$channelOverrides().clear();
                        duck.stature$channelOverrides().putAll(payload.channels());
                    }
                }));
        ClientPlayNetworking.registerGlobalReceiver(PartsPayload.TYPE, (payload, context) ->
                context.client().execute(() -> {
                    if (context.client().level == null) {
                        return;
                    }
                    Entity entity = context.client().level.getEntity(payload.entityId());
                    if (entity instanceof LivingEntity living) {
                        StatureScale duck = (StatureScale) living;
                        duck.stature$partFactors().clear();
                        duck.stature$partFactors().putAll(payload.parts());
                        duck.stature$setFirstPersonVanilla(payload.firstPersonVanilla());
                    }
                }));
        ClientPlayNetworking.registerGlobalReceiver(ConfigPayload.TYPE, (payload, context) ->
                context.client().execute(() -> StatureConfig.applySynced(
                        payload.micro(), payload.macro(), payload.flags())));
        ClientPlayNetworking.registerGlobalReceiver(FootstepPayload.TYPE, (payload, context) ->
                context.client().execute(() -> {
                    Vec3 camera = ClientViews.cameraPosition();
                    if (camera != null) {
                        CameraTrauma.addShake(payload.x(), payload.y(), payload.z(), payload.scale(), camera);
                    }
                }));
        ClientPlayNetworking.registerGlobalReceiver(HandshakePayload.TYPE, (payload, context) ->
                context.client().execute(() -> ClientPlayNetworking.send(new HandshakePayload())));
    }
}
