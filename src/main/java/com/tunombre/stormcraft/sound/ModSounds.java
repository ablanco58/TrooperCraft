package com.tunombre.stormcraft.sound;

import com.tunombre.stormcraft.TroperCraftMod;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

public final class ModSounds {
	public static final SoundEvent BLASTER_SHOOT = sound("blaster_shoot");
	public static final SoundEvent LASER_IMPACT = sound("laser_impact");
	public static final SoundEvent TROOPER_RADIO = sound("trooper_radio");
	public static final SoundEvent TROOPER_HURT = sound("trooper_hurt");
	public static final SoundEvent TROOPER_DEATH = sound("trooper_death");

	private ModSounds() { }

	private static SoundEvent sound(String path) {
		Identifier id = TroperCraftMod.id(path);
		return Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id));
	}

	public static void register() {
		TrooperVoices.register();
	}
}
