package com.tunombre.stormcraft.world;

import com.tunombre.stormcraft.config.PatrolConfig;
import com.tunombre.stormcraft.entity.ModEntities;
import com.tunombre.stormcraft.entity.StarDestroyerEntity;
import com.tunombre.stormcraft.entity.StormtrooperEntity;
import com.tunombre.stormcraft.entity.LaserBoltEntity;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

public final class StarDestroyerSpawner {
	private StarDestroyerSpawner() { }

	public static void register() {
		ServerTickEvents.END_WORLD_TICK.register(StarDestroyerSpawner::tick);
		ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
			if (entity instanceof StarDestroyerEntity ship && (ShipPursuitState.get(world).isRetired(ship.getUuid())
					|| !ShipLifecycleState.get(world).claim(ship.getUuid(), world.getTimeOfDay()))) ship.discard();
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (!(entity.getWorld() instanceof ServerWorld world)) return;
			if (entity instanceof StormtrooperEntity && source.getAttacker() instanceof ServerPlayerEntity player) {
				onTrooperKilled(world, player);
			} else if (entity instanceof ServerPlayerEntity player) {
				ImperialHostilityState.get(world).clear(player.getUuid());
				var active = ShipLifecycleState.get(world).getActiveShip();
				var ship = active == null ? null : world.getEntity(active);
				boolean killedByShip = source.getAttacker() instanceof StarDestroyerEntity
						|| source.getSource() instanceof LaserBoltEntity laser && laser.isShipShot();
				if (ship instanceof StarDestroyerEntity destroyer && destroyer.getDepartureTicks() == 0
						&& (killedByShip || player.getUuid().equals(destroyer.getTargetPlayerId()))) {
					if (killedByShip) destroyer.setTargetPlayer(player);
					destroyer.beginDeparture();
				}
			}
		});
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				dispatcher.register(CommandManager.literal("stardestroyer")
						.requires(source -> source.hasPermissionLevel(2))
						.executes(context -> {
							var source = context.getSource();
							boolean spawned = source.getEntity() instanceof ServerPlayerEntity player
									? spawnEncounter(source.getWorld(), player)
									: spawnShip(source.getWorld(), source.getPosition(), source.getRotation().y);
							if (spawned) source.sendFeedback(() -> Text.translatable("commands.tropercraft-mod.ship.success"), true);
							else source.sendError(Text.translatable("commands.tropercraft-mod.ship.failed"));
							return spawned ? 1 : 0;
						})));
	}

	public static boolean isEncounterNight(long timeOfDay) {
		return timeOfDay >= 61000 && timeOfDay % 24000 >= 13000 && timeOfDay % 24000 < 23000;
	}

	public static void onTrooperKilled(ServerWorld world, ServerPlayerEntity player) {
		ShipLifecycleState state = ShipLifecycleState.get(world);
		if (state.recordTrooperKill(player.getUuid()) > 5 && state.canSpawn(world.getTimeOfDay())) spawnEncounter(world, player);
	}

	public static void tick(ServerWorld world) {
		if (!world.getRegistryKey().equals(World.OVERWORLD)) return;
		ShipPursuitState.get(world).tick(world, world.getTime());
		if (world.getTime() % 20 != 0 || !PatrolSpawner.canSpawnNaturally(world) || PatrolConfig.get().spawnChance <= 0) return;
		ShipLifecycleState state = ShipLifecycleState.get(world);
		if (!state.canSpawn(world.getTimeOfDay())) return;
		var players = world.getPlayers(player -> player.isAlive() && !player.isSpectator() && !player.isCreative());
		for (ServerPlayerEntity player : players) {
			if (state.isProvokedBy(player.getUuid()) && spawnEncounter(world, player)) return;
		}
		if (!state.shouldAppearTonight(world.getTimeOfDay()) || players.isEmpty()) return;
		ServerPlayerEntity player = players.stream().filter(candidate -> candidate.getUuid().equals(state.getReturningPlayer()))
				.findFirst().orElse(players.get(world.random.nextInt(players.size())));
		if (player.age >= 200) spawnEncounter(world, player);
	}

	public static boolean spawnEncounter(ServerWorld world, ServerPlayerEntity player) {
		return spawnShip(world, player.getPos(), player.getYaw(), player);
	}

	public static boolean spawnShip(ServerWorld world, Vec3d center, float yaw) {
		return spawnShip(world, center, yaw, null);
	}

	public static void announceEncounter(ServerPlayerEntity player) {
		player.sendMessage(Text.translatable("message.tropercraft-mod.destroyer_hunting").formatted(Formatting.RED), false);
	}

	public static boolean arriveAtPlayer(ServerWorld world, StarDestroyerEntity ship, ServerPlayerEntity player) {
		if (!ShipLifecycleState.get(world).canUse(ship.getUuid(), world.getTimeOfDay())) return false;
		Vec3d ahead = encounterPosition(world, player.getPos(), player.getYaw(), true);
		if (ahead == null) return false;
		float facing = player.getYaw()+180;
		ship.refreshPositionAndAngles(ahead.x, StarDestroyerEntity.getCruiseAltitude(world, ahead, facing), ahead.z, facing, 0);
		ship.setTargetPlayer(player);
		ship.resetFlightLifetime();
		ship.beginArrival();
		return true;
	}

	private static Vec3d encounterPosition(ServerWorld world, Vec3d center, float yaw, boolean ahead) {
		if (!world.getRegistryKey().equals(World.OVERWORLD)) return null;
		for (int distance : ahead ? new int[]{120, 104, 88} : new int[]{0}) {
			Vec3d position = center.add(Vec3d.fromPolar(0, yaw).multiply(distance));
			BlockPos block = BlockPos.ofFloored(position);
			if (world.isChunkLoaded(block) && world.getWorldBorder().contains(block)) return position;
		}
		return null;
	}

	private static boolean spawnShip(ServerWorld world, Vec3d center, float yaw, ServerPlayerEntity target) {
		if (!ShipLifecycleState.get(world).canSpawn(world.getTimeOfDay())) return false;
		StarDestroyerEntity ship = ModEntities.STAR_DESTROYER.create(world);
		if (ship == null) return false;
		if (target != null) {
			if (!arriveAtPlayer(world, ship, target)) return false;
		} else {
			Vec3d position = encounterPosition(world, center, yaw, false);
			if (position == null) return false;
			float facing = yaw+180;
			ship.refreshPositionAndAngles(position.x, StarDestroyerEntity.getCruiseAltitude(world, position, facing), position.z, facing, 0);
			ship.beginArrival();
		}
		boolean spawned = world.spawnEntity(ship);
		if (spawned && !ship.isRemoved()) {
			ShipLifecycleState.get(world).recordAppearance(world.getTimeOfDay(), target == null ? null : target.getUuid());
			if (target != null) announceEncounter(target);
		}
		spawned &= !ship.isRemoved();
		return spawned;
	}

	/** Initial deployment ignores probability/population caps; later reinforcements are bounded. */
	public static int deployGroundPatrol(ServerWorld world, StarDestroyerEntity ship, boolean firstDeployment) {
		if (!PatrolSpawner.canSpawnNaturally(world) || PatrolConfig.get().spawnChance <= 0
				|| !world.isChunkLoaded(ship.getBlockPos())) return 0;
		int surface = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, ship.getBlockX(), ship.getBlockZ());
		BlockPos focus = new BlockPos(ship.getBlockX(), surface, ship.getBlockZ());
		int size = PatrolSpawner.MIN_PATROL_SIZE;
		if (!firstDeployment) {
			int cap = Math.min(12, PatrolProgression.forTime(world.getTimeOfDay(), PatrolConfig.get()).perPlayerCap());
			int nearby = world.getEntitiesByClass(StormtrooperEntity.class, new Box(focus).expand(72, 32, 72),
					StormtrooperEntity::isAlive).size();
			int loaded = 0;
			for (Entity entity : world.iterateEntities()) {
				if (entity instanceof StormtrooperEntity && entity.isAlive()) loaded++;
			}
			int available = Math.min(cap - nearby, PatrolConfig.get().maxTroopersPerWorld - loaded);
			if (available < size) return 0;
			size += world.random.nextInt(Math.min(PatrolSpawner.MAX_PATROL_SIZE, available) - size + 1);
		}
		// Never roll a spawn chance. Try the center first, then ground under the hull.
		for (int attempt = 0; attempt < 49; attempt++) {
			double angle = Math.PI * 2 * (attempt % 8) / 8;
			int radius = attempt == 0 ? 0 : 24 + ((attempt - 1) / 8) * 6;
			int x = focus.getX() + MathHelper.floor(Math.cos(angle) * radius);
			int z = focus.getZ() + MathHelper.floor(Math.sin(angle) * radius);
			BlockPos candidate = PatrolSpawner.findCandidate(world, focus, x, z, false);
			if (candidate == null) continue;
			int spawned = PatrolSpawner.spawnPatrol(world, candidate, size, true);
			if (spawned > 0) return spawned;
		}
		return 0;
	}
}
