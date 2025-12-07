package net.deathquota.mod;

import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.deathquota.mod.command.DeathQuotaCommands;
import net.deathquota.mod.death.DeathQuotaManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DeathQuotaMod implements DedicatedServerModInitializer, ModInitializer {
    public static final String MOD_ID = "death_quota";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static final int DEFAULT_MAX_DEATHS = 3;
    private static boolean initialized;

    @Override
    public void onInitializeServer() {
        initialize();
    }

    @Override
    public void onInitialize() {
        initialize();
    }

    private static synchronized void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;
        DeathQuotaManager.registerEventHooks();
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                DeathQuotaCommands.register(dispatcher, registryAccess));
        LOGGER.info("Death Quota rules armed. Players have {} lives per world by default.", DEFAULT_MAX_DEATHS);
    }
}
