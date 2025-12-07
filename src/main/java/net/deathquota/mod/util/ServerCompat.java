package net.deathquota.mod.util;

import net.deathquota.mod.DeathQuotaMod;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Compatibility layer for server access differences between 1.21.x versions.
 * 
 * In 1.21.0-1.21.9: ServerPlayerEntity.getServer() returns MinecraftServer
 * In 1.21.10+: Method renamed or changed
 * 
 * This class provides a unified way to get the server instance from a player.
 */
public final class ServerCompat {
    private static final Method GET_SERVER_METHOD;
    private static final Method GET_SERVER_WORLD_METHOD;
    private static final Method GET_WORLD_METHOD;
    private static final Field WORLD_FIELD;
    
    static {
        GET_SERVER_METHOD = findMethod("getServer");
        GET_SERVER_WORLD_METHOD = findMethod("getServerWorld");
        Method worldMethod = findMethod("getWorld");
        GET_WORLD_METHOD = worldMethod;
        WORLD_FIELD = findWorldField();
        DeathQuotaMod.LOGGER.info("ServerCompat initialized: getServer={}, getServerWorld={}, getWorld={}, worldField={}",
            GET_SERVER_METHOD != null, GET_SERVER_WORLD_METHOD != null, GET_WORLD_METHOD != null, WORLD_FIELD != null);
    }
    
    private ServerCompat() {}

    private static Method findMethod(String name) {
        try {
            Method method = ServerPlayerEntity.class.getMethod(name);
            DeathQuotaMod.LOGGER.info("ServerCompat: Found {}() method", name);
            return method;
        } catch (NoSuchMethodException e) {
            DeathQuotaMod.LOGGER.warn("ServerCompat: {}() method not found", name);
            return null;
        }
    }

    private static Field findWorldField() {
        try {
            Field field = Class.forName("net.minecraft.entity.Entity").getDeclaredField("world");
            field.setAccessible(true);
            DeathQuotaMod.LOGGER.info("ServerCompat: Found Entity.world field");
            return field;
        } catch (Exception e) {
            DeathQuotaMod.LOGGER.warn("ServerCompat: Entity.world field not accessible", e);
            return null;
        }
    }
    
    /**
     * Gets the MinecraftServer instance from a ServerPlayerEntity.
     * Works across all 1.21.x versions.
     * 
     * @param player The player to get the server from
     * @return The MinecraftServer instance
     */
    public static MinecraftServer getServer(ServerPlayerEntity player) {
        try {
            if (GET_SERVER_METHOD != null) {
                return (MinecraftServer) GET_SERVER_METHOD.invoke(player);
            }
        } catch (Exception e) {
            DeathQuotaMod.LOGGER.error("ServerCompat: invoking getServer() failed", e);
        }

        ServerWorld world = getWorldFromReflection(player);
        if (world == null) {
            world = directWorldCast(player);
        }
        if (world != null) {
            return world.getServer();
        }

        throw new IllegalStateException("Failed to resolve MinecraftServer from ServerPlayerEntity");
    }

    public static ServerWorld getWorld(ServerPlayerEntity player) {
        ServerWorld world = getWorldFromReflection(player);
        if (world != null) {
            return world;
        }

        world = directWorldCast(player);
        if (world != null) {
            return world;
        }

        return getServer(player).getOverworld();
    }

    private static ServerWorld getWorldFromReflection(ServerPlayerEntity player) {
        try {
            if (GET_SERVER_WORLD_METHOD != null) {
                Object world = GET_SERVER_WORLD_METHOD.invoke(player);
                if (world instanceof ServerWorld serverWorld) {
                    return serverWorld;
                }
            }
            if (GET_WORLD_METHOD != null) {
                Object world = GET_WORLD_METHOD.invoke(player);
                if (world instanceof ServerWorld serverWorld) {
                    return serverWorld;
                }
            }
        } catch (Exception e) {
            DeathQuotaMod.LOGGER.error("ServerCompat: Failed to resolve player world via reflection", e);
        }
        return null;
    }

    private static ServerWorld directWorldCast(ServerPlayerEntity player) {
        if (WORLD_FIELD != null) {
            try {
                Object world = WORLD_FIELD.get(player);
                if (world instanceof ServerWorld serverWorld) {
                    return serverWorld;
                }
            } catch (Exception e) {
                DeathQuotaMod.LOGGER.error("ServerCompat: reading Entity.world failed", e);
            }
        }
        return null;
    }
}
