package com.tunombre.stormcraft.sound;

import com.tunombre.stormcraft.TroperCraftMod;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

public final class ModSounds {
	// Keep these ranges in sync with attenuation_distance in sounds.json/importers.
	public static final int VOICE_RANGE = 48;
	public static final int BLASTER_RANGE = 64;
	public static final SoundEvent BLASTER_SHOOT = sound("blaster_shoot", BLASTER_RANGE);
	public static final SoundEvent STAR_DESTROYER_HURT = sound("star_destroyer_hurt", 160);
	public static final SoundEvent STAR_DESTROYER_ENCOUNTER = sound("star_destroyer_encounter", 192);
	public static final SoundEvent STAR_DESTROYER_ENGINE = sound("star_destroyer_engine", 320);
	public static final SoundEvent LASER_IMPACT = sound("laser_impact");
	public static final SoundEvent TROOPER_RADIO = sound("trooper_radio");
	public static final SoundEvent TROOPER_HURT = sound("trooper_hurt");
	public static final SoundEvent TROOPER_DEATH = sound("trooper_death");

	private ModSounds() { }

	private static SoundEvent sound(String path) {
		Identifier id = TroperCraftMod.id(path);
		return Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id));
	}

	private static SoundEvent sound(String path, float range) {
		Identifier id = TroperCraftMod.id(path);
		return Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id, range));
	}

	public static void register() {
		TrooperVoices.register();
	}
}
