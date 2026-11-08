package com.tunombre.stormcraft.world;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;

/** One introductory encounter per player/world, including across reconnects. */
public final class FirstEncounterState extends PersistentState {
	private final Set<UUID> introducedPlayers = new HashSet<>();

	public static FirstEncounterState get(ServerWorld world) {
		return world.getPersistentStateManager().getOrCreate(FirstEncounterState::fromNbt,
				FirstEncounterState::new, "tropercraft_first_encounters");
	}

	public static FirstEncounterState getShips(ServerWorld world) {
		return world.getPersistentStateManager().getOrCreate(FirstEncounterState::fromNbt,
				FirstEncounterState::new, "tropercraft_ship_encounters");
	}

	public boolean hasEncountered(UUID playerId) {
		return introducedPlayers.contains(playerId);
	}

	public void markEncountered(UUID playerId) {
		if (introducedPlayers.add(playerId)) markDirty();
	}

	public static FirstEncounterState fromNbt(NbtCompound nbt) {
		FirstEncounterState state = new FirstEncounterState();
		NbtList players = nbt.getList("Players", NbtElement.STRING_TYPE);
		for (int index = 0; index < players.size(); index++) {
			try {
				state.introducedPlayers.add(UUID.fromString(players.getString(index)));
			} catch (IllegalArgumentException ignored) {
				// An invalid saved UUID must not disable introductions for everyone else.
			}
		}
		return state;
	}

	@Override
	public NbtCompound writeNbt(NbtCompound nbt) {
		NbtList players = new NbtList();
		introducedPlayers.forEach(id -> players.add(NbtString.of(id.toString())));
		nbt.put("Players", players);
		return nbt;
	}
}
