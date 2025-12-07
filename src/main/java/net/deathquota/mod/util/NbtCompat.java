package net.deathquota.mod.util;

import net.deathquota.mod.DeathQuotaMod;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;

import java.lang.reflect.Method;
import java.util.Optional;

/**
 * Compatibility layer for NbtCompound API differences between 1.21.x versions.
 * 
 * Handles three different API patterns:
 * - 1.21.0-1.21.1: Direct primitive returns (getInt(String) returns int)
 * - 1.21.2-1.21.9: Optional wrapped returns (getInt(String) returns Optional<Integer>)
 * - 1.21.10+: Removed direct accessors, must use get(String) and extract value
 * 
 * This class provides unified access methods that work on all versions via reflection.
 */
public final class NbtCompat {
    private NbtCompat() {}
    
    /**
     * Gets an int value from the NBT compound, returning default if not present.
     */
    public static int getInt(NbtCompound nbt, String key, int defaultValue) {
        if (!nbt.contains(key)) {
            return defaultValue;
        }
        try {
            // Try get(String) method first (1.21.10+)
            Object value = nbt.get(key);
            if (value != null) {
                Method intValueMethod = value.getClass().getMethod("intValue");
                return (int) intValueMethod.invoke(value);
            }
        } catch (Exception ignored) {
        }
        
        try {
            // Try getInt(String) via reflection
            Method getIntMethod = NbtCompound.class.getMethod("getInt", String.class);
            Object result = getIntMethod.invoke(nbt, key);
            
            // Check if it's Optional (1.21.2-1.21.9)
            if (result instanceof Optional<?> opt) {
                if (opt.isPresent()) {
                    return (int) opt.get();
                }
                return defaultValue;
            }
            // Direct primitive (1.21.0-1.21.1)
            if (result instanceof Integer i) {
                return i;
            }
        } catch (Exception e) {
            DeathQuotaMod.LOGGER.debug("Failed to get int from NBT: {}", e.getMessage());
        }
        return defaultValue;
    }
    
    /**
     * Gets a boolean value from the NBT compound, returning default if not present.
     */
    public static boolean getBoolean(NbtCompound nbt, String key, boolean defaultValue) {
        if (!nbt.contains(key)) {
            return defaultValue;
        }
        try {
            // Try get(String) method first (1.21.10+)
            Object value = nbt.get(key);
            if (value != null) {
                Method byteValueMethod = value.getClass().getMethod("byteValue");
                byte b = (byte) byteValueMethod.invoke(value);
                return b != 0;
            }
        } catch (Exception ignored) {
        }
        
        try {
            // Try getBoolean(String) via reflection
            Method getBoolMethod = NbtCompound.class.getMethod("getBoolean", String.class);
            Object result = getBoolMethod.invoke(nbt, key);
            
            if (result instanceof Optional<?> opt) {
                if (opt.isPresent()) {
                    return (boolean) opt.get();
                }
                return defaultValue;
            }
            if (result instanceof Boolean b) {
                return b;
            }
        } catch (Exception e) {
            DeathQuotaMod.LOGGER.debug("Failed to get boolean from NBT: {}", e.getMessage());
        }
        return defaultValue;
    }
    
    /**
     * Gets a long value from the NBT compound, returning default if not present.
     */
    public static long getLong(NbtCompound nbt, String key, long defaultValue) {
        if (!nbt.contains(key)) {
            return defaultValue;
        }
        try {
            // Try get(String) method first (1.21.10+)
            Object value = nbt.get(key);
            if (value != null) {
                Method longValueMethod = value.getClass().getMethod("longValue");
                return (long) longValueMethod.invoke(value);
            }
        } catch (Exception ignored) {
        }
        
        try {
            // Try getLong(String) via reflection
            Method getLongMethod = NbtCompound.class.getMethod("getLong", String.class);
            Object result = getLongMethod.invoke(nbt, key);
            
            if (result instanceof Optional<?> opt) {
                if (opt.isPresent()) {
                    return (long) opt.get();
                }
                return defaultValue;
            }
            if (result instanceof Long l) {
                return l;
            }
        } catch (Exception e) {
            DeathQuotaMod.LOGGER.debug("Failed to get long from NBT: {}", e.getMessage());
        }
        return defaultValue;
    }
    
    /**
     * Gets a float value from the NBT compound, returning default if not present.
     */
    public static float getFloat(NbtCompound nbt, String key, float defaultValue) {
        if (!nbt.contains(key)) {
            return defaultValue;
        }
        try {
            // Try get(String) method first (1.21.10+)
            Object value = nbt.get(key);
            if (value != null) {
                Method floatValueMethod = value.getClass().getMethod("floatValue");
                return (float) floatValueMethod.invoke(value);
            }
        } catch (Exception ignored) {
        }
        
        try {
            // Try getFloat(String) via reflection
            Method getFloatMethod = NbtCompound.class.getMethod("getFloat", String.class);
            Object result = getFloatMethod.invoke(nbt, key);
            
            if (result instanceof Optional<?> opt) {
                if (opt.isPresent()) {
                    return (float) opt.get();
                }
                return defaultValue;
            }
            if (result instanceof Float f) {
                return f;
            }
        } catch (Exception e) {
            DeathQuotaMod.LOGGER.debug("Failed to get float from NBT: {}", e.getMessage());
        }
        return defaultValue;
    }
    
    /**
     * Gets a String value from the NBT compound, returning default if not present.
     */
    public static String getString(NbtCompound nbt, String key, String defaultValue) {
        if (!nbt.contains(key)) {
            return defaultValue;
        }
        try {
            // Try get(String) method first (1.21.10+)
            Object value = nbt.get(key);
            if (value != null) {
                Method asStringMethod = value.getClass().getMethod("asString");
                return (String) asStringMethod.invoke(value);
            }
        } catch (Exception ignored) {
        }
        
        try {
            // Try getString(String) via reflection
            Method getStringMethod = NbtCompound.class.getMethod("getString", String.class);
            Object result = getStringMethod.invoke(nbt, key);
            
            if (result instanceof Optional<?> opt) {
                if (opt.isPresent()) {
                    String s = (String) opt.get();
                    return s.isEmpty() && defaultValue != null ? defaultValue : s;
                }
                return defaultValue;
            }
            if (result instanceof String s) {
                return s.isEmpty() && defaultValue != null ? defaultValue : s;
            }
        } catch (Exception e) {
            DeathQuotaMod.LOGGER.debug("Failed to get string from NBT: {}", e.getMessage());
        }
        return defaultValue;
    }
    
    /**
     * Gets an Optional<BlockPos> for pos values.
     */
    public static Optional<BlockPos> getBlockPos(NbtCompound nbt, String key) {
        if (!nbt.contains(key)) {
            return Optional.empty();
        }
        long posLong = getLong(nbt, key, 0L);
        return posLong != 0L ? Optional.of(BlockPos.fromLong(posLong)) : Optional.empty();
    }
    
    /**
     * Gets an Optional<String> for string values that might not exist.
     */
    public static Optional<String> getOptionalString(NbtCompound nbt, String key) {
        if (!nbt.contains(key)) {
            return Optional.empty();
        }
        String value = getString(nbt, key, null);
        return value != null && !value.isEmpty() ? Optional.of(value) : Optional.empty();
    }
}
