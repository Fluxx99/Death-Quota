package net.deathquota.mod.mixin;

import net.minecraft.network.packet.c2s.play.ClientStatusC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.deathquota.mod.death.DeathQuotaManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayNetworkHandler.class)
public abstract class ServerPlayNetworkHandlerMixin {
    @Shadow public ServerPlayerEntity player;

    @Inject(method = "onClientStatus", at = @At("HEAD"), cancellable = true)
    private void deathQuota$interceptRespawn(ClientStatusC2SPacket packet, CallbackInfo ci) {
        if (packet.getMode() == ClientStatusC2SPacket.Mode.PERFORM_RESPAWN
                && DeathQuotaManager.isSpectatorLocked(this.player)) {
            DeathQuotaManager.applyPostRespawnState(this.player);
            ci.cancel();
        }
    }
}
