package net.deathquota.mod.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.deathquota.mod.death.DeathQuotaConfig;
import net.deathquota.mod.death.DeathQuotaManager;
import net.deathquota.mod.death.DeathQuotaState;
import net.deathquota.mod.death.DeathRecord;

import static net.minecraft.command.argument.EntityArgumentType.getPlayer;
import static net.minecraft.command.argument.EntityArgumentType.player;
import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;

public final class DeathQuotaCommands {
    private DeathQuotaCommands() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess) {
        dispatcher.register(CommandManager.literal("deathquota")
                .executes(DeathQuotaCommands::selfInfo)
                .then(CommandManager.literal("info")
                        .requires(source -> source.hasPermissionLevel(2))
                        .then(CommandManager.argument("target", player())
                                .executes(ctx -> infoAbout(ctx, getPlayer(ctx, "target")))))
                .then(CommandManager.literal("reset")
                        .requires(source -> source.hasPermissionLevel(2))
                        .then(CommandManager.argument("target", player())
                        .executes(ctx -> resetTarget(ctx, getPlayer(ctx, "target")))))
                .then(CommandManager.literal("resetall")
                    .requires(source -> source.hasPermissionLevel(2))
                    .executes(DeathQuotaCommands::resetAllPlayers))
                .then(CommandManager.literal("setmax")
                    .requires(source -> source.hasPermissionLevel(2))
                    .then(CommandManager.argument("value", integer(1, 99))
                        .executes(ctx -> setMaxLives(ctx, getInteger(ctx, "value"))))));
    }

    private static int selfInfo(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        ctx.getSource().sendFeedback(() -> DeathQuotaManager.describe(player), false);
        return 1;
    }

    private static int infoAbout(CommandContext<ServerCommandSource> ctx, ServerPlayerEntity target) {
        ctx.getSource().sendFeedback(() -> DeathQuotaManager.describe(target), false);
        return 1;
    }

    private static int resetTarget(CommandContext<ServerCommandSource> ctx, ServerPlayerEntity target) {
        DeathRecord record = DeathQuotaManager.reset(target);
        Text text = Text.literal("Reset death quota for ").append(target.getDisplayName())
            .append(Text.literal(" (" + record.getDeathCount() + " deaths now)"));
        ctx.getSource().sendFeedback(() -> text, true);
        target.sendMessage(Text.literal("[Death Quota] An operator reset your lives."), false);
        return 1;
    }

    private static int resetAllPlayers(CommandContext<ServerCommandSource> ctx) {
        int affected = DeathQuotaManager.resetAll(ctx.getSource().getServer());
        Text feedback = Text.literal("Reset death quotas for " + affected + " stored player(s).");
        ctx.getSource().sendFeedback(() -> feedback, true);
        ctx.getSource().getServer().getPlayerManager().getPlayerList()
                .forEach(player -> player.sendMessage(Text.literal("[Death Quota] An operator reset everyone's lives."), false));
        return affected;
    }

    private static int setMaxLives(CommandContext<ServerCommandSource> ctx, int value) {
        ServerCommandSource source = ctx.getSource();
        MinecraftServer server = source.getServer();
        DeathQuotaConfig config = DeathQuotaConfig.get(server);
        config.setMaxLives(value);
        int changes = DeathQuotaState.get(server).reconcileLocks(config.getMaxLives());
        server.getPlayerManager().getPlayerList().forEach(DeathQuotaManager::applyPostRespawnState);
        Text feedback = Text.literal("Set max lives to " + config.getMaxLives() + ". Adjusted " + changes + " stored record(s).");
        source.sendFeedback(() -> feedback, true);
        return config.getMaxLives();
    }
}
