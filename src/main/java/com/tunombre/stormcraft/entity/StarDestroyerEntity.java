package com.tunombre.stormcraft.entity;

import com.tunombre.stormcraft.sound.ModSounds;
import com.tunombre.stormcraft.world.StarDestroyerSpawner;
import com.tunombre.stormcraft.world.ShipPursuitState;
import com.tunombre.stormcraft.world.ShipLifecycleState;
import com.tunombre.stormcraft.world.ImperialHostilityState;
import net.minecraft.world.GameMode;
import com.tunombre.stormcraft.item.TrooperHeadItem;
import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Difficulty;
import net.minecraft.world.Heightmap;
import net.minecraft.world.RaycastContext;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.World;

/** Flying hostile hull. Custom physics avoids scanning thousands of hull blocks every tick. */
public final class StarDestroyerEntity extends Entity {
	public static final float MAX_HEALTH = 100.0F; // Vanilla ravager health.
	public static final double FLIGHT_SPEED = 0.10;
	public static final int GROUND_CLEARANCE = 48;
	public static final int MAX_FLIGHT_TICKS = 6000;
	public static final int ARRIVAL_TICKS = 30;
	private static final TrackedData<Float> HEALTH = DataTracker.registerData(StarDestroyerEntity.class, TrackedDataHandlerRegistry.FLOAT);
	private static final TrackedData<Integer> ARRIVAL = DataTracker.registerData(StarDestroyerEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Integer> HURT = DataTracker.registerData(StarDestroyerEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Boolean> ATTACKING = DataTracker.registerData(StarDestroyerEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
	private static final TrackedData<Boolean> PLAYER_VISIBLE = DataTracker.registerData(StarDestroyerEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
	private static final TrackedData<Integer> DEPARTURE = DataTracker.registerData(StarDestroyerEntity.class, TrackedDataHandlerRegistry.INTEGER);
	public static final double ESCAPE_DISTANCE = 512;
	public static final double FORWARD_RANGE = 512;
	public static final double BELOW_RANGE = 320;
	public static final double SIDE_RANGE = 256;
	public static final double REAR_RANGE = 256;
	public static final float MAX_TURN_RATE = .22F;
	public static final float TURN_ACCELERATION = .012F;
	public static final double CANNON_SPACING = .9;
	private float turnVelocity;
	private int flightTicks;
	private int deploymentCooldown = 1;
	private int attackTicks;
	private int hurtCooldown;
	private boolean deployedFirstPatrol;
	private boolean altitudeInitialized;
	private double cruiseAltitude;
	private UUID targetId;
	private UUID ignoredPlayerId;
	private UUID mobTargetId;
	private Vec3d lastSeenPlayer;
	private Vec3d searchOrigin;
	private boolean searching;
	private double pursuitHeight;
	private int targetDeaths;
	public static final int ALERT_RANGE = 192;
	private int alertCooldown;
	private int unseenTicks;
	private int interpolationTicks;
	private Vec3d interpolationTarget = Vec3d.ZERO;
	private float interpolationYaw;

	public StarDestroyerEntity(EntityType<? extends StarDestroyerEntity> type, World world) {
		super(type, world);
		noClip = true;
		setNoGravity(true);
	}

	@Override
	protected void initDataTracker() {
		dataTracker.startTracking(HEALTH, MAX_HEALTH);
		dataTracker.startTracking(ARRIVAL, 0);
		dataTracker.startTracking(HURT, 0);
		dataTracker.startTracking(ATTACKING, false);
		dataTracker.startTracking(PLAYER_VISIBLE, false);
		dataTracker.startTracking(DEPARTURE, 0);
	}

	public float getHealth() { return dataTracker.get(HEALTH); }
	public int getArrivalTicks() { return dataTracker.get(ARRIVAL); }
	public void beginArrival() { dataTracker.set(ARRIVAL, ARRIVAL_TICKS); }
	public boolean isAttacking() { return dataTracker.get(ATTACKING); }
	public boolean isSirenActive() { return dataTracker.get(PLAYER_VISIBLE); }
	public int getDepartureTicks() { return dataTracker.get(DEPARTURE); }
	public void beginDeparture() {
		if (getWorld().isClient || getDepartureTicks() > 0) return;
		ServerWorld world = (ServerWorld) getWorld();
		ShipLifecycleState.get(world).deferToNextNight(world.getTimeOfDay(), targetId);
		ShipPursuitState.get(world).cancel(getUuid());
		ignoredPlayerId = targetId;
		targetId = null;
		mobTargetId = null;
		searching = false;
		attackTicks = 0;
		dataTracker.set(ATTACKING, false);
		dataTracker.set(PLAYER_VISIBLE, false);
		dataTracker.set(ARRIVAL, 0);
		dataTracker.set(DEPARTURE, ARRIVAL_TICKS);
		world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.HOSTILE, .7F, .6F);
	}

	public int getHurtTicks() { return dataTracker.get(HURT); }
	public boolean isSearching() { return searching; }
	public Vec3d getSearchOrigin() { return searchOrigin; }
	public UUID getTargetPlayerId() { return targetId; }

	public void setTargetPlayer(PlayerEntity player) {
		dataTracker.set(DEPARTURE, 0);
		targetId = player.getUuid();
		ignoredPlayerId = null;
		searching = false;
		searchOrigin = null;
		lastSeenPlayer = player.getPos();
		pursuitHeight = player.getY();
		targetDeaths = player instanceof ServerPlayerEntity serverPlayer ? ShipPursuitState.deaths(serverPlayer) : 0;
		unseenTicks = 0;
		attackTicks = 0;
		if (getWorld() instanceof ServerWorld world) ShipPursuitState.get(world).cancel(getUuid());
	}

	public void abandonPursuit() {
		ignoredPlayerId = targetId;
		targetId = null;
		searching = true;
		if (searchOrigin == null) searchOrigin = lastSeenPlayer == null ? getPos() : lastSeenPlayer;
		attackTicks = 0;
	}

	public void resetFlightLifetime() { flightTicks = 0; altitudeInitialized = true; cruiseAltitude = getY(); }

	private void beginSearch(ServerWorld world, PlayerEntity player) {
		searching = true;
		searchOrigin = lastSeenPlayer == null ? player.getPos() : lastSeenPlayer;
		attackTicks = 0;
		if (player instanceof ServerPlayerEntity serverPlayer) ShipPursuitState.get(world).schedule(world, this, serverPlayer);
	}

	@Override
	public void tick() {
		// Entity.tick scans fluids across its entire box. Keep that work local to the origin.
		Box hull = getBoundingBox();
		setBoundingBox(new Box(getX()-1, getY(), getZ()-1, getX()+1, getY()+2, getZ()+1));
		super.tick();
		setBoundingBox(hull);
		if (getWorld().isClient) {
			if (interpolationTicks > 0) {
				double fraction = 1.0 / interpolationTicks--;
				setPosition(getPos().lerp(interpolationTarget, fraction));
				setYaw(MathHelper.lerpAngleDegrees((float) fraction, getYaw(), interpolationYaw));
			}
			setBoundingBox(calculateBoundingBox());
			return;
		}
		ServerWorld world = (ServerWorld) getWorld();
		ShipPursuitState pursuit = ShipPursuitState.get(world);
		if (!ShipLifecycleState.get(world).claim(getUuid(), world.getTimeOfDay())) { discard(); return; }
		dataTracker.set(ATTACKING, false);
		dataTracker.set(PLAYER_VISIBLE, false);
		if (getDepartureTicks() > 0) {
			dataTracker.set(DEPARTURE, getDepartureTicks()-1);
			if (getDepartureTicks() == 0) {
				world.spawnParticles(ParticleTypes.END_ROD, getX(), getY(), getZ(), 70, 30, 8, 30, .4);
				pursuit.retire(getUuid());
				discard();
			}
			return;
		}
		if (pursuit.isRetired(getUuid())) { discard(); return; }
		if (pursuit.isAbandoned(getUuid()) && targetId != null) { beginDeparture(); return; }
		if (++flightTicks >= MAX_FLIGHT_TICKS || world.getDifficulty() == Difficulty.PEACEFUL) {
			pursuit.cancel(getUuid());
			discard();
			return;
		}
		if (hurtCooldown > 0) hurtCooldown--;
		if (getHurtTicks() > 0) dataTracker.set(HURT, getHurtTicks()-1);
		if (getArrivalTicks() > 0) {
			dataTracker.set(ARRIVAL, getArrivalTicks()-1);
			if (getArrivalTicks() == 0) {
				world.spawnParticles(ParticleTypes.END_ROD, getX(), getY()+5, getZ(), 80, 35, 8, 35, 0.4);
				world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_ENDERMAN_TELEPORT,
						SoundCategory.HOSTILE, 0.8F, 0.5F);
			}
			return;
		}
		if (!world.isChunkLoaded(getBlockPos())) return;
		PlayerEntity playerTarget = trackPlayer(world);
		if (getDepartureTicks() > 0) return;
		boolean seesPlayer = playerTarget != null && canDetectPosition(playerTarget.getEyePos())
				&& hasLineOfSight(world, playerTarget);
		LivingEntity combatTarget = seesPlayer ? playerTarget : findMobTarget(world);
		dataTracker.set(PLAYER_VISIBLE, seesPlayer);
		if (seesPlayer) {
			if (--alertCooldown <= 0) {
				alertNearbyTroopers(world, playerTarget);
				alertCooldown = 20;
			}
		} else alertCooldown = 0;
		Vec3d destination = null;
		if (searching && searchOrigin != null) {
			// Orbit the last known position rather than following the hidden player's coordinates.
			double angle = Math.atan2(getZ()-searchOrigin.z, getX()-searchOrigin.x)+0.18;
			destination = searchOrigin.add(Math.cos(angle)*100, 0, Math.sin(angle)*100);
		} else if (playerTarget != null) destination = playerTarget.getPos();
		else if (combatTarget != null) destination = combatTarget.getPos();
		if (destination != null) {
			Vec3d delta = destination.subtract(getPos());
			float desiredYaw = (float) (MathHelper.atan2(delta.z, delta.x)*180/Math.PI)-90;
			float error = MathHelper.wrapDegrees(desiredYaw-getYaw());
			float desiredTurn = MathHelper.clamp(error*.025F, -MAX_TURN_RATE, MAX_TURN_RATE);
			turnVelocity = MathHelper.stepTowards(turnVelocity, desiredTurn, TURN_ACCELERATION);
			setYaw(MathHelper.wrapDegrees(getYaw()+turnVelocity));
		}
		if (!altitudeInitialized) {
			cruiseAltitude = getCruiseAltitude(world, getPos(), getYaw());
			setPosition(getX(), cruiseAltitude, getZ());
			altitudeInitialized = true;
		}
		Vec3d forward = Vec3d.fromPolar(0, getYaw());
		double distance = destination == null ? 200 : horizontalDistance(destination);
		double speed = searching ? FLIGHT_SPEED : destination == null ? 0.025
				: distance > 105 ? FLIGHT_SPEED : distance < 85 ? -FLIGHT_SPEED : 0;
		Vec3d next = getPos().add(forward.multiply(speed));
		if (world.isChunkLoaded(BlockPos.ofFloored(next)) && world.getWorldBorder().contains(BlockPos.ofFloored(next))) {
			// Turns expose new parts of the hull to terrain; check the next position before moving.
			cruiseAltitude = getCruiseAltitude(world, next, getYaw());
			setPosition(next.x, Math.max(cruiseAltitude, getY()-0.15), next.z);
		}
		setBoundingBox(calculateBoundingBox());
		if (combatTarget != null) tickAttack(world, combatTarget);
		else attackTicks = 0;
		if (--deploymentCooldown <= 0) {
			int count = StarDestroyerSpawner.deployGroundPatrol(world, this, !deployedFirstPatrol);
			if (count > 0) deployedFirstPatrol = true;
			deploymentCooldown = count > 0 ? 600 : 100;
		}
	}

	private void alertNearbyTroopers(ServerWorld world, PlayerEntity player) {
		for (StormtrooperEntity trooper : world.getEntitiesByClass(StormtrooperEntity.class,
				new Box(getPos(), getPos()).expand(ALERT_RANGE),
				candidate -> candidate.isAlive() && candidate.squaredDistanceTo(this) <= ALERT_RANGE*ALERT_RANGE)) {
			trooper.alertToPlayer(player);
		}
	}

	private double horizontalDistance(Vec3d position) {
		return Math.hypot(position.x-getX(), position.z-getZ());
	}

	private PlayerEntity trackPlayer(ServerWorld world) {
		PlayerEntity player = targetId == null ? null : world.getPlayerByUuid(targetId);
		if (targetId != null && player != null && (!player.isAlive()
				|| player instanceof ServerPlayerEntity serverPlayer && ShipPursuitState.deaths(serverPlayer) != targetDeaths)) {
			beginDeparture();
			return null;
		}
		if (searching && isSurvivalPlayer(player) && !TrooperHeadItem.isDisguised(player)) {
			if (isValidTarget(player) && canDetectPosition(player.getEyePos())
					&& pursuitHeight-player.getY() < 4 && hasLineOfSight(world, player)) setTargetPlayer(player);
			else return null;
		}
		if (player == null || !isSurvivalPlayer(player)) {
			player = world.getPlayers(candidate -> isSurvivalPlayer(candidate) && !candidate.getUuid().equals(ignoredPlayerId))
					.stream().min(java.util.Comparator.comparingDouble(this::squaredDistanceTo)).orElse(null);
			if (player != null) setTargetPlayer(player);
		}
		if (player == null) return null;
		if (TrooperHeadItem.isDisguised(player)) {
			ShipPursuitState.get(world).cancel(getUuid());
			searching = true;
			if (searchOrigin == null) searchOrigin = lastSeenPlayer == null ? getPos() : lastSeenPlayer;
			return null;
		}
		pursuitHeight = Math.max(pursuitHeight, player.getY());
		boolean visible = hasLineOfSight(world, player);
		unseenTicks = visible ? 0 : unseenTicks+1;
		if (!canDetectPosition(player.getEyePos()) || pursuitHeight-player.getY() >= 4 || unseenTicks >= 40) {
			beginSearch(world, player);
			return null;
		}
		if (visible) lastSeenPlayer = player.getPos();
		return player;
	}

	public static boolean isEnemyMob(LivingEntity entity) {
		return GroundTargetRules.isGroundMob(entity);
	}

	private LivingEntity findMobTarget(ServerWorld world) {
		Vec3d focus = muzzlePosition();
		Entity previous = mobTargetId == null ? null : world.getEntity(mobTargetId);
		if (flightTicks % 20 != 0) {
			return previous instanceof LivingEntity living && isEnemyMob(living)
					&& canDetectPosition(living.getEyePos()) && hasLineOfSight(world, living) ? living : null;
		}
		LivingEntity nearest = null;
		double best = Double.MAX_VALUE;
		for (MobEntity mob : world.getEntitiesByClass(MobEntity.class, new Box(getPos().add(-600, -BELOW_RANGE, -600), getPos().add(600, 160, 600)), StarDestroyerEntity::isEnemyMob)) {
			double distance = mob.squaredDistanceTo(focus);
			if (canDetectPosition(mob.getEyePos()) && distance < best && hasLineOfSight(world, mob)) { nearest = mob; best = distance; }
		}
		mobTargetId = nearest == null ? null : nearest.getUuid();
		return nearest;
	}

	/** Broad forward sensors and full underside coverage, with shorter flank/rear coverage. */
	public boolean canDetectPosition(Vec3d position) {
		Vec3d delta = position.subtract(getPos());
		Vec3d forward = Vec3d.fromPolar(0, getYaw());
		double longitudinal = delta.x*forward.x+delta.z*forward.z;
		double lateral = Math.abs(-delta.x*forward.z+delta.z*forward.x);
		boolean below = delta.y <= 0 && delta.y >= -BELOW_RANGE && lateral <= 128 && longitudinal >= -112 && longitudinal <= 144;
		boolean ahead = longitudinal >= 0 && longitudinal <= FORWARD_RANGE && lateral <= SIDE_RANGE && delta.y >= -BELOW_RANGE && delta.y <= 160;
		boolean flankOrRear = longitudinal >= -REAR_RANGE && longitudinal < 0 && lateral <= SIDE_RANGE && delta.y >= -192 && delta.y <= 96;
		return below || ahead || flankOrRear;
	}

	public float getTurnVelocity() { return turnVelocity; }

	private static boolean isSurvivalPlayer(PlayerEntity player) {
		return player != null && player.isAlive() && !player.isSpectator() && !player.isCreative()
				&& (!(player instanceof ServerPlayerEntity server) || server.interactionManager.getGameMode() == GameMode.SURVIVAL);
	}

	private static boolean isValidTarget(PlayerEntity player) {
		return isSurvivalPlayer(player) && !TrooperHeadItem.isDisguised(player);
	}

	private Vec3d muzzlePosition() {
		return getPos().add(Vec3d.fromPolar(0, getYaw()).multiply(65)).add(0, -9, 0);
	}

	private boolean hasLineOfSight(ServerWorld world, LivingEntity target) {
		return world.raycast(new RaycastContext(muzzlePosition(), target.getEyePos(), RaycastContext.ShapeType.COLLIDER,
				RaycastContext.FluidHandling.NONE, this)).getType() == HitResult.Type.MISS;
	}

	private void tickAttack(ServerWorld world, LivingEntity target) {
		if (getDepartureTicks() > 0 || target instanceof PlayerEntity player && !isValidTarget(player)) { attackTicks = 0; return; }
		Vec3d muzzle = muzzlePosition();
		Vec3d aim = target.getEyePos();
		if (!canDetectPosition(aim) || world.raycast(new RaycastContext(muzzle, aim,
				RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, this)).getType() != HitResult.Type.MISS) {
			attackTicks = 0;
			return;
		}
		dataTracker.set(ATTACKING, true);
		// Ghast rhythm: short visible charge, one aimed shot, then recovery.
		if (++attackTicks == 10) world.spawnParticles(ParticleTypes.ELECTRIC_SPARK,
				muzzle.x, muzzle.y, muzzle.z, 18, 1.3, 0.6, 1.3, 0.05);
		if (attackTicks == 20 || attackTicks == 26) fireLaserPair(world, muzzle, aim);
		if (attackTicks >= 26) attackTicks = -40;
	}

	private void fireLaserPair(ServerWorld world, Vec3d muzzle, Vec3d aim) {
		Vec3d forward = Vec3d.fromPolar(0, getYaw());
		Vec3d right = new Vec3d(-forward.z, 0, forward.x);
		Vec3d velocity = aim.subtract(muzzle).normalize().multiply(2.5);
		for (int side : new int[]{-1, 1}) {
			LaserBoltEntity laser = new LaserBoltEntity(ModEntities.LASER_BOLT, world);
			laser.setOwner(this);
			laser.setPosition(muzzle.add(right.multiply(side*CANNON_SPACING/2)));
			laser.setVelocity(velocity);
			world.spawnEntity(laser);
		}
		world.playSound(null, muzzle.x, muzzle.y, muzzle.z, ModSounds.BLASTER_SHOOT, SoundCategory.HOSTILE, .75F, .65F);
	}

	/** Sample the triangular hull and a short lookahead, using loaded chunks only. */
	public static double getCruiseAltitude(ServerWorld world, Vec3d center, float yaw) {
		int highest = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, MathHelper.floor(center.x), MathHelper.floor(center.z));
		Vec3d forward = Vec3d.fromPolar(0, yaw);
		Vec3d right = new Vec3d(-forward.z, 0, forward.x);
		for (int longitudinal = -64; longitudinal <= 96; longitudinal += 16) {
			double halfWidth = MathHelper.clamp((88.0-longitudinal)/144.0, 0, 1)*48;
			for (int lateral = -2; lateral <= 2; lateral++) {
				BlockPos pos = BlockPos.ofFloored(center.add(forward.multiply(longitudinal)).add(right.multiply(halfWidth*lateral/2)));
				if (world.isChunkLoaded(pos)) highest = Math.max(highest, world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ()));
			}
		}
		return highest+GROUND_CLEARANCE;
	}

	@Override
	protected Box calculateBoundingBox() {
		// Bounds of every mesh vertex, rotated with the renderer (180 - yaw).
		double angle = Math.toRadians(180-getYaw()), sin = Math.sin(angle), cos = Math.cos(angle);
		double minX = Double.POSITIVE_INFINITY, maxX = Double.NEGATIVE_INFINITY;
		double minZ = Double.POSITIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
		for (double x : new double[]{-48, 48}) for (double z : new double[]{-81.5, 63}) {
			double rx = x*cos+z*sin, rz = -x*sin+z*cos;
			minX = Math.min(minX, rx); maxX = Math.max(maxX, rx);
			minZ = Math.min(minZ, rz); maxZ = Math.max(maxZ, rz);
		}
		return new Box(getX()+minX, getY()-7.36, getZ()+minZ, getX()+maxX, getY()+43, getZ()+maxZ);
	}

	@Override
	public void updateTrackedPositionAndAngles(double x, double y, double z, float yaw, float pitch, int steps, boolean interpolate) {
		interpolationTarget = new Vec3d(x, y, z);
		interpolationYaw = yaw;
		interpolationTicks = Math.max(1, steps);
	}

	@Override
	public boolean damage(DamageSource source, float amount) {
		if (getWorld() instanceof ServerWorld world && isAlive() && source.getAttacker() instanceof PlayerEntity player
				&& (TrooperHeadItem.isWearingTrooperHead(player)
						|| source.getSource() instanceof LaserBoltEntity laser && laser.wasFiredWithTrooperHead())) {
			ImperialHostilityState.get(world).identify(player);
		}
		if (getWorld().isClient || !isAlive() || getArrivalTicks() > 0 || getDepartureTicks() > 0 || hurtCooldown > 0
				|| isInvulnerableTo(source) || !Float.isFinite(amount) || amount <= 0) return false;
		float remaining = Math.max(0, getHealth()-amount);
		dataTracker.set(HEALTH, remaining);
		hurtCooldown = 10;
		dataTracker.set(HURT, 10);
		if (source.getAttacker() instanceof PlayerEntity player) setTargetPlayer(player);
		ServerWorld world = (ServerWorld) getWorld();
		Vec3d impact = source.getPosition() == null ? getPos() : source.getPosition();
		world.playSound(null, impact.x, impact.y, impact.z, ModSounds.STAR_DESTROYER_HURT, SoundCategory.HOSTILE, 0.75F, 0.8F);
		ShipPursuitState.get(world).updateSnapshot(this);
		world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, source.getPosition() == null ? getX() : source.getPosition().x,
				getY(), source.getPosition() == null ? getZ() : source.getPosition().z, 12, 2, 1, 2, 0.1);
		if (remaining <= 0) {
			ShipLifecycleState.get(world).destroyed(getUuid(), world.getTimeOfDay());
			ShipPursuitState.get(world).cancel(getUuid());
			world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getY(), getZ(), 8, 25, 8, 25, 0);
			world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.HOSTILE, 0.8F, 0.7F);
			discard();
		}
		return true;
	}

	@Override public void kill() {
		if (getWorld() instanceof ServerWorld world) {
			ShipPursuitState.get(world).cancel(getUuid());
			ShipLifecycleState.get(world).destroyed(getUuid(), world.getTimeOfDay());
		}
		super.kill();
	}

	@Override public void remove(RemovalReason reason) {
		if (getWorld() instanceof ServerWorld world) {
			if (reason == RemovalReason.UNLOADED_TO_CHUNK) {
				PlayerEntity player = targetId == null ? null : world.getPlayerByUuid(targetId);
				if (getDepartureTicks() == 0 && player instanceof ServerPlayerEntity && isValidTarget(player)) {
					if (!ShipPursuitState.get(world).isPending(getUuid())) beginSearch(world, player);
				} else {
					ShipPursuitState.get(world).retire(getUuid());
					ShipLifecycleState.get(world).release(getUuid());
				}
			} else if (reason.shouldDestroy()) {
				ShipPursuitState.get(world).cancel(getUuid());
				ShipLifecycleState.get(world).release(getUuid());
			}
		}
		super.remove(reason);
	}

	@Override public boolean isAttackable() { return true; }
	@Override public boolean canHit() { return isAlive() && getArrivalTicks() == 0 && getDepartureTicks() == 0; }
	@Override public boolean isCollidable() { return false; }
	@Override public boolean isAlive() { return !isRemoved() && getHealth() > 0; }
	@Override public boolean shouldRender(double distance) { return distance < 768.0*768.0; }
	@Override public Box getVisibilityBoundingBox() { return (getArrivalTicks() > 0 || getDepartureTicks() > 0) ? getBoundingBox().expand(600) : getBoundingBox(); }
	@Override public Packet<ClientPlayPacketListener> createSpawnPacket() { return new EntitySpawnS2CPacket(this); }

	@Override
	protected void writeCustomDataToNbt(NbtCompound nbt) {
		nbt.putFloat("Health", getHealth());
		nbt.putInt("ArrivalTicks", getArrivalTicks());
		nbt.putInt("DepartureTicks", getDepartureTicks());
		nbt.putInt("FlightTicks", flightTicks);
		nbt.putFloat("TurnVelocity", turnVelocity);
		nbt.putInt("DeploymentCooldown", deploymentCooldown);
		nbt.putBoolean("DeployedFirstPatrol", deployedFirstPatrol);
		if (targetId != null) nbt.putUuid("TargetPlayer", targetId);
		if (ignoredPlayerId != null) nbt.putUuid("IgnoredPlayer", ignoredPlayerId);
		nbt.putBoolean("Searching", searching);
		nbt.putDouble("PursuitHeight", pursuitHeight);
		nbt.putInt("TargetDeaths", targetDeaths);
		if (lastSeenPlayer != null) writePosition(nbt, "LastSeen", lastSeenPlayer);
		if (searchOrigin != null) writePosition(nbt, "SearchOrigin", searchOrigin);
	}

	@Override
	protected void readCustomDataFromNbt(NbtCompound nbt) {
		dataTracker.set(HEALTH, nbt.contains("Health") ? MathHelper.clamp(nbt.getFloat("Health"), 0, MAX_HEALTH) : MAX_HEALTH);
		dataTracker.set(DEPARTURE, MathHelper.clamp(nbt.getInt("DepartureTicks"), 0, ARRIVAL_TICKS));
		dataTracker.set(ARRIVAL, MathHelper.clamp(nbt.getInt("ArrivalTicks"), 0, ARRIVAL_TICKS));
		flightTicks = MathHelper.clamp(nbt.getInt("FlightTicks"), 0, MAX_FLIGHT_TICKS);
		turnVelocity = MathHelper.clamp(nbt.getFloat("TurnVelocity"), -MAX_TURN_RATE, MAX_TURN_RATE);
		deploymentCooldown = MathHelper.clamp(nbt.getInt("DeploymentCooldown"), 1, 600);
		deployedFirstPatrol = nbt.getBoolean("DeployedFirstPatrol");
		targetId = nbt.containsUuid("TargetPlayer") ? nbt.getUuid("TargetPlayer") : null;
		ignoredPlayerId = nbt.containsUuid("IgnoredPlayer") ? nbt.getUuid("IgnoredPlayer") : null;
		searching = nbt.getBoolean("Searching");
		pursuitHeight = nbt.getDouble("PursuitHeight");
		targetDeaths = nbt.getInt("TargetDeaths");
		lastSeenPlayer = readPosition(nbt, "LastSeen");
		searchOrigin = readPosition(nbt, "SearchOrigin");
		altitudeInitialized = false;
	}

	private static void writePosition(NbtCompound nbt, String key, Vec3d position) {
		NbtCompound tag = new NbtCompound();
		tag.putDouble("X", position.x); tag.putDouble("Y", position.y); tag.putDouble("Z", position.z);
		nbt.put(key, tag);
	}

	private static Vec3d readPosition(NbtCompound nbt, String key) {
		if (!nbt.contains(key)) return null;
		NbtCompound tag = nbt.getCompound(key);
		return new Vec3d(tag.getDouble("X"), tag.getDouble("Y"), tag.getDouble("Z"));
	}
}
