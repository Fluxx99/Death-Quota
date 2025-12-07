package net.deathquota.mod.death;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Uuids;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;
import net.deathquota.mod.DeathQuotaMod;
import net.deathquota.mod.util.DataFixTypeCompat;
import net.deathquota.mod.util.PersistentStateCompat;

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

    // Cross-version compatible state type bridging all 1.21.x flavors via reflection
    private static final Object STATE_TYPE = PersistentStateCompat.createType(
        STORAGE_KEY,           // storage ID (not MOD_ID!)
        DeathQuotaState::new,  // constructor
        DeathQuotaState::readFromNbt,  // NBT reader (unused in modern API)
        CODEC,                 // serialization codec
        DataFixTypeCompat.persistentDataType()  // fix type (can be null)
    );

    private final Map<UUID, DeathRecord> records = new Object2ObjectOpenHashMap<>();

    private static DeathQuotaState fromRecords(Map<UUID, DeathRecord> records) {
        DeathQuotaState state = new DeathQuotaState();
        state.records.putAll(records);
        return state;
    }

    private static DeathQuotaState readFromNbt(NbtCompound nbt) {
        DeathQuotaState state = CODEC.parse(NbtOps.INSTANCE, nbt)
                .resultOrPartial(error -> DeathQuotaMod.LOGGER.error("Failed to read death quota state: {}", error))
                .orElseGet(DeathQuotaState::new);
        DeathQuotaMod.LOGGER.debug("Loaded death quota state with {} records", state.records.size());
        return state;
    }

    public static DeathQuotaState get(MinecraftServer server) {
        ServerWorld overworld = server.getOverworld();
        LegacyPersistentDataMigrator.migrate(server, "three_life_quota", STORAGE_KEY);
        PersistentStateManager manager = overworld.getPersistentStateManager();
        return PersistentStateCompat.get(
            manager, 
            STATE_TYPE, 
            DeathQuotaState::readFromNbt, 
            DeathQuotaState::new, 
            STORAGE_KEY
        );
    }

    /**
     * Writes the death quota state to NBT for persistence.
     * Uses Codec-based serialization for consistency with Minecraft's data format.
     * No @Override so it can serve potential future signatures without registry parameters.
     */
    public NbtCompound writeNbt(NbtCompound nbt) {
        return writeNbtCompat(nbt, null);
    }

    /**
     * Signature required on 1.21.0–1.21.4; no @Override so this class stays loadable when the API drops the lookup.
     */
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        return writeNbtCompat(nbt, registryLookup);
    }

    private NbtCompound writeNbtCompat(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        CODEC.encodeStart(NbtOps.INSTANCE, this)
                .resultOrPartial(error -> DeathQuotaMod.LOGGER.error("Failed to write death quota state: {}", error))
                .ifPresent(tag -> copyInto(nbt, tag));
        return nbt;
    }

    private static void copyInto(NbtCompound into, NbtElement tag) {
        if (tag instanceof NbtCompound compound) {
            into.copyFrom(compound);
        }
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
