package com.tunombre.stormcraft.sound;

import com.tunombre.stormcraft.config.PatrolConfig;
import java.util.function.BooleanSupplier;
import net.minecraft.util.math.random.Random;
import org.jetbrains.annotations.Nullable;

/** Per-entity state: changing combat mode never interrupts an unfinished sentence. */
public final class TrooperSpeech {
	private int cooldownTicks;
	private int speakingTicks;
	private boolean wasInCombat;
	private String previousId = "";

	public TrooperSpeech(Random random) {
		cooldownTicks = 40 + random.nextInt(121);
	}

	public boolean isSpeaking() {
		return speakingTicks > 0;
	}

	@Nullable
	public TrooperVoices.VoiceLine tick(boolean combat, Random random, BooleanSupplier hasVoiceSlot) {
		if (speakingTicks > 0) speakingTicks--;
		if (cooldownTicks > 0) cooldownTicks--;
		if (combat != wasInCombat) {
			wasInCombat = combat;
			cooldownTicks = combat
					? Math.min(cooldownTicks, speakingTicks + random.nextInt(21))
					: Math.max(cooldownTicks, speakingTicks + pauseTicks(false, random));
		}
		if (!PatrolConfig.get().enableVoices || PatrolConfig.get().voiceVolume <= 0
				|| speakingTicks > 0 || cooldownTicks > 0) return null;
		if (!hasVoiceSlot.getAsBoolean()) {
			cooldownTicks = 10 + random.nextInt(21);
			return null;
		}
		TrooperVoices.VoiceLine line = TrooperVoices.choose(combat, previousId, random);
		previousId = line.id();
		// Keep a small guard for Vorbis padding and client-side playback scheduling.
		speakingTicks = line.durationTicks() + 4;
		cooldownTicks = speakingTicks + pauseTicks(combat, random);
		return line;
	}

	private static int pauseTicks(boolean combat, Random random) {
		PatrolConfig config = PatrolConfig.get();
		int minimum = combat ? config.minCombatVoicePauseSeconds : config.minPatrolVoicePauseSeconds;
		int maximum = combat ? config.maxCombatVoicePauseSeconds : config.maxPatrolVoicePauseSeconds;
		return (minimum + random.nextInt(maximum - minimum + 1)) * 20;
	}
}
