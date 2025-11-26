package net.deathquota.mod;

import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.deathquota.mod.command.DeathQuotaCommands;
import net.deathquota.mod.death.DeathQuotaManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DeathQuotaMod implements DedicatedServerModInitializer {
    public static final String MOD_ID = "death_quota";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static final int DEFAULT_MAX_DEATHS = 3;

    @Override
    public void onInitializeServer() {
        DeathQuotaManager.registerEventHooks();
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                DeathQuotaCommands.register(dispatcher, registryAccess));
        LOGGER.info("Death Quota server rules armed. Players have {} lives per world by default.", DEFAULT_MAX_DEATHS);
    }
}
