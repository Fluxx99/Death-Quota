package net.deathquota.mod.util;

import net.minecraft.datafixer.DataFixTypes;

/**
 * Provides compatibility helpers for selecting a persistent-data DataFixTypes
 * value that exists across 1.21.x versions. Newer versions ship extra
 * constants (e.g. SAVED_DATA_RANDOM_SEQUENCES) that are absent on 1.21.1, so
 * we resolve them via reflection once and fall back to a safe baseline.
 */
public final class DataFixTypeCompat {
    private static final DataFixTypes PERSISTENT_DATA_TYPE = resolvePersistentType();

    private DataFixTypeCompat() {
    }

    public static DataFixTypes persistentDataType() {
        return PERSISTENT_DATA_TYPE;
    }

    private static DataFixTypes resolvePersistentType() {
        try {
            return (DataFixTypes) DataFixTypes.class.getField("SAVED_DATA_RANDOM_SEQUENCES").get(null);
        } catch (ReflectiveOperationException ignored) {
            return DataFixTypes.LEVEL;
        }
    }
}
