package com.tunombre.stormcraft.world;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;

/** An exposed disguise stays exposed through equipment changes, reconnects and ship replacements. */
public final class ImperialHostilityState extends PersistentState {
	public static final String SAVE_KEY = "tropercraft_identified_enemies";
	private final Map<UUID, Integer> identified = new HashMap<>();

	public static ImperialHostilityState get(ServerWorld world) {
		return world.getServer().getOverworld().getPersistentStateManager().getOrCreate(
				ImperialHostilityState::fromNbt, ImperialHostilityState::new, SAVE_KEY);
	}

	public void identify(PlayerEntity player) {
		identified.put(player.getUuid(), player instanceof ServerPlayerEntity server ? ShipPursuitState.deaths(server) : 0);
		markDirty();
	}

	public boolean isIdentified(PlayerEntity player) {
		Integer deaths = identified.get(player.getUuid());
		if (deaths == null) return false;
		if (!player.isAlive() || player instanceof ServerPlayerEntity server && ShipPursuitState.deaths(server) != deaths) {
			clear(player.getUuid());
			return false;
		}
		return true;
	}

	public void clear(UUID player) { if (identified.remove(player) != null) markDirty(); }

	public static ImperialHostilityState fromNbt(NbtCompound nbt) {
		ImperialHostilityState state = new ImperialHostilityState();
		NbtList list = nbt.getList("Enemies", NbtElement.COMPOUND_TYPE);
		for (int i = 0; i < list.size(); i++) {
			NbtCompound entry = list.getCompound(i);
			if (entry.containsUuid("Player")) state.identified.put(entry.getUuid("Player"), entry.getInt("Deaths"));
		}
		return state;
	}

	@Override
	public NbtCompound writeNbt(NbtCompound nbt) {
		NbtList list = new NbtList();
		identified.forEach((player, deaths) -> {
			NbtCompound entry = new NbtCompound(); entry.putUuid("Player", player); entry.putInt("Deaths", deaths); list.add(entry);
		});
		nbt.put("Enemies", list);
		return nbt;
	}
}
