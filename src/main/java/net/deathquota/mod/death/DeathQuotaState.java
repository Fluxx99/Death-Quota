package net.deathquota.mod.death;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Uuids;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;
import net.minecraft.world.PersistentStateType;
import net.deathquota.mod.DeathQuotaMod;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class DeathQuotaState extends PersistentState {
    private static final String STORAGE_KEY = DeathQuotaMod.MOD_ID + "_quota";

    public static final Codec<DeathQuotaState> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.unboundedMap(Uuids.CODEC, DeathRecord.CODEC)
                            .optionalFieldOf("records", Map.of())
                            .forGetter(state -> state.records)
            ).apply(instance, DeathQuotaState::fromRecords)
    );

    public static final PersistentStateType<DeathQuotaState> STATE_TYPE = new PersistentStateType<>(
            STORAGE_KEY,
            DeathQuotaState::new,
            CODEC,
            DataFixTypes.SAVED_DATA_RANDOM_SEQUENCES
    );

    private final Map<UUID, DeathRecord> records = new Object2ObjectOpenHashMap<>();

    private static DeathQuotaState fromRecords(Map<UUID, DeathRecord> records) {
        DeathQuotaState state = new DeathQuotaState();
        state.records.putAll(records);
        return state;
    }

    public static DeathQuotaState get(MinecraftServer server) {
        ServerWorld overworld = server.getOverworld();
        LegacyPersistentDataMigrator.migrate(server, "three_life_quota", STORAGE_KEY);
        PersistentStateManager manager = overworld.getPersistentStateManager();
        return manager.getOrCreate(STATE_TYPE);
    }

    public DeathRecord recordDeath(UUID uuid) {
        DeathRecord record = records.computeIfAbsent(uuid, ignored -> new DeathRecord());
        markDirty();
        return record;
    }

    public Optional<DeathRecord> get(UUID uuid) {
        return Optional.ofNullable(records.get(uuid));
    }

    public void overwrite(UUID uuid, DeathRecord updated) {
        records.put(uuid, updated);
        markDirty();
    }

    public void remove(UUID uuid) {
        if (records.remove(uuid) != null) {
            markDirty();
        }
    }

    public int resetAll() {
        if (records.isEmpty()) {
            return 0;
        }
        records.values().forEach(DeathRecord::reset);
        markDirty();
        return records.size();
    }

    public int reconcileLocks(int maxLives) {
        if (records.isEmpty()) {
            return 0;
        }
        int changes = 0;
        for (DeathRecord record : records.values()) {
            boolean shouldLock = record.getDeathCount() >= maxLives;
            if (record.isSpectatorLocked() != shouldLock) {
                record.setSpectatorLocked(shouldLock);
                changes++;
            }
        }
        if (changes > 0) {
            markDirty();
        }
        return changes;
    }

}
