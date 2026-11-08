package com.tunombre.stormcraft.world;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.tunombre.stormcraft.config.PatrolConfig;
import com.tunombre.stormcraft.entity.ModEntities;
import com.tunombre.stormcraft.entity.StormtrooperEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameRules;
import net.minecraft.world.Heightmap;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

/** Spawn whole squads rather than letting vanilla truncate a biome spawn group. */
public final class PatrolSpawner {
	public static final int MIN_PATROL_SIZE = 3;
	public static final int MAX_PATROL_SIZE = 5;
	private static final int SEARCH_ATTEMPTS = 24;
	private static final int MIN_PLAYER_DISTANCE = 24;
	private static final int MAX_CANDIDATE_DISTANCE = 96;

	private PatrolSpawner() { }

	public static void register() {
		ServerTickEvents.END_WORLD_TICK.register(PatrolSpawner::tick);
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				dispatcher.register(CommandManager.literal("stormpatrol")
						.requires(source -> source.hasPermissionLevel(2))
						.executes(context -> spawnFromCommand(context.getSource(), 3))
						.then(CommandManager.argument("size", IntegerArgumentType.integer(3, 5))
								.executes(context -> spawnFromCommand(context.getSource(),
										IntegerArgumentType.getInteger(context, "size"))))));
	}

	private static void tick(ServerWorld world) {
		if (!canSpawnNaturally(world)) return;
		PatrolProgression.SpawnProfile profile = PatrolProgression.forTime(world.getTimeOfDay(), PatrolConfig.get());
		boolean regularCycle = world.getTime() % (profile.intervalSeconds() * 20L) == 0;
		boolean introductionCycle = world.getTime() % 40L == 0 && PatrolConfig.get().spawnChance > 0;
		boolean explorationCycle = world.getTime() % 20L == 0 && PatrolConfig.get().spawnChance > 0;
		if (!regularCycle && !introductionCycle && !explorationCycle) return;
		List<ServerPlayerEntity> players = world.getPlayers(player -> player.isAlive() && !player.isSpectator());
		if (players.isEmpty()) {
			if (explorationCycle) ExplorationSpawnState.get(world).retainPlayers(List.of());
			return;
		}
		if (introductionCycle) {
			FirstEncounterState encounters = FirstEncounterState.get(world);
			for (ServerPlayerEntity player : players) {
				if (player.age < 60 || encounters.hasEncountered(player.getUuid())) continue;
				boolean visibleTrooper = world.getEntitiesByClass(StormtrooperEntity.class,
						player.getBoundingBox().expand(48), other -> other.isAlive()
						&& player.squaredDistanceTo(other) <= 48 * 48
						&& canSeeEncounter(world, player, other.getEyePos())).size() > 0;
				if (visibleTrooper || spawnFirstEncounter(world, player) > 0) {
					encounters.markEncountered(player.getUuid());
				}
			}
		}
		ExplorationSpawnState exploration = explorationCycle ? ExplorationSpawnState.get(world) : null;
		boolean explorationDue = false;
		if (exploration != null) {
			exploration.retainPlayers(players.stream().map(ServerPlayerEntity::getUuid).toList());
			for (ServerPlayerEntity player : players) {
				exploration.observe(player.getUuid(), player.getChunkPos().toLong(), world.getTime());
				explorationDue |= exploration.canAttempt(player.getUuid(), world.getTime());
			}
		}
		if (!regularCycle && !explorationDue) return;
		int loadedTroopers = 0;
		for (Entity entity : world.iterateEntities()) {
			if (entity instanceof StormtrooperEntity && entity.isAlive()) loadedTroopers++;
		}
		int populationCap = (int) Math.min(profile.worldCap(),
				(long) profile.perPlayerCap() * players.size());
		// Rotate the first player so that a shared population limit does not always favor the same player.
		int offset = world.random.nextInt(players.size());
		for (int index = 0; index < players.size(); index++) {
			ServerPlayerEntity player = players.get((index + offset) % players.size());
			int nearby = world.getEntitiesByClass(StormtrooperEntity.class, player.getBoundingBox().expand(96),
					StormtrooperEntity::isAlive).size();
			if (exploration != null && exploration.canAttempt(player.getUuid(), world.getTime())) {
				int available = Math.min(populationCap - loadedTroopers, profile.perPlayerCap() - nearby);
				if (available >= MIN_PATROL_SIZE) {
					double chance = PatrolConfig.get().spawnChance * 0.95;
					boolean rolled = world.random.nextDouble() < chance;
					int size = MIN_PATROL_SIZE + world.random.nextInt(Math.min(MAX_PATROL_SIZE, available) - MIN_PATROL_SIZE + 1);
					int spawned = rolled ? spawnExplorationPatrol(world, player, size) : 0;
					exploration.finishAttempt(player.getUuid(), world.getTime(), !rolled || spawned > 0);
					loadedTroopers += spawned;
					nearby += spawned;
				}
			}
			if (!regularCycle) continue;
			List<MobEntity> landMobs = world.getEntitiesByClass(MobEntity.class, player.getBoundingBox().expand(64),
					mob -> !(mob instanceof StormtrooperEntity) && mob.isAlive() && mob.isOnGround()
					&& !mob.isTouchingWater() && mob.squaredDistanceTo(player) >= 28 * 28
					&& mob.squaredDistanceTo(player) <= 64 * 64);
			for (int wave = 0; wave < profile.patrolsPerCycle(); wave++) {
				int available = Math.min(populationCap - loadedTroopers, profile.perPlayerCap() - nearby);
				if (available < MIN_PATROL_SIZE) break;
				int size = MIN_PATROL_SIZE + world.random.nextInt(Math.min(MAX_PATROL_SIZE, available) - MIN_PATROL_SIZE + 1);
				if (world.random.nextDouble() >= profile.chance()) continue;
				// Give both surface and underground a dedicated search; neither requires darkness.
				// With just one search, alternate the preferred environment each cycle.
				boolean preferCave = (world.getTime() / (profile.intervalSeconds() * 20L) + wave) % 2 == 1;
				for (int attempt = 0; attempt < SEARCH_ATTEMPTS; attempt++) {
					double angle = world.random.nextDouble() * Math.PI * 2;
					double radius = 28 + world.random.nextInt(37);
					int x = MathHelper.floor(player.getX() + Math.cos(angle) * radius);
					int z = MathHelper.floor(player.getZ() + Math.sin(angle) * radius);
					boolean cave = attempt < SEARCH_ATTEMPTS / 2 ? preferCave : !preferCave;
					BlockPos candidate;
					if (!landMobs.isEmpty() && attempt < SEARCH_ATTEMPTS * 3 / 4) {
						// Spend 75% of searches near other land mobs, then fall back to the normal ring.
						MobEntity anchor = landMobs.get(world.random.nextInt(landMobs.size()));
						x = anchor.getBlockX() + world.random.nextInt(17) - 8;
						z = anchor.getBlockZ() + world.random.nextInt(17) - 8;
						candidate = findLocalFloor(world, anchor.getBlockPos(), x, z, 4);
					} else {
						candidate = findCandidate(world, player.getBlockPos(), x, z, cave);
					}
					if (candidate == null) continue;
					int spawned = spawnPatrol(world, candidate, size, true);
					if (spawned > 0) {
						loadedTroopers += spawned;
						nearby += spawned;
						break;
					}
				}
			}
		}
	}

	private static int spawnExplorationPatrol(ServerWorld world, LivingEntity player, int size) {
		boolean underground = !world.isSkyVisible(player.getBlockPos());
		for (int attempt = 0; attempt < SEARCH_ATTEMPTS * 2; attempt++) {
			// Favor the direction of travel/view before trying the full surrounding ring.
			float yaw = player.getYaw() + (attempt < SEARCH_ATTEMPTS
					? world.random.nextFloat() * 120 - 60 : world.random.nextFloat() * 360);
			Vec3d forward = Vec3d.fromPolar(0, yaw).multiply(28 + world.random.nextInt(25));
			int x = MathHelper.floor(player.getX() + forward.x);
			int z = MathHelper.floor(player.getZ() + forward.z);
			BlockPos candidate = findLocalFloor(world, player.getBlockPos(), x, z, 8);
			if (candidate == null) candidate = findCandidate(world, player.getBlockPos(), x, z, underground);
			if (candidate == null) continue;
			int count = spawnPatrol(world, candidate, size, true);
			if (count > 0) return count;
		}
		return 0;
	}

	/** No probability roll: keep retrying a visible, safe first squad until it fits. */
	public static int spawnFirstEncounter(ServerWorld world, LivingEntity player) {
		if (!canSpawnNaturally(world)) return 0;
		for (int radius : new int[] {28, 32, 36, 40}) {
			for (int offset : new int[] {0, -10, 10, -20, 20, -30, 30}) {
				Vec3d forward = Vec3d.fromPolar(0, player.getYaw() + offset).multiply(radius);
				int x = MathHelper.floor(player.getX() + forward.x);
				int z = MathHelper.floor(player.getZ() + forward.z);
				BlockPos candidate = findLocalFloor(world, player.getBlockPos(), x, z, 8);
				if (candidate == null) candidate = findCandidate(world, player.getBlockPos(), x, z, false);
				if (candidate == null || isNearPlayer(world, candidate)
						|| !canSeeEncounter(world, player, Vec3d.ofBottomCenter(candidate).add(0, 1, 0))) continue;
				int count = spawnPatrol(world, candidate, MIN_PATROL_SIZE, true);
				if (count > 0) return count;
			}
		}
		return 0;
	}

	private static boolean canSeeEncounter(ServerWorld world, LivingEntity player, Vec3d target) {
		Vec3d direction = target.subtract(player.getEyePos());
		if (direction.lengthSquared() < 1.0E-6
				|| Vec3d.fromPolar(player.getPitch(), player.getYaw()).dotProduct(direction.normalize()) < 0.85) return false;
		return world.raycast(new RaycastContext(player.getEyePos(), target, RaycastContext.ShapeType.COLLIDER,
				RaycastContext.FluidHandling.NONE, player)).getType() == HitResult.Type.MISS;
	}

	private static BlockPos findLocalFloor(ServerWorld world, BlockPos focus, int x, int z, int verticalRange) {
		if (!world.isChunkLoaded(new BlockPos(x, focus.getY(), z))) return null;
		for (int distance = 0; distance <= verticalRange; distance++) {
			BlockPos above = new BlockPos(x, focus.getY() + distance, z);
			if (isSafePosition(world, above)) return above;
			if (distance > 0) {
				BlockPos below = new BlockPos(x, focus.getY() - distance, z);
				if (isSafePosition(world, below)) return below;
			}
		}
		return null;
	}

	public static boolean canSpawnNaturally(ServerWorld world) {
		return world.getRegistryKey().equals(World.OVERWORLD)
				&& world.getDifficulty() != Difficulty.PEACEFUL
				&& world.getServer().isMonsterSpawningEnabled()
				&& world.getGameRules().getBoolean(GameRules.DO_MOB_SPAWNING);
	}

	public static BlockPos findCandidate(ServerWorld world, BlockPos focus, int x, int z, boolean cave) {
		if (!world.isChunkLoaded(new BlockPos(x, focus.getY(), z))) return null;
		double dx = x - focus.getX();
		double dz = z - focus.getZ();
		double verticalSquared = MAX_CANDIDATE_DISTANCE * MAX_CANDIDATE_DISTANCE - dx * dx - dz * dz;
		if (verticalSquared < 0) return null;
		int verticalRange = MathHelper.floor(Math.sqrt(verticalSquared));
		if (!cave) {
			BlockPos surface = new BlockPos(x, world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z), z);
			return Math.abs(surface.getY() - focus.getY()) <= verticalRange && isSafePosition(world, surface)
					? surface : null;
		}
		int bottom = Math.max(world.getBottomY() + 1, focus.getY() - verticalRange);
		int top = Math.min(world.getTopY() - 2, focus.getY() + verticalRange);
		if (bottom > top) return null;
		int start = bottom + world.random.nextInt(top - bottom + 1);
		for (int offset = 0; offset <= top - bottom; offset++) {
			int y = bottom + (start - bottom + offset) % (top - bottom + 1);
			BlockPos pos = new BlockPos(x, y, z);
			if (isSafePosition(world, pos) && !world.isSkyVisible(pos)) return pos;
		}
		return null;
	}

	/** Returns either the requested size or zero; never leaves an incomplete initial squad. */
	public static int spawnPatrol(ServerWorld world, BlockPos origin, int size, boolean natural) {
		if (size < MIN_PATROL_SIZE || size > MAX_PATROL_SIZE
				|| !world.getRegistryKey().equals(World.OVERWORLD)
				|| world.getDifficulty() == Difficulty.PEACEFUL
				|| (natural && !canSpawnNaturally(world))) return 0;
		List<BlockPos> positions = new ArrayList<>();
		for (int radius = 0; radius <= 4 && positions.size() < size; radius++) {
			for (int dx = -radius; dx <= radius && positions.size() < size; dx++) {
				for (int dz = -radius; dz <= radius && positions.size() < size; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
					for (int dy : new int[] {0, 1, -1, 2, -2}) {
						BlockPos pos = origin.add(dx, dy, dz);
						if (!isSafePosition(world, pos) || (natural && isNearPlayer(world, pos))) continue;
						Box box = bodyBox(pos);
						if (positions.stream().anyMatch(other -> bodyBox(other).expand(0.15).intersects(box))) continue;
						positions.add(pos);
						break;
					}
				}
			}
		}
		if (positions.size() != size) return 0;
		List<StormtrooperEntity> patrol = new ArrayList<>();
		UUID patrolId = UUID.randomUUID();
		float yaw = world.random.nextFloat() * 360;
		for (int slot = 0; slot < size; slot++) {
			StormtrooperEntity trooper = ModEntities.STORMTROOPER.create(world);
			if (trooper == null) return 0;
			BlockPos pos = positions.get(slot);
			trooper.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, yaw, 0);
			trooper.initialize(world, world.getLocalDifficulty(pos),
					natural ? SpawnReason.NATURAL : SpawnReason.COMMAND, null, null);
			trooper.setPatrol(patrolId, slot == 0 ? trooper.getUuid() : patrol.get(0).getUuid(), slot);
			patrol.add(trooper);
		}
		for (StormtrooperEntity trooper : patrol) {
			if (!world.spawnEntity(trooper)) {
				patrol.forEach(Entity::discard);
				return 0;
			}
		}
		return patrol.size();
	}

	public static boolean isSafePosition(ServerWorld world, BlockPos pos) {
		if (pos.getY() <= world.getBottomY() || pos.getY() + 2 >= world.getTopY()
				|| !world.isChunkLoaded(pos)) return false;
		Box body = bodyBox(pos);
		return world.getWorldBorder().contains(body)
				&& hasSupportingFloor(world, pos.down())
				&& !world.getBlockState(pos.down()).isOf(Blocks.MAGMA_BLOCK)
				&& !world.getBlockState(pos).isOf(Blocks.POWDER_SNOW)
				&& !world.getBlockState(pos).isOf(Blocks.FIRE)
				&& !world.getBlockState(pos).isOf(Blocks.SOUL_FIRE)
				&& !world.containsFluid(body) && world.isSpaceEmpty(null, body);
	}

	private static boolean hasSupportingFloor(ServerWorld world, BlockPos floor) {
		BlockState state = world.getBlockState(floor);
		// Paths, farmland, slabs and stairs can support the patrol too; a full cube is unnecessary.
		return state.getCollisionShape(world, floor).getBoundingBoxes().stream().anyMatch(box ->
				box.minX <= 0.5 && box.maxX >= 0.5 && box.minZ <= 0.5 && box.maxZ >= 0.5 && box.maxY >= 0.5);
	}

	private static Box bodyBox(BlockPos pos) {
		return new Box(pos.getX() + 0.2, pos.getY(), pos.getZ() + 0.2,
				pos.getX() + 0.8, pos.getY() + 1.8, pos.getZ() + 0.8);
	}

	private static boolean isNearPlayer(ServerWorld world, BlockPos pos) {
		Vec3d center = Vec3d.ofBottomCenter(pos);
		return world.getPlayers().stream().anyMatch(player -> !player.isSpectator()
				&& player.squaredDistanceTo(center) < MIN_PLAYER_DISTANCE * MIN_PLAYER_DISTANCE);
	}

	private static int spawnFromCommand(ServerCommandSource source, int size) {
		Vec3d forward = Vec3d.fromPolar(0, source.getRotation().y).multiply(6);
		BlockPos origin = BlockPos.ofFloored(source.getPosition().add(forward));
		int count = spawnPatrol(source.getWorld(), origin, size, false);
		if (count == 0) {
			source.sendError(Text.translatable("commands.tropercraft-mod.patrol.failed"));
		} else {
			source.sendFeedback(() -> Text.translatable("commands.tropercraft-mod.patrol.success", count), true);
		}
		return count;
	}
}
