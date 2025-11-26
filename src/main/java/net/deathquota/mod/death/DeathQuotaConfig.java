package net.deathquota.mod.death;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;
import net.minecraft.world.PersistentStateType;
import net.deathquota.mod.DeathQuotaMod;

public final class DeathQuotaConfig extends PersistentState {
    private static final String STORAGE_KEY = DeathQuotaMod.MOD_ID + "_config";

    public static final Codec<DeathQuotaConfig> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.INT.optionalFieldOf("maxLives", DeathQuotaMod.DEFAULT_MAX_DEATHS)
                            .forGetter(config -> config.maxLives)
            ).apply(instance, DeathQuotaConfig::fromMaxLives)
    );

    public static final PersistentStateType<DeathQuotaConfig> STATE_TYPE = new PersistentStateType<>(
            STORAGE_KEY,
            DeathQuotaConfig::new,
            CODEC,
            DataFixTypes.SAVED_DATA_RANDOM_SEQUENCES
    );

    private int maxLives = DeathQuotaMod.DEFAULT_MAX_DEATHS;

    private static DeathQuotaConfig fromMaxLives(int maxLives) {
        DeathQuotaConfig config = new DeathQuotaConfig();
        config.maxLives = Math.max(1, maxLives);
        return config;
    }

    public static DeathQuotaConfig get(MinecraftServer server) {
        ServerWorld overworld = server.getOverworld();
        LegacyPersistentDataMigrator.migrate(server, "three_life_config", STORAGE_KEY);
        PersistentStateManager manager = overworld.getPersistentStateManager();
        return manager.getOrCreate(STATE_TYPE);
    }

    public int getMaxLives() {
        return maxLives;
    }

    public void setMaxLives(int maxLives) {
        int clamped = Math.max(1, maxLives);
        if (this.maxLives != clamped) {
            this.maxLives = clamped;
            markDirty();
        }
    }
}
