package com.bowrangehit;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.nio.file.Files;
import java.nio.file.Path;

public class BowRangeHitClient implements ClientModInitializer {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	/** Only hits at least this far (in blocks) are shown. Editable in config/bowrangehit.json. */
	private static double minDistance = 50.0;

	/** Max age (ticks) of a bow release that can still be credited for a hit. */
	private static final long MAX_FLIGHT_TICKS = 200;

	private static boolean wasUsingBow = false;
	private static Vec3 shotPos = null;
	private static long shotTick = -1000;

	@Override
	public void onInitializeClient() {
		loadConfig();
		ClientTickEvents.END_CLIENT_TICK.register(BowRangeHitClient::tick);
	}

	private static void tick(Minecraft mc) {
		LocalPlayer player = mc.player;
		if (player == null || mc.level == null) {
			wasUsingBow = false;
			return;
		}
		boolean usingBow = player.isUsingItem() && player.getUseItem().is(Items.BOW);
		if (wasUsingBow && !usingBow) {
			// bow released: remember where the shot started
			shotPos = player.getEyePosition();
			shotTick = mc.level.getGameTime();
		}
		wasUsingBow = usingBow;
	}

	/** Called from the mixin for every damage event the client receives. */
	public static void onDamage(ClientboundDamageEventPacket packet) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null || mc.level == null || shotPos == null) return;

		Entity victim = mc.level.getEntity(packet.entityId());
		if (victim == null || victim == player) return;

		DamageSource source = packet.getSource(mc.level);
		if (source == null || !source.is(DamageTypes.ARROW) || source.getEntity() != player) return;

		// must come from a recent bow shot (this also filters out crossbows)
		if (mc.level.getGameTime() - shotTick > MAX_FLIGHT_TICKS) return;

		double dist = shotPos.distanceTo(victim.getBoundingBox().getCenter());
		if (dist < minDistance) return;

		Component msg = Component.literal("Bugs Hit ").withStyle(ChatFormatting.GOLD)
			.append(victim.getName().copy().withStyle(ChatFormatting.WHITE))
			.append(Component.literal(" from ").withStyle(ChatFormatting.GRAY))
			.append(Component.literal(String.format("%.1f blocks", dist)).withStyle(ChatFormatting.GREEN));
		mc.gui.getChat().addMessage(msg);
	}

	private static void loadConfig() {
		Path file = FabricLoader.getInstance().getConfigDir().resolve("bowrangehit.json");
		try {
			if (Files.exists(file)) {
				Config c = GSON.fromJson(Files.readString(file), Config.class);
				if (c != null) minDistance = c.minDistance;
			} else {
				Config c = new Config();
				c.minDistance = minDistance;
				Files.writeString(file, GSON.toJson(c));
			}
		} catch (Exception ignored) {
			// keep default
		}
	}

	private static class Config {
		double minDistance = 50.0;
	}
}
