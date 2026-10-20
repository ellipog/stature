package dev.ellipog.stature.common.api;

import java.util.Objects;
import java.util.OptionalDouble;

import dev.ellipog.stature.api.ChannelSpec;
import dev.ellipog.stature.api.Easing;
import dev.ellipog.stature.api.ScaleAxes;
import dev.ellipog.stature.api.ScaleLimits;
import dev.ellipog.stature.api.ScaleState;
import dev.ellipog.stature.common.ScaleService;
import dev.ellipog.stature.common.StatureScale;
import dev.ellipog.stature.common.channel.ChannelRegistry;
import dev.ellipog.stature.common.channel.ChannelService;
import dev.ellipog.stature.common.frame.FrameService;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

/**
 * The one implementation behind {@link StatureApi}.
 *
 * <p>Kept package-private and free of state: every method is a translation of one facade call into
 * the service that already owns that operation. The thread check lives here rather than in the
 * services because the facade is the boundary that promises it; the services are also used by
 * commands and payload handlers, whose call sites already run on the right thread.
 */
final class StatureApiImpl implements StatureApi {

    static final StatureApiImpl INSTANCE = new StatureApiImpl();

    private StatureApiImpl() {
    }

    @Override
    public ScaleState state(LivingEntity entity) {
        return ScaleService.stateOf(Objects.requireNonNull(entity, "entity"));
    }

    @Override
    public void setScale(LivingEntity entity, double scale, int ticks, Easing easing) {
        requireServerThread(entity);
        Objects.requireNonNull(easing, "easing");
        if (!Double.isFinite(scale)) {
            throw new IllegalArgumentException("Stature: scale must be finite, got " + scale);
        }
        FrameService.setManualScale(
                entity, ScaleLimits.clamp((float) scale), ticks, easing, StatureEvents.Cause.API);
    }

    @Override
    public void setShape(LivingEntity entity, ScaleAxes axes, int ticks) {
        requireServerThread(entity);
        FrameService.setManualShape(
                entity, Objects.requireNonNull(axes, "axes"), ticks, Easing.LINEAR,
                StatureEvents.Cause.API);
    }

    @Override
    public OptionalDouble channelOverride(LivingEntity entity, String channelId) {
        Objects.requireNonNull(entity, "entity");
        Objects.requireNonNull(channelId, "channelId");
        if (ChannelRegistry.instance().get(channelId) == null) {
            throw new IllegalArgumentException("no channel named " + channelId + " (declare it first)");
        }
        Float value = ((StatureScale) entity).stature$channelOverrides().get(channelId);
        return value == null ? OptionalDouble.empty() : OptionalDouble.of(value);
    }

    @Override
    public void setChannel(LivingEntity entity, String channelId, double value, int ticks) {
        requireServerThread(entity);
        Objects.requireNonNull(channelId, "channelId");
        ChannelService.setChannel(
                entity, channelId, (float) value, ticks, Easing.LINEAR, StatureEvents.Cause.API);
    }

    @Override
    public void clearChannel(LivingEntity entity, String channelId) {
        requireServerThread(entity);
        Objects.requireNonNull(channelId, "channelId");
        ChannelService.resetChannel(entity, channelId, StatureEvents.Cause.API);
    }

    @Override
    public boolean setIntegrationFrame(LivingEntity entity, Identifier frameId) {
        requireServerThread(entity);
        Objects.requireNonNull(frameId, "frameId");
        return FrameService.setIntegrationFrame(entity, frameId, 0);
    }

    @Override
    public boolean clearIntegrationFrame(LivingEntity entity) {
        requireServerThread(entity);
        return FrameService.clearIntegrationFrame(entity);
    }

    @Override
    public void registerChannel(ChannelSpec spec) {
        ChannelRegistry.instance().register(Objects.requireNonNull(spec, "spec"));
    }

    /**
     * Refuses a mutation where the services cannot honestly make one: the client has no authority
     * over these numbers, and the server state is only safe to touch from its own thread.
     */
    private static void requireServerThread(LivingEntity entity) {
        Objects.requireNonNull(entity, "entity");
        if (!(entity.level() instanceof ServerLevel level)) {
            throw new IllegalStateException(
                    "StatureApi mutators are server-side; this entity is on a client level");
        }
        MinecraftServer server = level.getServer();
        if (server == null || !server.isSameThread()) {
            throw new IllegalStateException(
                    "StatureApi mutators must run on the server thread");
        }
    }
}
