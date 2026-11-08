package com.tunombre.stormcraft.world;

import com.tunombre.stormcraft.entity.StarDestroyerEntity;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;

/** A saved reservation keeps the single ship slot occupied even while its chunk is unloaded. */
public final class ShipLifecycleState extends PersistentState {
	public static final String SAVE_KEY = "tropercraft_ship_lifecycle";
	private UUID activeShip;
	private long blockedUntil;
	private long lastAppearanceNight = Long.MIN_VALUE;
	private long returnAt = -1;
	private UUID returningPlayer;
	private final Map<UUID, Integer> trooperKills = new HashMap<>();

	public static ShipLifecycleState get(ServerWorld world) {
		ServerWorld overworld = world.getServer().getOverworld();
		return overworld.getPersistentStateManager().getOrCreate(ShipLifecycleState::fromNbt,
				ShipLifecycleState::new, SAVE_KEY);
	}

	public static long nightStart(long time) { return Math.floorDiv(time-13000, 24000)*24000+13000; }
	public static long nextNight(long time) { return nightStart(time)+24000; }
	public static boolean isNight(long time) { long phase = Math.floorMod(time, 24000); return phase >= 13000 && phase < 23000; }
	public UUID getActiveShip() { return activeShip; }
	public long getBlockedUntil() { return blockedUntil; }
	public UUID getReturningPlayer() { return returningPlayer; }
	public boolean canSpawn(long time) { return activeShip == null && time >= blockedUntil; }
	public boolean canUse(UUID ship, long time) { return time >= blockedUntil && (activeShip == null || activeShip.equals(ship)); }

	public boolean claim(UUID ship, long time) {
		if (ship.equals(activeShip)) return true; // A departing ship retains its reservation during the animation.
		if (!canSpawn(time)) return false;
		activeShip = ship;
		markDirty();
		return true;
	}

	public void release(UUID ship) {
		if (ship.equals(activeShip)) { activeShip = null; markDirty(); }
	}

	public boolean transfer(UUID oldShip, UUID replacement) {
		if (activeShip != null && !activeShip.equals(oldShip)) return false;
		activeShip = replacement;
		markDirty();
		return true;
	}

	public void recordAppearance(long time, UUID player) {
		lastAppearanceNight = nightStart(time);
		returnAt = -1;
		returningPlayer = player;
		if (player != null) trooperKills.remove(player);
		markDirty();
	}

	public void deferToNextNight(long time, UUID player) {
		blockedUntil = Math.max(blockedUntil, nextNight(time));
		returnAt = blockedUntil;
		returningPlayer = player;
		markDirty();
	}

	public void destroyed(UUID ship, long time) {
		deferToNextNight(time, returningPlayer);
		release(ship);
	}

	public boolean shouldAppearTonight(long time) {
		if (!canSpawn(time) || !isNight(time)) return false;
		return returnAt >= 0 && time >= returnAt
				|| StarDestroyerSpawner.isEncounterNight(time) && lastAppearanceNight < nightStart(time);
	}

	public int recordTrooperKill(UUID player) {
		int count = Math.min(6, trooperKills.getOrDefault(player, 0)+1);
		trooperKills.put(player, count);
		markDirty();
		return count;
	}

	public boolean isProvokedBy(UUID player) { return trooperKills.getOrDefault(player, 0) > 5; }

	public static ShipLifecycleState fromNbt(NbtCompound nbt) {
		ShipLifecycleState state = new ShipLifecycleState();
		state.activeShip = nbt.containsUuid("ActiveShip") ? nbt.getUuid("ActiveShip") : null;
		state.blockedUntil = nbt.getLong("BlockedUntil");
		state.lastAppearanceNight = nbt.contains("LastNight") ? nbt.getLong("LastNight") : Long.MIN_VALUE;
		state.returnAt = nbt.contains("ReturnAt") ? nbt.getLong("ReturnAt") : -1;
		state.returningPlayer = nbt.containsUuid("ReturningPlayer") ? nbt.getUuid("ReturningPlayer") : null;
		NbtList kills = nbt.getList("TrooperKills", NbtElement.COMPOUND_TYPE);
		for (int i = 0; i < kills.size(); i++) {
			NbtCompound tag = kills.getCompound(i);
			if (tag.containsUuid("Player")) state.trooperKills.put(tag.getUuid("Player"), tag.getInt("Count"));
		}
		return state;
	}

	@Override
	public NbtCompound writeNbt(NbtCompound nbt) {
		if (activeShip != null) nbt.putUuid("ActiveShip", activeShip);
		nbt.putLong("BlockedUntil", blockedUntil);
		nbt.putLong("LastNight", lastAppearanceNight);
		nbt.putLong("ReturnAt", returnAt);
		if (returningPlayer != null) nbt.putUuid("ReturningPlayer", returningPlayer);
		NbtList kills = new NbtList();
		trooperKills.forEach((player, count) -> { NbtCompound tag = new NbtCompound(); tag.putUuid("Player", player); tag.putInt("Count", count); kills.add(tag); });
		nbt.put("TrooperKills", kills);
		return nbt;
	}
}
