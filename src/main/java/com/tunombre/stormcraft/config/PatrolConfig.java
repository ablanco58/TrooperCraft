package com.tunombre.stormcraft.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.tunombre.stormcraft.TroperCraftMod;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/** Server settings; group sizes and health deliberately stay fixed. */
public final class PatrolConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static PatrolConfig instance = new PatrolConfig();

	public int spawnIntervalSeconds = 10;
	public double spawnChance = 1.0;
	public int patrolsPerCycle = 2;
	public int maxTroopersPerPlayer = 40;
	public int maxTroopersPerWorld = 160;
	public float laserDamage = 3.0F;
	public float playerLaserDamage = 10.0F;
	public int shotIntervalTicks = 30;
	public boolean enableVoices = true;
	public float voiceVolume = 0.85F;
	public int minPatrolVoicePauseSeconds = 8;
	public int maxPatrolVoicePauseSeconds = 18;
	public int minCombatVoicePauseSeconds = 1;
	public int maxCombatVoicePauseSeconds = 3;

	public static PatrolConfig get() {
		return instance;
	}

	public static void load() {
		Path path = FabricLoader.getInstance().getConfigDir().resolve("tropercraft.json");
		try {
			if (Files.exists(path)) {
				try (Reader reader = Files.newBufferedReader(path)) {
					PatrolConfig loaded = GSON.fromJson(reader, PatrolConfig.class);
					if (loaded == null) throw new IllegalArgumentException("Configuración vacía");
					loaded.validate();
					instance = loaded;
				}
			} else {
				Files.createDirectories(path.getParent());
				Files.writeString(path, GSON.toJson(instance) + System.lineSeparator());
			}
		} catch (IOException | RuntimeException exception) {
			TroperCraftMod.LOGGER.error("No se pudo cargar {}. Se usarán los valores predeterminados.", path, exception);
			instance = new PatrolConfig();
		}
	}

	private void validate() {
		if (spawnIntervalSeconds < 5 || spawnIntervalSeconds > 3600
				|| !Double.isFinite(spawnChance) || spawnChance < 0 || spawnChance > 1
				|| patrolsPerCycle < 1 || patrolsPerCycle > 4
				|| maxTroopersPerPlayer < 5 || maxTroopersPerPlayer > 200
				|| maxTroopersPerWorld < 5 || maxTroopersPerWorld > 1000
				|| !Float.isFinite(laserDamage) || laserDamage <= 0 || laserDamage > 100
				|| !Float.isFinite(playerLaserDamage) || playerLaserDamage <= 0 || playerLaserDamage > 100
				|| shotIntervalTicks < 10 || shotIntervalTicks > 1200
				|| !Float.isFinite(voiceVolume) || voiceVolume < 0 || voiceVolume > 1
				|| minPatrolVoicePauseSeconds < 1 || maxPatrolVoicePauseSeconds > 300
				|| maxPatrolVoicePauseSeconds < minPatrolVoicePauseSeconds
				|| minCombatVoicePauseSeconds < 1 || maxCombatVoicePauseSeconds > 120
				|| maxCombatVoicePauseSeconds < minCombatVoicePauseSeconds) {
			throw new IllegalArgumentException("Valores fuera de los límites documentados en DETALLES_DEL_MOD.txt");
		}
	}
}
