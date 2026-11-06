package com.tunombre.stormcraft.world;

import com.tunombre.stormcraft.config.PatrolConfig;

/** Calendar-based escalation that stops growing after the third night. */
public final class PatrolProgression {
	private static final long DAY_TICKS = 24000L;
	private static final long NIGHT_START = 13000L;

	private PatrolProgression() { }

	public record SpawnProfile(int stage, int intervalSeconds, double chance,
			int patrolsPerCycle, int perPlayerCap, int worldCap) { }

	public static SpawnProfile forTime(long timeOfDay, PatrolConfig config) {
		long time = Math.max(0L, timeOfDay);
		long nightsStarted = time / DAY_TICKS + (time % DAY_TICKS >= NIGHT_START ? 1 : 0);
		int stage = (int) Math.max(1L, Math.min(3L, nightsStarted));
		int intervalMultiplier;
		double chanceMultiplier;
		int perPlayerCap;
		int worldCap;
		if (stage == 1) {
			intervalMultiplier = 6;
			chanceMultiplier = 0.45;
			perPlayerCap = 6;
			worldCap = 24;
		} else if (stage == 2) {
			intervalMultiplier = 3;
			chanceMultiplier = 0.75;
			perPlayerCap = 12;
			worldCap = 48;
		} else {
			intervalMultiplier = 2;
			chanceMultiplier = 1.0;
			perPlayerCap = 24;
			worldCap = 96;
		}
		// Existing configuration remains a ceiling, including in older worlds.
		return new SpawnProfile(stage, config.spawnIntervalSeconds * intervalMultiplier,
				config.spawnChance * chanceMultiplier,
				Math.min(config.patrolsPerCycle, stage == 3 ? 2 : 1),
				Math.min(config.maxTroopersPerPlayer, perPlayerCap),
				Math.min(config.maxTroopersPerWorld, worldCap));
	}
}
