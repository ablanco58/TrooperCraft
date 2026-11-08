package com.tunombre.stormcraft.world;

import com.tunombre.stormcraft.config.PatrolConfig;

/** Calendar-based escalation that stops growing after the fifth night. */
public final class PatrolProgression {
	private static final long DAY_TICKS = 24000L;
	private static final long NIGHT_START = 13000L;
	private static final double[] CHANCE_MULTIPLIERS = {0.40, 0.50, 0.60, 0.70, 0.80};
	private static final int[] PER_PLAYER_CAPS = {6, 10, 14, 20, 24};

	private PatrolProgression() { }

	public record SpawnProfile(int stage, int intervalSeconds, double chance,
			int patrolsPerCycle, int perPlayerCap, int worldCap) { }

	public static SpawnProfile forTime(long timeOfDay, PatrolConfig config) {
		long time = Math.max(0L, timeOfDay);
		long nightsStarted = time / DAY_TICKS + (time % DAY_TICKS >= NIGHT_START ? 1 : 0);
		int stage = (int) Math.max(1L, Math.min(5L, nightsStarted));
		int index = stage - 1;
		int intervalSeconds = Math.max(5, config.spawnIntervalSeconds * 3 / 2);
		int perPlayerCap = PER_PLAYER_CAPS[index];
		int worldCap = perPlayerCap * 4;
		// Existing configuration remains a ceiling, including in older worlds.
		return new SpawnProfile(stage, intervalSeconds,
				config.spawnChance * CHANCE_MULTIPLIERS[index],
				Math.min(config.patrolsPerCycle, stage >= 3 ? 2 : 1),
				Math.min(config.maxTroopersPerPlayer, perPlayerCap),
				Math.min(config.maxTroopersPerWorld, worldCap));
	}
}
