package net.deathquota.mod.death;

import com.mojang.serialization.Codec;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;

import java.util.Optional;

public final class DeathRecord {
    public static final Codec<DeathRecord> CODEC = NbtCompound.CODEC.xmap(DeathRecord::fromNbt, DeathRecord::toNbt);

    private int deathCount;
    private boolean spectatorLocked;
    private BlockPos lastDeathPos;
    private String lastDeathDimension;
    private String lastDeathMessage;
    private long lastDeathGameTime;
    private float lastYaw;
    private float lastPitch;

    public int increment(int maxLives) {
        deathCount++;
        if (deathCount >= maxLives) {
            spectatorLocked = true;
        }
        return deathCount;
    }

    public int getDeathCount() {
        return deathCount;
    }

    public boolean isSpectatorLocked() {
        return spectatorLocked;
    }

    public void setSpectatorLocked(boolean spectatorLocked) {
        this.spectatorLocked = spectatorLocked;
    }

    public Optional<BlockPos> getLastDeathPos() {
        return Optional.ofNullable(lastDeathPos);
    }

    public Optional<String> getLastDeathDimension() {
        return Optional.ofNullable(lastDeathDimension);
    }

    public Optional<String> getLastDeathMessage() {
        return Optional.ofNullable(lastDeathMessage);
    }

    public long getLastDeathGameTime() {
        return lastDeathGameTime;
    }

    public float getLastYaw() {
        return lastYaw;
    }

    public float getLastPitch() {
        return lastPitch;
    }

    public void setLastDeath(BlockPos pos, String dimensionId, float yaw, float pitch, long gameTime) {
        this.lastDeathPos = pos;
        this.lastDeathDimension = dimensionId;
        this.lastYaw = yaw;
        this.lastPitch = pitch;
        this.lastDeathGameTime = gameTime;
    }

    public void setLastDeathMessage(String message) {
        this.lastDeathMessage = message;
    }

    public void reset() {
        deathCount = 0;
        spectatorLocked = false;
        lastDeathPos = null;
        lastDeathDimension = null;
        lastDeathMessage = null;
        lastDeathGameTime = 0L;
        lastYaw = 0;
        lastPitch = 0;
    }

    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putInt("deaths", deathCount);
        nbt.putBoolean("locked", spectatorLocked);
        if (lastDeathPos != null) {
            nbt.putLong("pos", lastDeathPos.asLong());
        }
        if (lastDeathDimension != null) {
            nbt.putString("dimension", lastDeathDimension);
        }
        if (lastDeathMessage != null) {
            nbt.putString("message", lastDeathMessage);
        }
        nbt.putLong("time", lastDeathGameTime);
        nbt.putFloat("yaw", lastYaw);
        nbt.putFloat("pitch", lastPitch);
        return nbt;
    }

    public static DeathRecord fromNbt(NbtCompound nbt) {
        DeathRecord record = new DeathRecord();
        record.deathCount = nbt.getInt("deaths").orElse(0);
        record.spectatorLocked = nbt.getBoolean("locked").orElse(false);
        nbt.getLong("pos").ifPresent(value -> record.lastDeathPos = BlockPos.fromLong(value));
        nbt.getString("dimension").ifPresent(value -> record.lastDeathDimension = value);
        nbt.getString("message").ifPresent(value -> record.lastDeathMessage = value);
        record.lastDeathGameTime = nbt.getLong("time").orElse(0L);
        record.lastYaw = nbt.getFloat("yaw").orElse(0f);
        record.lastPitch = nbt.getFloat("pitch").orElse(0f);
        return record;
    }
}
