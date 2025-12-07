package net.deathquota.mod.death;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;
import net.deathquota.mod.DeathQuotaMod;
import net.deathquota.mod.util.DataFixTypeCompat;
import net.deathquota.mod.util.PersistentStateCompat;

public final class DeathQuotaConfig extends PersistentState {
    private static final String STORAGE_KEY = DeathQuotaMod.MOD_ID + "_config";

    public static final Codec<DeathQuotaConfig> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.INT.optionalFieldOf("maxLives", DeathQuotaMod.DEFAULT_MAX_DEATHS)
                            .forGetter(config -> config.maxLives),
                    Codec.BOOL.optionalFieldOf("showDeathLocationMessages", true)
                            .forGetter(config -> config.showDeathLocationMessages)
            ).apply(instance, DeathQuotaConfig::fromValues)
    );

    // Cross-version compatible state type - uses reflection to handle API differences
    private static final Object STATE_TYPE = PersistentStateCompat.createType(
        STORAGE_KEY,
        DeathQuotaConfig::new,
        DeathQuotaConfig::readFromNbt,
        CODEC,
        DataFixTypeCompat.persistentDataType()
    );

    private int maxLives = DeathQuotaMod.DEFAULT_MAX_DEATHS;
    private boolean showDeathLocationMessages = true;

    private static DeathQuotaConfig fromValues(int maxLives, boolean showDeathLocationMessages) {
        DeathQuotaConfig config = new DeathQuotaConfig();
        config.maxLives = Math.max(1, maxLives);
        config.showDeathLocationMessages = showDeathLocationMessages;
        return config;
    }

    public static DeathQuotaConfig get(MinecraftServer server) {
        ServerWorld overworld = server.getOverworld();
        LegacyPersistentDataMigrator.migrate(server, "three_life_config", STORAGE_KEY);
        PersistentStateManager manager = overworld.getPersistentStateManager();
        return PersistentStateCompat.get(manager, STATE_TYPE, DeathQuotaConfig::readFromNbt, DeathQuotaConfig::new, STORAGE_KEY);
    }

    private static DeathQuotaConfig readFromNbt(NbtCompound nbt) {
        return CODEC.parse(NbtOps.INSTANCE, nbt)
                .resultOrPartial(error -> DeathQuotaMod.LOGGER.error("Failed to read death quota config: {}", error))
                .orElseGet(DeathQuotaConfig::new);
    }

    /**
     * Writes the config state to NBT for persistence.
     * Uses Codec-based serialization for consistency with Minecraft's data format.
     * Kept without @Override so it can service future API versions that drop the registry parameter.
     */
    public NbtCompound writeNbt(NbtCompound nbt) {
        return writeNbtCompat(nbt, null);
    }

    /**
     * Signature required on 1.21.0–1.21.4; kept without @Override so this class can still run on future versions.
     */
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        return writeNbtCompat(nbt, registries);
    }

    private NbtCompound writeNbtCompat(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        CODEC.encodeStart(NbtOps.INSTANCE, this)
                .resultOrPartial(error -> DeathQuotaMod.LOGGER.error("Failed to write death quota config: {}", error))
                .ifPresent(tag -> copyInto(nbt, tag));
        return nbt;
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

    public boolean isShowDeathLocationMessages() {
        return showDeathLocationMessages;
    }

    public void setShowDeathLocationMessages(boolean enabled) {
        DeathQuotaMod.LOGGER.info("setShowDeathLocationMessages: old={}, new={}", this.showDeathLocationMessages, enabled);
        if (this.showDeathLocationMessages != enabled) {
            this.showDeathLocationMessages = enabled;
            markDirty();
            DeathQuotaMod.LOGGER.info("Config marked dirty, showDeathLocationMessages now: {}", this.showDeathLocationMessages);
        }
    }

    private static void copyInto(NbtCompound into, NbtElement tag) {
        if (tag instanceof NbtCompound compound) {
            into.copyFrom(compound);
        }
    }
}
