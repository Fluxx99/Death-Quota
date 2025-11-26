package net.deathquota.mod.death;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;
import net.deathquota.mod.DeathQuotaMod;

import java.util.Collections;
import java.util.Optional;

public final class DeathQuotaManager {
    private DeathQuotaManager() {
    }

    public static void registerEventHooks() {
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            if (entity instanceof ServerPlayerEntity player) {
                handleDeath(player, damageSource);
            }
        });

        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) ->
                newPlayer.getServer().execute(() -> applyPostRespawnState(newPlayer)));

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
            server.execute(() -> applyPostRespawnState(handler.player)));
    }

    private static void handleDeath(ServerPlayerEntity player, DamageSource source) {
        MinecraftServer server = player.getServer();
        DeathQuotaState state = DeathQuotaState.get(server);
        DeathRecord record = state.recordDeath(player.getUuid());
        int maxLives = getMaxLives(server);
        record.increment(maxLives);
        record.setLastDeath(player.getBlockPos(),
                player.getServerWorld().getRegistryKey().getValue().toString(),
                player.getYaw(),
                player.getPitch(),
                player.getServerWorld().getTime());
        record.setLastDeathMessage(source.getDeathMessage(player).getString());
        state.overwrite(player.getUuid(), record);

        int remaining = Math.max(0, maxLives - record.getDeathCount());
        MutableText feedback = Text.literal("[Death Quota] ");
        if (record.isSpectatorLocked()) {
            feedback.append(Text.literal("No remaining lives. You'll respawn as a spectator."));
        } else {
            feedback.append(Text.literal("Lives remaining: " + remaining + "/" + maxLives));
        }
        player.sendMessage(feedback, false);
        DeathQuotaMod.LOGGER.debug("Player {} now has {} deaths recorded", player.getGameProfile().getName(), record.getDeathCount());
    }

    public static void applyPostRespawnState(ServerPlayerEntity player) {
        DeathQuotaState state = DeathQuotaState.get(player.getServer());
        Optional<DeathRecord> record = state.get(player.getUuid());
        record.ifPresent(current -> {
            if (current.isSpectatorLocked()) {
                forceSpectator(player, current);
            } else {
                notifyLives(player, current);
            }
        });
    }

    private static void notifyLives(ServerPlayerEntity player, DeathRecord record) {
        int configuredMax = getMaxLives(player.getServer());
        int remaining = Math.max(0, configuredMax - record.getDeathCount());
        player.sendMessage(Text.literal("[Death Quota] Lives remaining: " + remaining + "/" + configuredMax), true);
    }

    public static boolean isSpectatorLocked(ServerPlayerEntity player) {
        return DeathQuotaState.get(player.getServer()).get(player.getUuid())
                .map(DeathRecord::isSpectatorLocked)
                .orElse(false);
    }

    public static DeathRecord ensureRecord(ServerPlayerEntity player) {
        DeathQuotaState state = DeathQuotaState.get(player.getServer());
        return state.get(player.getUuid()).orElseGet(() -> {
            DeathRecord record = new DeathRecord();
            state.overwrite(player.getUuid(), record);
            return record;
        });
    }

    public static DeathRecord reset(ServerPlayerEntity player) {
        DeathQuotaState state = DeathQuotaState.get(player.getServer());
        DeathRecord record = ensureRecord(player);
        record.reset();
        state.overwrite(player.getUuid(), record);
        return record;
    }

    public static int resetAll(MinecraftServer server) {
        return DeathQuotaState.get(server).resetAll();
    }

    public static int getMaxLives(MinecraftServer server) {
        return DeathQuotaConfig.get(server).getMaxLives();
    }

    public static Text describe(ServerPlayerEntity player) {
        DeathRecord record = ensureRecord(player);
        int maxLives = getMaxLives(player.getServer());
        int remaining = Math.max(0, maxLives - record.getDeathCount());
        MutableText text = Text.literal(player.getName().getString())
                .append(Text.literal(": deaths=" + record.getDeathCount()))
                .append(Text.literal(", remaining=" + remaining));
        if (record.isSpectatorLocked()) {
            text.append(Text.literal(" (LOCKED)"));
        }
        record.getLastDeathDimension().ifPresent(dim ->
                text.append(Text.literal(" dim=" + dim)));
        record.getLastDeathPos().ifPresent(pos ->
                text.append(Text.literal(" pos=" + pos.toShortString())));
        return text;
    }

    private static void forceSpectator(ServerPlayerEntity player, DeathRecord record) {
        record.setSpectatorLocked(true);
        DeathQuotaState.get(player.getServer()).overwrite(player.getUuid(), record);
        if (player.interactionManager.getGameMode() != GameMode.SPECTATOR) {
            player.changeGameMode(GameMode.SPECTATOR);
        }
        teleportToLastDeath(player, record);
        player.sendMessage(Text.literal("[Death Quota] You exhausted all lives. Spectate or disconnect."), false);
    }

    private static void teleportToLastDeath(ServerPlayerEntity player, DeathRecord record) {
        if (record.getLastDeathPos().isEmpty()) {
            return;
        }
        record.getLastDeathDimension().ifPresentOrElse(dimensionId -> {
            ServerWorld targetWorld = player.getServer().getWorld(ServerWorld.OVERWORLD);
            for (ServerWorld world : player.getServer().getWorlds()) {
                if (world.getRegistryKey().getValue().toString().equals(dimensionId)) {
                    targetWorld = world;
                    break;
                }
            }
            BlockPos pos = record.getLastDeathPos().orElse(player.getBlockPos());
                    player.teleport(targetWorld, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5,
                        Collections.<PositionFlag>emptySet(),
                    record.getLastYaw(), record.getLastPitch(), false);
        }, () -> {
            BlockPos pos = record.getLastDeathPos().orElse(player.getBlockPos());
                player.teleport(player.getServerWorld(), pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5,
                        Collections.<PositionFlag>emptySet(),
                    record.getLastYaw(), record.getLastPitch(), false);
        });
    }
}
