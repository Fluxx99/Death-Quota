package net.deathquota.mod.util;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Set;

/**
 * Compatibility layer for teleport API differences between 1.21.x versions.
 * 
 * In 1.21.0/1.21.1: teleport(ServerWorld, double, double, double, Set, float, float) - 7 params
 * In 1.21.2+: teleport(ServerWorld, double, double, double, Set, float, float, boolean) - 8 params
 * 
 * This class provides a unified teleport method that works on both versions.
 */
public final class TeleportCompat {
    private static final Method TELEPORT_7_PARAM;
    private static final Method TELEPORT_8_PARAM;
    private static final Method TELEPORT_6_PARAM;
    
    static {
        Method teleport6 = null;
        Method teleport7 = null;
        Method teleport8 = null;
        
        try {
            // Try 8-param version first (1.21.2+)
            teleport8 = ServerPlayerEntity.class.getMethod("teleport", 
                ServerWorld.class, double.class, double.class, double.class,
                Set.class, float.class, float.class, boolean.class);
        } catch (NoSuchMethodException e) {
            // 1.21.1 or earlier
        }
        
        if (teleport8 == null) {
            try {
                // Try 7-param version (1.21.0/1.21.1)
                teleport7 = ServerPlayerEntity.class.getMethod("teleport", 
                    ServerWorld.class, double.class, double.class, double.class,
                    Set.class, float.class, float.class);
            } catch (NoSuchMethodException e) {
                // Fallback - try simple teleport
            }
        }
        
        try {
            // 6-param fallback version that's commonly available
            teleport6 = ServerPlayerEntity.class.getMethod("teleport",
                ServerWorld.class, double.class, double.class, double.class,
                float.class, float.class);
        } catch (NoSuchMethodException e) {
            // May not exist
        }
        
        TELEPORT_6_PARAM = teleport6;
        TELEPORT_7_PARAM = teleport7;
        TELEPORT_8_PARAM = teleport8;
    }
    
    private TeleportCompat() {}
    
    /**
     * Teleports a player to the specified location.
     * Works on both 1.21.0/1.21.1 and 1.21.2+ versions.
     * 
     * @param player The player to teleport
     * @param world The target world
     * @param x X coordinate
     * @param y Y coordinate
     * @param z Z coordinate
     * @param yaw Player yaw
     * @param pitch Player pitch
     */
    public static void teleport(ServerPlayerEntity player, ServerWorld world, 
                                 double x, double y, double z, 
                                 float yaw, float pitch) {
        try {
            if (TELEPORT_8_PARAM != null) {
                // 1.21.2+ - 8 parameter version
                TELEPORT_8_PARAM.invoke(player, world, x, y, z, 
                    Collections.emptySet(), yaw, pitch, false);
            } else if (TELEPORT_7_PARAM != null) {
                // 1.21.0/1.21.1 - 7 parameter version
                TELEPORT_7_PARAM.invoke(player, world, x, y, z, 
                    Collections.emptySet(), yaw, pitch);
            } else if (TELEPORT_6_PARAM != null) {
                // Simple 6-param fallback
                TELEPORT_6_PARAM.invoke(player, world, x, y, z, yaw, pitch);
            } else {
                // Last resort fallback - use available method
                player.refreshPositionAndAngles(x, y, z, yaw, pitch);
            }
        } catch (Exception e) {
            // Fallback to position refresh if reflection fails
            player.refreshPositionAndAngles(x, y, z, yaw, pitch);
        }
    }
    
    /**
     * Teleports a player to a block position in their current world.
     */
    public static void teleportToPos(ServerPlayerEntity player, BlockPos pos, float yaw, float pitch) {
        teleport(player, ServerCompat.getWorld(player), 
                 pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, yaw, pitch);
    }
    
    /**
     * Teleports a player to a block position in a specific world.
     */
    public static void teleportToPos(ServerPlayerEntity player, ServerWorld world, BlockPos pos, float yaw, float pitch) {
        teleport(player, world, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, yaw, pitch);
    }
}
