package com.bowrangehit.mixin;

import com.bowrangehit.BowRangeHitClient;
import net.minecraft.client.Minecraft;
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
		// this method first runs on the network thread, then again on the main thread: only use the main one
		if (!Minecraft.getInstance().isSameThread()) return;
		BowRangeHitClient.onDamage(packet);
	}
}
