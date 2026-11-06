package com.tunombre.stormcraft;

import net.fabricmc.api.ModInitializer;
import com.tunombre.stormcraft.config.PatrolConfig;
import com.tunombre.stormcraft.entity.ModEntities;
import com.tunombre.stormcraft.item.ModItems;
import com.tunombre.stormcraft.sound.ModSounds;
import com.tunombre.stormcraft.world.PatrolSpawner;

import net.minecraft.util.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TroperCraftMod implements ModInitializer {
	public static final String MOD_ID = "tropercraft-mod";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		PatrolConfig.load();
		ModSounds.register();
		ModEntities.register();
		ModItems.register();
		PatrolSpawner.register();
		LOGGER.info("TroperCraft: las patrullas imperiales están listas.");
	}

	public static Identifier id(String path) {
		return new Identifier(MOD_ID, path);
	}
}
