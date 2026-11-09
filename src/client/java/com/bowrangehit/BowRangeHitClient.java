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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
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
	private static int lastVictimId = -1;
	private static long lastShownTick = -1000;

	// player hit by our arrow, waiting to see if it was a kill
	private static int pendingVictimId = -1;
	private static double pendingDist = 0;
	private static long pendingTick = -1000;

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

		// a player we hit with an arrow died: always announce it, any distance
		if (pendingVictimId != -1) {
			long now = mc.level.getGameTime();
			Entity e = mc.level.getEntity(pendingVictimId);
			if (now - pendingTick > 10 || !(e instanceof LivingEntity)) {
				pendingVictimId = -1;
			} else if (((LivingEntity) e).isDeadOrDying()) {
				show(mc, player, e, pendingDist);
				pendingVictimId = -1;
			}
		}
	}

	/** Called from the mixin for every damage event the client receives. */
	public static void onDamage(ClientboundDamageEventPacket packet) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null || mc.level == null) return;

		Entity victim = mc.level.getEntity(packet.entityId());
		if (victim == null || victim == player) return;

		DamageSource source = packet.getSource(mc.level);
		if (source == null || !source.is(DamageTypes.ARROW) || source.getEntity() != player) return;

		long now = mc.level.getGameTime();
		boolean recentShot = shotPos != null && now - shotTick <= MAX_FLIGHT_TICKS;
		Vec3 origin = recentShot ? shotPos : player.getEyePosition();
		double dist = origin.distanceTo(victim.getBoundingBox().getCenter());

		// players: remember the hit so a kill gets announced no matter the distance
		if (victim instanceof Player) {
			pendingVictimId = victim.getId();
			pendingDist = dist;
			pendingTick = now;
		}

		// normal far hits: bow shots only (this also filters out crossbows)
		if (!recentShot || dist < minDistance) return;
		show(mc, player, victim, dist);
	}

	private static void show(Minecraft mc, LocalPlayer player, Entity victim, double dist) {
		// the server can send more than one damage packet per hit, only show it once
		long now = mc.level.getGameTime();
		if (victim.getId() == lastVictimId && now - lastShownTick < 10) return;
		lastVictimId = victim.getId();
		lastShownTick = now;

		Component msg = player.getName().copy().withStyle(ChatFormatting.GREEN)
			.append(Component.literal(" sniped ").withStyle(ChatFormatting.GRAY))
			.append(victim.getName().copy().withStyle(ChatFormatting.RED))
			.append(Component.literal(String.format(" (%,d m)", Math.round(dist))).withStyle(ChatFormatting.GRAY));
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
