package net.deathquota.mod.death;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;
import net.deathquota.mod.DeathQuotaMod;
import net.deathquota.mod.util.ServerCompat;
import net.deathquota.mod.util.TeleportCompat;

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
                ServerCompat.getServer(newPlayer).execute(() -> applyPostRespawnState(newPlayer)));

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
            server.execute(() -> applyPostRespawnState(handler.player)));

        // Continuously enforce spectator mode for locked players (1.21.5 compatibility)
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                if (isSpectatorLocked(player) && player.interactionManager.getGameMode() != GameMode.SPECTATOR) {
                    DeathQuotaMod.LOGGER.warn("Player {} escaped spectator lock, re-enforcing", player.getName().getString());
                    player.changeGameMode(GameMode.SPECTATOR);
                }
            }
        });
    }

    private static void handleDeath(ServerPlayerEntity player, DamageSource source) {
        MinecraftServer server = ServerCompat.getServer(player);
        DeathQuotaState state = DeathQuotaState.get(server);
        DeathRecord record = state.recordDeath(player.getUuid());
        int maxLives = getMaxLives(server);
        record.increment(maxLives);
        ServerWorld playerWorld = ServerCompat.getWorld(player);
        record.setLastDeath(player.getBlockPos(),
            playerWorld.getRegistryKey().getValue().toString(),
            player.getYaw(),
            player.getPitch(),
            playerWorld.getTime());
        record.setLastDeathMessage(source.getDeathMessage(player).getString());
        state.overwrite(player.getUuid(), record);

        sendDeathLocationMessage(player, record);

        int remaining = Math.max(0, maxLives - record.getDeathCount());
        MutableText feedback = Text.literal("[Death Quota] ");
        if (record.isSpectatorLocked()) {
            feedback.append(Text.literal("No remaining lives. You'll respawn as a spectator."));
        } else {
            feedback.append(Text.literal("Lives remaining: " + remaining + "/" + maxLives));
        }
        player.sendMessage(feedback, false);
        DeathQuotaMod.LOGGER.debug("Player {} now has {} deaths recorded", player.getName().getString(), record.getDeathCount());
    }

    public static void applyPostRespawnState(ServerPlayerEntity player) {
        DeathQuotaState state = DeathQuotaState.get(ServerCompat.getServer(player));
        Optional<DeathRecord> record = state.get(player.getUuid());
        record.ifPresent(current -> {
            if (current.isSpectatorLocked()) {
                forceSpectator(player, current);
                // Add delayed enforcement for 1.21.5 compatibility where gamemode might not stick immediately
                ServerCompat.getServer(player).execute(() -> {
                    if (player.interactionManager.getGameMode() != GameMode.SPECTATOR) {
                        DeathQuotaMod.LOGGER.warn("Player {} escaped spectator lock, re-enforcing", player.getName().getString());
                        player.changeGameMode(GameMode.SPECTATOR);
                    }
                });
            } else {
                notifyLives(player, current);
            }
        });
    }

    private static void notifyLives(ServerPlayerEntity player, DeathRecord record) {
        int configuredMax = getMaxLives(ServerCompat.getServer(player));
        int remaining = Math.max(0, configuredMax - record.getDeathCount());
        player.sendMessage(Text.literal("[Death Quota] Lives remaining: " + remaining + "/" + configuredMax), true);
    }

    public static boolean isSpectatorLocked(ServerPlayerEntity player) {
        return DeathQuotaState.get(ServerCompat.getServer(player)).get(player.getUuid())
                .map(DeathRecord::isSpectatorLocked)
                .orElse(false);
    }

    public static DeathRecord ensureRecord(ServerPlayerEntity player) {
        DeathQuotaState state = DeathQuotaState.get(ServerCompat.getServer(player));
        return state.get(player.getUuid()).orElseGet(() -> {
            DeathRecord record = new DeathRecord();
            state.overwrite(player.getUuid(), record);
            return record;
        });
    }

    public static DeathRecord reset(ServerPlayerEntity player) {
        DeathQuotaState state = DeathQuotaState.get(ServerCompat.getServer(player));
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
        int maxLives = getMaxLives(ServerCompat.getServer(player));
        int remaining = Math.max(0, maxLives - record.getDeathCount());
        MutableText text = Text.literal(player.getName().getString())
                .append(Text.literal(": deaths=" + record.getDeathCount()))
                .append(Text.literal(", remaining=" + remaining));
        if (record.isSpectatorLocked()) {
            text.append(Text.literal(" (LOCKED)"));
        }
        return text;
    }

    private static void forceSpectator(ServerPlayerEntity player, DeathRecord record) {
        record.setSpectatorLocked(true);
        DeathQuotaState.get(ServerCompat.getServer(player)).overwrite(player.getUuid(), record);
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
            MinecraftServer server = ServerCompat.getServer(player);
            ServerWorld targetWorld = server.getWorld(ServerWorld.OVERWORLD);
            for (ServerWorld world : server.getWorlds()) {
                if (world.getRegistryKey().getValue().toString().equals(dimensionId)) {
                    targetWorld = world;
                    break;
                }
            }
            BlockPos pos = record.getLastDeathPos().orElse(player.getBlockPos());
            // Use TeleportCompat for cross-version compatibility (1.21.0-1.21.1 vs 1.21.2+)
            TeleportCompat.teleportToPos(player, targetWorld, pos, record.getLastYaw(), record.getLastPitch());
        }, () -> {
            BlockPos pos = record.getLastDeathPos().orElse(player.getBlockPos());
            TeleportCompat.teleportToPos(player, pos, record.getLastYaw(), record.getLastPitch());
        });
    }

    private static void sendDeathLocationMessage(ServerPlayerEntity player, DeathRecord record) {
        // Check global config setting
        DeathQuotaConfig config = DeathQuotaConfig.get(ServerCompat.getServer(player));
        boolean enabled = config.isShowDeathLocationMessages();
        DeathQuotaMod.LOGGER.debug("sendDeathLocationMessage: enabled={}", enabled);
        if (!enabled) {
            return;
        }
        MutableText message = Text.literal("[Death Quota] ");
        record.getLastDeathPos().ifPresentOrElse(pos -> {
            String dimension = record.getLastDeathDimension().orElse("unknown");
            message.append(Text.literal("Death at " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ()))
                    .append(Text.literal(" in " + dimension));
        }, () -> message.append(Text.literal("Death location unavailable.")));
        player.sendMessage(message, false);
    }
}
