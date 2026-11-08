package com.tunombre.stormcraft.client;

import com.tunombre.stormcraft.entity.StarDestroyerEntity;
import com.tunombre.stormcraft.sound.ModSounds;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.math.MathHelper;

/** One positional, seamless engine source follows each tracked ship. */
public final class StarDestroyerEngineSound extends MovingSoundInstance {
	private final StarDestroyerEntity ship;
	private final boolean alarm;

	public StarDestroyerEngineSound(StarDestroyerEntity ship) {
		super(ship.isSirenActive() ? ModSounds.STAR_DESTROYER_ENCOUNTER : ModSounds.STAR_DESTROYER_ENGINE, SoundCategory.HOSTILE, Random.create());
		alarm = ship.isSirenActive();
		this.ship = ship;
		attenuationType = AttenuationType.NONE;
		repeat = true;
		repeatDelay = 0;
		volume = 0.8F;
		pitch = 1.0F;
		x = ship.getX();
		y = ship.getY();
		z = ship.getZ();
	}

	public boolean isAlarm() { return alarm; }

	@Override
	public void tick() {
		if (alarm != ship.isSirenActive() || ship.isRemoved() || ship.getWorld() != MinecraftClient.getInstance().world) {
			setDone();
			return;
		}
		x = ship.getX();
		y = ship.getY();
		z = ship.getZ();
		// Smooth positional falloff at normal volume; no volume multiplier above one.
		var player = MinecraftClient.getInstance().player;
		if (player != null) {
			float distance = (float) ship.getPos().distanceTo(player.getPos());
			float range = alarm ? 192 : 320;
			float proximity = MathHelper.clamp((range-distance)/(range-64), 0, 1);
			float fadeIn = Math.min(1, ship.age/20.0F);
			volume = (alarm ? .8F : .55F)*proximity*fadeIn;
		}
	}
}
