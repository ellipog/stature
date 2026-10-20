package dev.ellipog.stature.neoforge.client;

import dev.ellipog.stature.api.Stature;
import dev.ellipog.stature.common.ResolvedThresholds;
import dev.ellipog.stature.common.ScaleService;
import dev.ellipog.stature.common.StatureConfig;
import dev.ellipog.stature.common.StatureScale;
import dev.ellipog.stature.common.channel.ChannelService;
import dev.ellipog.stature.common.client.CameraTrauma;
import dev.ellipog.stature.common.client.ClientViews;
import dev.ellipog.stature.common.net.AxesPayload;
import dev.ellipog.stature.common.net.ChannelsPayload;
import dev.ellipog.stature.common.net.ConfigPayload;
import dev.ellipog.stature.common.net.FootstepPayload;
import dev.ellipog.stature.common.net.PartsPayload;
import dev.ellipog.stature.common.net.ThresholdsPayload;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

/**
 * The client half of NeoForge's payload registration (improvement plan M8).
 *
 * <p>Kept out of {@link dev.ellipog.stature.neoforge.StatureNeoForge} on purpose. A dedicated server
 * must never classload {@link ClientViews}, which names {@code Minecraft.getInstance()}, and a lambda
 * written beside the payload types is loaded with its owner whether or not it ever runs.
 * {@link RegisterClientPayloadHandlersEvent} fires only on a client, and the dist filter on this
 * class stops the loader reading it on a server at all.
 *
 * <p>Every handler still runs through {@code enqueueWork}, because the entity lookup and the state
 * writes belong on the main thread even though the event's registrar already defaults there.
 */
@EventBusSubscriber(modid = Stature.MOD_ID, value = Dist.CLIENT)
public final class NeoForgeStatureClientPayloads {

    private NeoForgeStatureClientPayloads() {
    }

    @SubscribeEvent
    public static void register(RegisterClientPayloadHandlersEvent event) {
        event.register(AxesPayload.TYPE, (payload, context) -> context.enqueueWork(() -> {
            Entity entity = context.player().level().getEntity(payload.entityId());
            if (entity instanceof LivingEntity living) {
                ScaleService.applyFromServer(living, payload.state(), payload.eyeHeight());
            }
        }));
        event.register(ChannelsPayload.TYPE, (payload, context) -> context.enqueueWork(() -> {
            Entity entity = context.player().level().getEntity(payload.entityId());
            if (entity instanceof LivingEntity living) {
                ChannelService.applyFromServer(living, payload.channels());
            }
        }));
        event.register(PartsPayload.TYPE, (payload, context) -> context.enqueueWork(() -> {
            Entity entity = context.player().level().getEntity(payload.entityId());
            if (entity instanceof LivingEntity living) {
                StatureScale duck = (StatureScale) living;
                duck.stature$partFactors().clear();
                duck.stature$partFactors().putAll(payload.parts());
                duck.stature$setFirstPersonVanilla(payload.firstPersonVanilla());
            }
        }));
        event.register(ThresholdsPayload.TYPE, (payload, context) -> context.enqueueWork(() -> {
            Entity entity = context.player().level().getEntity(payload.entityId());
            if (entity instanceof LivingEntity living) {
                StatureConfig.applyFromServer(
                        living,
                        new ResolvedThresholds(payload.micro(), payload.macro(), payload.flags()));
            }
        }));
        event.register(ConfigPayload.TYPE, (payload, context) -> context.enqueueWork(() ->
                StatureConfig.applySynced(payload.micro(), payload.macro(), payload.flags())));
        event.register(FootstepPayload.TYPE, (payload, context) -> context.enqueueWork(() -> {
            Vec3 camera = ClientViews.cameraPosition();
            if (camera != null) {
                CameraTrauma.addShake(
                        payload.x(), payload.y(), payload.z(), payload.scale(), camera,
                        ClientViews.screenEffectScale());
            }
        }));
    }
}
