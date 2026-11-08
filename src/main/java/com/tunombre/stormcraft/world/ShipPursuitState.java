package com.tunombre.stormcraft.world;

import com.tunombre.stormcraft.entity.ModEntities;
import com.tunombre.stormcraft.entity.StarDestroyerEntity;
import com.tunombre.stormcraft.item.TrooperHeadItem;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.stat.Stats;
import net.minecraft.world.Difficulty;
import net.minecraft.world.PersistentState;

/** World-clock searches continue even after the patrolling ship's chunk unloads. */
public final class ShipPursuitState extends PersistentState {
	public static final int SEARCH_TICKS = 30*20;
	private record Search(UUID player, UUID ship, long due, int deaths, NbtCompound snapshot) { }
	private final Map<UUID, Search> searches = new HashMap<>();
	private final Set<UUID> retiredShips = new HashSet<>();
	private final Set<UUID> abandonedShips = new HashSet<>();

	public static ShipPursuitState get(ServerWorld world) {
		return world.getPersistentStateManager().getOrCreate(ShipPursuitState::fromNbt,
				ShipPursuitState::new, "tropercraft_ship_pursuits");
	}

	public static int deaths(ServerPlayerEntity player) {
		return player.getStatHandler().getStat(Stats.CUSTOM, Stats.DEATHS);
	}

	public void schedule(ServerWorld world, StarDestroyerEntity ship, ServerPlayerEntity player) {
		searches.put(ship.getUuid(), new Search(player.getUuid(), ship.getUuid(), world.getTime()+SEARCH_TICKS,
				deaths(player), ship.writeNbt(new NbtCompound())));
		markDirty();
	}

	public void retire(UUID ship) { cancel(ship); retiredShips.add(ship); markDirty(); }
	public boolean isPending(UUID ship) { return searches.containsKey(ship); }
	public boolean isRetired(UUID ship) { return retiredShips.contains(ship); }
	public boolean isAbandoned(UUID ship) { return abandonedShips.contains(ship); }

	public void cancel(UUID ship) {
		if (searches.remove(ship) != null) markDirty();
	}

	public void updateSnapshot(StarDestroyerEntity ship) {
		Search old = searches.get(ship.getUuid());
		if (old != null) {
			searches.put(old.ship, new Search(old.player, old.ship, old.due, old.deaths, ship.writeNbt(new NbtCompound())));
			markDirty();
		}
	}

	public void tick(ServerWorld world, long now) {
		for (Search search : new ArrayList<>(searches.values())) {
			ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(search.player);
			var entity = world.getEntity(search.ship);
			StarDestroyerEntity ship = entity instanceof StarDestroyerEntity found ? found : null;
			if (player == null) continue; // No forced chunks or appearances while offline.
			if (!player.isAlive() || deaths(player) != search.deaths || player.getWorld() != world
					|| player.isCreative() || player.isSpectator() || world.getDifficulty() == Difficulty.PEACEFUL) {
				cancel(search.ship);
				abandonedShips.add(search.ship);
				if (ship != null) ship.beginDeparture();
				else {
					ShipLifecycleState.get(world).deferToNextNight(world.getTimeOfDay(), search.player);
					ShipLifecycleState.get(world).release(search.ship);
					retiredShips.add(search.ship);
				}
				markDirty();
				continue;
			}
			if (TrooperHeadItem.isDisguised(player)) {
				cancel(search.ship);
				if (ship == null) {
					retire(search.ship);
					ShipLifecycleState.get(world).release(search.ship);
				}
				continue;
			}
			if (now < search.due) continue;
			if (ship != null && !ship.isAlive()) {
				cancel(search.ship);
				continue;
			}
			boolean replacement = ship == null;
			if (replacement) {
				ship = ModEntities.STAR_DESTROYER.create(world);
				if (ship == null) continue;
				UUID freshId = ship.getUuid();
				ship.readNbt(search.snapshot.copy());
				ship.setUuid(freshId);
			}
			ShipLifecycleState lifecycle = ShipLifecycleState.get(world);
			if (replacement && !lifecycle.transfer(search.ship, ship.getUuid())) continue;
			if (!StarDestroyerSpawner.arriveAtPlayer(world, ship, player) || replacement && !world.spawnEntity(ship)) {
				if (replacement) lifecycle.transfer(ship.getUuid(), search.ship);
				continue;
			}
			cancel(search.ship);
			StarDestroyerSpawner.announceEncounter(player);
			if (replacement) retiredShips.add(search.ship); // The old saved hull discards itself on load.
			markDirty();
		}
	}

	public static ShipPursuitState fromNbt(NbtCompound nbt) {
		ShipPursuitState state = new ShipPursuitState();
		NbtList list = nbt.getList("Searches", NbtElement.COMPOUND_TYPE);
		for (int i = 0; i < list.size(); i++) {
			NbtCompound saved = list.getCompound(i);
			if (!saved.containsUuid("Player") || !saved.containsUuid("Ship")) continue;
			Search search = new Search(saved.getUuid("Player"), saved.getUuid("Ship"), saved.getLong("Due"),
					saved.getInt("Deaths"), saved.getCompound("Snapshot").copy());
			state.searches.put(search.ship, search);
		}
		readIds(nbt, "Retired", state.retiredShips);
		readIds(nbt, "Abandoned", state.abandonedShips);
		return state;
	}

	private static void readIds(NbtCompound nbt, String key, Set<UUID> ids) {
		NbtList list = nbt.getList(key, NbtElement.COMPOUND_TYPE);
		for (int i = 0; i < list.size(); i++) if (list.getCompound(i).containsUuid("Id")) ids.add(list.getCompound(i).getUuid("Id"));
	}

	private static NbtList writeIds(Set<UUID> ids) {
		NbtList list = new NbtList();
		for (UUID id : ids) { NbtCompound tag = new NbtCompound(); tag.putUuid("Id", id); list.add(tag); }
		return list;
	}

	@Override
	public NbtCompound writeNbt(NbtCompound nbt) {
		NbtList list = new NbtList();
		for (Search search : searches.values()) {
			NbtCompound tag = new NbtCompound();
			tag.putUuid("Player", search.player); tag.putUuid("Ship", search.ship);
			tag.putLong("Due", search.due); tag.putInt("Deaths", search.deaths);
			tag.put("Snapshot", search.snapshot.copy()); list.add(tag);
		}
		nbt.put("Searches", list);
		nbt.put("Retired", writeIds(retiredShips));
		nbt.put("Abandoned", writeIds(abandonedShips));
		return nbt;
	}
}
