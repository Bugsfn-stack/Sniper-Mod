package com.bowrangehit.mixin;

import com.bowrangehit.BowRangeHitClient;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {
	@Inject(method = "handleDamageEvent", at = @At("HEAD"))
	private void bowrangehit$onDamage(ClientboundDamageEventPacket packet, CallbackInfo ci) {
		BowRangeHitClient.onDamage(packet);
	}
}
