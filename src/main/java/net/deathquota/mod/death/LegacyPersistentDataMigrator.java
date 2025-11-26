package net.deathquota.mod.death;

import net.deathquota.mod.DeathQuotaMod;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

final class LegacyPersistentDataMigrator {
    private LegacyPersistentDataMigrator() {
    }

    static void migrate(MinecraftServer server, String legacyKey, String targetKey) {
        Path dataDir = server.getSavePath(WorldSavePath.ROOT).resolve("data");

        migrateFile(dataDir, legacyKey + ".dat", targetKey + ".dat");
        migrateFile(dataDir, legacyKey + ".dat_old", targetKey + ".dat_old");
    }

    private static void migrateFile(Path dataDir, String legacyName, String targetName) {
        Path legacyFile = dataDir.resolve(legacyName);
        if (!Files.exists(legacyFile)) {
            return;
        }

        Path targetFile = dataDir.resolve(targetName);
        if (Files.exists(targetFile)) {
            DeathQuotaMod.LOGGER.warn("Legacy data file {} detected but {} already exists; skipping migration.", legacyName, targetName);
            return;
        }

        try {
            Files.createDirectories(dataDir);
            Files.move(legacyFile, targetFile);
            DeathQuotaMod.LOGGER.info("Migrated legacy data file {} -> {}", legacyName, targetName);
        } catch (IOException e) {
            DeathQuotaMod.LOGGER.error("Failed to migrate legacy data file {} -> {}", legacyName, targetName, e);
        }
    }
}
