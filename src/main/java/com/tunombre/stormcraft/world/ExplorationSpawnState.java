package com.tunombre.stormcraft.world;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;

/** Coalesces newly visited chunks into a limited number of extra spawn searches. */
public final class ExplorationSpawnState extends PersistentState {
	public static final int COOLDOWN_TICKS = 100;
	public static final int RETRY_WINDOW_TICKS = 300;
	private final Set<Long> visitedChunks = new HashSet<>();
	private final Map<UUID, PlayerProgress> players = new HashMap<>();

	private static final class PlayerProgress {
		long lastChunk;
		long nextAttemptTick;
		long pendingUntilTick = -1;

		PlayerProgress(long chunk, long time) {
			lastChunk = chunk;
			nextAttemptTick = time + COOLDOWN_TICKS;
		}
	}

	public static ExplorationSpawnState get(ServerWorld world) {
		return world.getPersistentStateManager().getOrCreate(ExplorationSpawnState::fromNbt,
				ExplorationSpawnState::new, "tropercraft_exploration");
	}

	public void observe(UUID playerId, long chunk, long time) {
		PlayerProgress progress = players.get(playerId);
		if (progress == null) {
			players.put(playerId, new PlayerProgress(chunk, time));
			visit(chunk);
			return; // Joining/loading the initial view is handled by the first encounter.
		}
		if (progress.lastChunk == chunk) return;
		progress.lastChunk = chunk;
		if (visit(chunk)) progress.pendingUntilTick = time + RETRY_WINDOW_TICKS;
	}

	private boolean visit(long chunk) {
		if (!visitedChunks.add(chunk)) return false;
		markDirty();
		return true;
	}

	public boolean canAttempt(UUID playerId, long time) {
		PlayerProgress progress = players.get(playerId);
		return progress != null && time >= progress.nextAttemptTick && time <= progress.pendingUntilTick;
	}

	/** A failed terrain search can retry; an actual probability roll failure is final. */
	public void finishAttempt(UUID playerId, long time, boolean completed) {
		PlayerProgress progress = players.get(playerId);
		if (progress == null) return;
		progress.nextAttemptTick = time + COOLDOWN_TICKS;
		if (completed) progress.pendingUntilTick = -1;
	}

	public void retainPlayers(Collection<UUID> activePlayers) {
		players.keySet().retainAll(activePlayers);
	}

	public static ExplorationSpawnState fromNbt(NbtCompound nbt) {
		ExplorationSpawnState state = new ExplorationSpawnState();
		for (long chunk : nbt.getLongArray("VisitedChunks")) state.visitedChunks.add(chunk);
		return state;
	}

	@Override
	public NbtCompound writeNbt(NbtCompound nbt) {
		nbt.putLongArray("VisitedChunks", visitedChunks.stream().mapToLong(Long::longValue).toArray());
		return nbt;
	}
}
