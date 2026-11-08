package com.tunombre.stormcraft;

import com.tunombre.stormcraft.entity.ModEntities;
import com.tunombre.stormcraft.entity.StarDestroyerEntity;
import com.tunombre.stormcraft.entity.StormtrooperEntity;
import com.tunombre.stormcraft.world.StarDestroyerSpawner;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

public class StarDestroyerGameTests implements FabricGameTest {
	@Override
	public void invokeTestMethod(TestContext context, java.lang.reflect.Method method) {
		resetShipFixtures(context);
		try { FabricGameTest.super.invokeTestMethod(context, method); }
		finally { resetShipFixtures(context); }
	}

	private static void resetShipFixtures(TestContext context) {
		var ships = new java.util.ArrayList<StarDestroyerEntity>();
		for (var entity : context.getWorld().iterateEntities()) if (entity instanceof StarDestroyerEntity ship) ships.add(ship);
		ships.forEach(StarDestroyerEntity::discard);
		context.getWorld().getPersistentStateManager().set(com.tunombre.stormcraft.world.ShipLifecycleState.SAVE_KEY,
				new com.tunombre.stormcraft.world.ShipLifecycleState());
		context.getWorld().getPersistentStateManager().set(com.tunombre.stormcraft.world.ImperialHostilityState.SAVE_KEY, new com.tunombre.stormcraft.world.ImperialHostilityState());
		context.getWorld().getPersistentStateManager().set("tropercraft_ship_pursuits", new com.tunombre.stormcraft.world.ShipPursuitState());
	}
	@GameTest(templateName = EMPTY_STRUCTURE)
	public void flyoverUsesTerrainInsteadOfPlayerHeight(TestContext context) {
		Vec3d center = Vec3d.ofCenter(context.getAbsolutePos(new BlockPos(3, 1, 3)));
		double altitude = StarDestroyerEntity.getCruiseAltitude(context.getWorld(), center, 0);
		var existing = new java.util.HashSet<java.util.UUID>();
		for (var entity : context.getWorld().iterateEntities()) if (entity instanceof StarDestroyerEntity) existing.add(entity.getUuid());
		context.assertTrue(StarDestroyerSpawner.spawnShip(context.getWorld(), center.add(0, 300, 0), 0),
				"Debe crear la nave dentro de los chunks cargados");
		var ships = context.getWorld().getEntitiesByClass(StarDestroyerEntity.class,
				new Box(center.x-.1, altitude-1, center.z-.1, center.x+.1, altitude+1, center.z+.1), ship -> !existing.contains(ship.getUuid()));
		try {
			context.assertTrue(ships.size() == 1 && Math.abs(ships.get(0).getY()-altitude) < .01 && altitude < 224,
					"El sobrevuelo debe ser bajo aunque el ejecutor esté muy alto");
		} finally { ships.forEach(StarDestroyerEntity::discard); }
		context.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void shipDescendsGentlyAfterHigherTerrain(TestContext context) {
		StarDestroyerEntity ship = readyShip(context);
		double altitude = StarDestroyerEntity.getCruiseAltitude(context.getWorld(), ship.getPos(), ship.getYaw());
		ship.setPosition(ship.getX(), altitude+12, ship.getZ());
		try {
			for (int tick = 0; tick < 10; tick++) ship.tick();
			context.assertTrue(ship.getY() < altitude+11 && ship.getY() > altitude+10,
					"La nave debe descender gradualmente en lugar de conservar la altura de la montaña: "+ship.getY()+" base="+altitude);
			context.assertTrue(ship.getY() >= StarDestroyerEntity.getCruiseAltitude(context.getWorld(), ship.getPos(), ship.getYaw()),
					"Debe conservar la separación del terreno mientras desciende");
		} finally { ship.discard(); }
		context.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void savedShipRecalculatesOldCruiseAltitude(TestContext context) {
		StarDestroyerEntity ship = context.spawnEntity(ModEntities.STAR_DESTROYER, 3, 1, 3);
		ship.setPosition(ship.getX(), 280, ship.getZ());
		NbtCompound saved = ship.writeNbt(new NbtCompound());
		saved.putBoolean("AltitudeInitialized", true);
		saved.putInt("DeploymentCooldown", 600);
		ship.readNbt(saved);
		for (int tick = 0; tick < 2; tick++) ship.tick();
		{
			double altitude = StarDestroyerEntity.getCruiseAltitude(context.getWorld(), ship.getPos(), ship.getYaw());
			context.assertTrue(ship.getY() >= altitude && ship.getY() < 224,
					"Las naves guardadas con la altura anterior deben adoptar el vuelo bajo al cargar");
			ship.discard();
			context.complete();
		}
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void thirdNightAndLaterNightsOnly(TestContext context) {
		for (long time : new long[]{0, 13000, 37000, 60999, 72000, 85000-1}) {
			context.assertFalse(StarDestroyerSpawner.isEncounterNight(time), "No debe aparecer antes de la tercera noche ni de día: "+time);
		}
		context.assertTrue(StarDestroyerSpawner.isEncounterNight(61000), "Debe comenzar en la tercera noche");
		context.assertTrue(StarDestroyerSpawner.isEncounterNight(85000), "Permite encuentros pendientes en noches posteriores");
		context.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void hullHasRavagerHealthAndPersistsDamage(TestContext context) {
		StarDestroyerEntity ship = context.spawnEntity(ModEntities.STAR_DESTROYER, 3, 1, 3);
		context.assertTrue(ship.getHealth() == 100, "Debe tener los 100 puntos de vida de un ravager");
		context.assertTrue(ship.damage(context.getWorld().getDamageSources().generic(), 12), "Debe aceptar daño");
		context.assertTrue(ship.getHealth() == 88, "El casco debe perder vida");
		NbtCompound saved = ship.writeNbt(new NbtCompound());
		ship.readNbt(saved);
		context.assertTrue(ship.getHealth() == 88, "Guardar y cargar debe conservar el daño");
		ship.discard();
		context.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "shipCombat")
	public void lasersHitTheBowFarFromEntityOrigin(TestContext context) {
		var oldShips = new java.util.ArrayList<StarDestroyerEntity>();
		for (var entity : context.getWorld().iterateEntities()) if (entity instanceof StarDestroyerEntity ship && ship.getY() > 250) oldShips.add(ship);
		oldShips.forEach(StarDestroyerEntity::discard);
		StarDestroyerEntity ship = context.spawnEntity(ModEntities.STAR_DESTROYER, 3, 1, 3);
		ship.refreshPositionAndAngles(ship.getX(), 280, ship.getZ(), 0, 0);
		Vec3d bow = ship.getPos().add(0, 5, 80);
		context.assertTrue(context.getWorld().getOtherEntities(null, new Box(bow, bow.add(1, 1, 1)), entity -> entity == ship).contains(ship),
				"La búsqueda de impactos debe encontrar la proa a 80 bloques del centro");
		var player = context.createMockSurvivalPlayer();
		var bolt = new com.tunombre.stormcraft.entity.LaserBoltEntity(context.getWorld(), player);
		bolt.setPosition(ship.getPos().add(0, 5, 90));
		bolt.setVelocity(0, 0, -12);
		context.getWorld().getChunk(bolt.getBlockX() >> 4, bolt.getBlockZ() >> 4);
		context.getWorld().spawnEntity(bolt);
		var collision = net.minecraft.entity.projectile.ProjectileUtil.getCollision(bolt, entity -> entity == ship);
		context.assertTrue(collision.getType() == net.minecraft.util.hit.HitResult.Type.ENTITY, "El rayo del láser debe encontrar el casco: "+collision.getType()+" "+ship.getBoundingBox());
		bolt.tick();
		context.assertTrue(ship.getHealth() < 100, "El láser debe dañar la proa y no atravesarla; eliminado="+bolt.isRemoved()+" cargado="+context.getWorld().isChunkLoaded(bolt.getBlockPos())+" salud="+ship.getHealth());
		ship.discard();
		bolt.discard();
		context.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "shipCombat")
	public void arrivalThenPursuitAndAimedLaser(TestContext context) {
		context.getWorld().getServer().setDifficulty(net.minecraft.world.Difficulty.NORMAL, true);
		StarDestroyerEntity ship = context.spawnEntity(ModEntities.STAR_DESTROYER, 3, 1, 3);
		double altitude = 220;
		ship.refreshPositionAndAngles(ship.getX(), altitude, ship.getZ(), 0, 0);
		ship.resetFlightLifetime();
		var player = new net.minecraft.server.network.ServerPlayerEntity(context.getWorld().getServer(), context.getWorld(),
				new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "ship-target"));
		context.getWorld().getServer().getPlayerManager().onPlayerConnect(
				new net.minecraft.network.ClientConnection(net.minecraft.network.NetworkSide.SERVERBOUND), player);
		player.changeGameMode(net.minecraft.world.GameMode.SURVIVAL);
		context.assertFalse(player.isCreative() || player.isSpectator(), "El objetivo de la prueba debe estar en supervivencia");
		player.setPosition(ship.getX(), altitude-30, ship.getZ()+125);
		context.getWorld().getChunk(player.getBlockX() >> 4, player.getBlockZ() >> 4);
		Vec3d muzzle = ship.getPos().add(0, -9, 65);
		context.getWorld().getChunk(net.minecraft.util.math.BlockPos.ofFloored(muzzle).getX() >> 4,
				net.minecraft.util.math.BlockPos.ofFloored(muzzle).getZ() >> 4);
		ship.setTargetPlayer(player);
		ship.beginArrival();
		context.assertFalse(ship.damage(context.getWorld().getDamageSources().generic(), 1), "La entrada de hiperespacio debe finalizar antes del combate");
		for (int tick = 0; tick < StarDestroyerEntity.ARRIVAL_TICKS; tick++) ship.tick();
		double previousDistance = ship.squaredDistanceTo(player);
		for (int tick = 0; tick < 20; tick++) ship.tick();
		context.assertTrue(ship.squaredDistanceTo(player) < previousDistance-10, "Debe acercarse al jugador");
		var bolts = context.getWorld().getEntitiesByClass(com.tunombre.stormcraft.entity.LaserBoltEntity.class,
				ship.getBoundingBox().expand(100), bolt -> bolt.getOwner() == ship);
		context.assertTrue(!bolts.isEmpty(), "Tras la carga debe disparar un láser; nave="+ship.getPos()+" jugador="+player.getPos());
		context.assertTrue(bolts.get(0).getVelocity().normalize().dotProduct(player.getEyePos().subtract(bolts.get(0).getPos()).normalize()) > .99,
				"Debe apuntar al jugador");
		bolts.forEach(com.tunombre.stormcraft.entity.LaserBoltEntity::discard);
		ship.discard();
		player.discard();
		context.complete();
	}

	private static net.minecraft.server.network.ServerPlayerEntity survivalTarget(TestContext context, StarDestroyerEntity ship) {
		var player = new net.minecraft.server.network.ServerPlayerEntity(context.getWorld().getServer(), context.getWorld(),
				new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "ship_"+java.util.UUID.randomUUID().toString().substring(0, 8)));
		context.getWorld().getServer().getPlayerManager().onPlayerConnect(
				new net.minecraft.network.ClientConnection(net.minecraft.network.NetworkSide.SERVERBOUND), player);
		player.changeGameMode(net.minecraft.world.GameMode.SURVIVAL);
		for (int tick = 0; tick < 60; tick++) player.tick(); // Expire vanilla join protection before damage assertions.
		player.setPosition(ship.getX(), ship.getY()-30, ship.getZ()+125);
		loadTargetArea(context, player);
		ship.setTargetPlayer(player);
		return player;
	}

	private static void loadTargetArea(TestContext context, net.minecraft.entity.player.PlayerEntity player) {
		for (int offset : new int[]{0, 64, 120, 200}) {
			BlockPos pos = BlockPos.ofFloored(player.getPos().add(0, 0, offset));
			context.getWorld().getChunk(pos.getX() >> 4, pos.getZ() >> 4);
		}
	}

	private static StarDestroyerEntity readyShip(TestContext context) {
		context.getWorld().getServer().setDifficulty(net.minecraft.world.Difficulty.NORMAL, true);
		context.getWorld().getGameRules().get(net.minecraft.world.GameRules.DO_MOB_SPAWNING).set(false, context.getWorld().getServer());
		StarDestroyerEntity ship = context.spawnEntity(ModEntities.STAR_DESTROYER, 3, 1, 3);
		double altitude = StarDestroyerEntity.getCruiseAltitude(context.getWorld(), ship.getPos(), 0);
		ship.refreshPositionAndAngles(ship.getX(), altitude, ship.getZ(), 0, 0);
		ship.resetFlightLifetime();
		return ship;
	}

	private static void cleanEncounter(TestContext context, StarDestroyerEntity ship, net.minecraft.server.network.ServerPlayerEntity player) {
		com.tunombre.stormcraft.world.ShipPursuitState.get(context.getWorld()).cancel(ship.getUuid());
		context.getWorld().getEntitiesByClass(com.tunombre.stormcraft.entity.LaserBoltEntity.class,
				ship.getBoundingBox().expand(256), bolt -> bolt.getOwner() == ship).forEach(net.minecraft.entity.Entity::discard);
		ship.discard();
		context.getWorld().getServer().getPlayerManager().remove(player);
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "shipBehavior")
	public void damageFlashEndsAfterTenTicks(TestContext context) {
		StarDestroyerEntity ship = readyShip(context);
		ship.damage(context.getWorld().getDamageSources().generic(), 1);
		context.assertTrue(ship.getHurtTicks() == 10, "Cada impacto aceptado debe activar el rojo sincronizado");
		for (int i = 0; i < 10; i++) ship.tick();
		context.assertTrue(ship.getHurtTicks() == 0, "El destello debe terminar tras medio segundo");
		ship.discard();
		context.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "shipBehavior")
	public void droppingFourBlocksStartsPatrolThenReturnsAfterThirtySeconds(TestContext context) {
		StarDestroyerEntity ship = readyShip(context);
		var player = survivalTarget(context, ship);
		ship.damage(context.getWorld().getDamageSources().generic(), 12);
		for (int step = 0; step < 3; step++) { player.setPosition(player.getPos().add(0, -1, 0)); ship.tick(); }
		context.assertFalse(ship.isSearching(), "Tres bloques todavía no deben activar la búsqueda");
		player.setPosition(player.getPos().add(0, -1, 0)); ship.tick();
		context.assertTrue(ship.isSearching(), "Bajar cuatro bloques, incluso poco a poco, debe permitir perderla");
		Vec3d origin = ship.getSearchOrigin();
		player.setPosition(player.getPos().add(32, 0, 24));
		loadTargetArea(context, player);
		for (int tick = 0; tick < 20; tick++) ship.tick();
		context.assertTrue(ship.getSearchOrigin().equals(origin), "Debe patrullar la última posición y no seguir al jugador oculto");
		var state = com.tunombre.stormcraft.world.ShipPursuitState.get(context.getWorld());
		var restored = com.tunombre.stormcraft.world.ShipPursuitState.fromNbt(state.writeNbt(new NbtCompound()));
		context.assertTrue(restored.isPending(ship.getUuid()), "El temporizador debe sobrevivir a guardar y cargar");
		state.tick(context.getWorld(), context.getWorld().getTime()+599);
		context.assertTrue(ship.isSearching() && ship.getArrivalTicks() == 0, "No debe reaparecer antes de los 30 segundos");
		state.tick(context.getWorld(), context.getWorld().getTime()+600);
		context.assertTrue(!ship.isSearching() && ship.getArrivalTicks() == StarDestroyerEntity.ARRIVAL_TICKS,
				"Debe regresar desde el hiperespacio al cumplirse los 30 segundos");
		context.assertTrue(ship.getHealth() == 88 && !state.isPending(ship.getUuid()), "Debe conservar el daño y consumir el regreso");
		context.assertTrue(Math.abs(ship.getZ()-player.getZ()-120) < .01, "Debe reaparecer frente a la posición actual del jugador");
		cleanEncounter(context, ship, player);
		context.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "shipBehavior")
	public void sprintingCanEscapeTheSlowShip(TestContext context) {
		StarDestroyerEntity ship = readyShip(context);
		var player = survivalTarget(context, ship);
		Vec3d start = ship.getPos();
		for (int tick = 0; tick < 2400 && !ship.isSearching(); tick++) {
			player.setPosition(player.getPos().add(0, 0, .28)); // Normal sprint: 5.6 blocks/second.
			ship.tick();
		}
		context.assertTrue(ship.isSearching(), "Correr en línea recta debe superar el alcance de la nave");
		context.assertTrue(ship.getPos().distanceTo(start) < 245, "La nave no debe superar dos bloques por segundo");
		cleanEncounter(context, ship, player);
		context.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "shipBehavior")
	public void dyingAndRespawningCancelsTheReturn(TestContext context) {
		StarDestroyerEntity ship = readyShip(context);
		var player = survivalTarget(context, ship);
		player.setPosition(player.getPos().add(0, -4, 0)); ship.tick();
		var state = com.tunombre.stormcraft.world.ShipPursuitState.get(context.getWorld());
		context.assertTrue(state.isPending(ship.getUuid()), "Debe existir un regreso pendiente");
		int deaths = com.tunombre.stormcraft.world.ShipPursuitState.deaths(player);
		player.getStatHandler().setStat(player, net.minecraft.stat.Stats.CUSTOM.getOrCreateStat(net.minecraft.stat.Stats.DEATHS), deaths+1);
		state.tick(context.getWorld(), context.getWorld().getTime()+600);
		context.assertTrue(!state.isPending(ship.getUuid()) && ship.getArrivalTicks() == 0 && ship.getTargetPlayerId() == null,
				"Una muerte debe cancelar el regreso incluso si el jugador ya ha reaparecido vivo");
		context.assertTrue(ship.getDepartureTicks() > 0, "Debe iniciar la retirada al hiperespacio");
		for (int tick = 0; tick < StarDestroyerEntity.ARRIVAL_TICKS; tick++) ship.tick();
		context.assertTrue(ship.isRemoved(), "Debe desaparecer al terminar la animación");
		cleanEncounter(context, ship, player);
		context.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "shipBehavior")
	public void unloadedShipReturnsWithoutDuplicatingTheHull(TestContext context) {
		StarDestroyerEntity ship = readyShip(context);
		var player = survivalTarget(context, ship);
		ship.damage(context.getWorld().getDamageSources().generic(), 12);
		player.setPosition(player.getPos().add(0, -4, 0)); ship.tick();
		var state = com.tunombre.stormcraft.world.ShipPursuitState.get(context.getWorld());
		var oldId = ship.getUuid();
		NbtCompound oldSave = ship.writeNbt(new NbtCompound());
		ship.remove(net.minecraft.entity.Entity.RemovalReason.UNLOADED_TO_CHUNK);
		context.assertTrue(context.getWorld().getEntity(oldId) == null, "Debe simular una nave descargada");
		state.tick(context.getWorld(), context.getWorld().getTime()+600);
		var replacements = context.getWorld().getEntitiesByClass(StarDestroyerEntity.class,
				new Box(player.getPos(), player.getPos()).expand(256), candidate -> player.getUuid().equals(candidate.getTargetPlayerId()));
		context.assertTrue(replacements.size() == 1 && replacements.get(0).getHealth() == 88,
				"Debe volver una única nave con el daño conservado aunque el chunk original se descargue");
		context.assertTrue(state.isRetired(oldId), "La copia guardada anterior debe quedar retirada");
		StarDestroyerEntity reloaded = ModEntities.STAR_DESTROYER.create(context.getWorld());
		reloaded.readNbt(oldSave);
		context.getWorld().spawnEntity(reloaded);
		reloaded.tick();
		context.assertTrue(reloaded.isRemoved(), "Al volver a cargar la zona original no debe duplicarse la nave");
		cleanEncounter(context, replacements.get(0), player);
		context.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "shipBehavior")
	public void shipShootsMobsAndIgnoresTroopers(TestContext context) {
		StarDestroyerEntity ship = readyShip(context);
		ship.refreshPositionAndAngles(ship.getX(), 250, ship.getZ(), 0, 0);
		ship.resetFlightLifetime();
		var zombie = net.minecraft.entity.EntityType.ZOMBIE.create(context.getWorld());
		zombie.setPosition(ship.getX(), 230, ship.getZ()+80);
		zombie.setAiDisabled(true); context.getWorld().spawnEntity(zombie);
		var trooper = ModEntities.STORMTROOPER.create(context.getWorld());
		trooper.setPosition(ship.getX(), 230, ship.getZ()+15);
		trooper.setAiDisabled(true); context.getWorld().spawnEntity(trooper);
		context.assertTrue(StarDestroyerEntity.isEnemyMob(zombie), "Debe atacar a otros mobs");
		context.assertFalse(StarDestroyerEntity.isEnemyMob(trooper), "Los troopers deben ser aliados");
		for (int tick = 0; tick < 40; tick++) ship.tick();
		var bolts = context.getWorld().getEntitiesByClass(com.tunombre.stormcraft.entity.LaserBoltEntity.class,
				ship.getBoundingBox().expand(100), bolt -> bolt.getOwner() == ship);
		context.assertTrue(!bolts.isEmpty(), "La IA debe disparar contra el mob");
		context.assertFalse(ship.isSirenActive(), "Atacar a un mob debe mantener los motores sin activar la sirena");
		context.assertTrue(bolts.get(0).getVelocity().normalize().dotProduct(zombie.getEyePos().subtract(bolts.get(0).getPos()).normalize()) > .99,
				"El disparo debe apuntar al mob y no al trooper cercano");
		bolts.forEach(net.minecraft.entity.Entity::discard);
		ship.discard(); zombie.discard(); trooper.discard();
		context.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "shipBehavior")
	public void groundLasersExplodeWithoutFriendlyFire(TestContext context) {
		context.getWorld().getServer().setDifficulty(net.minecraft.world.Difficulty.NORMAL, true);
		var rules = context.getWorld().getGameRules();
		boolean griefing = rules.getBoolean(net.minecraft.world.GameRules.DO_MOB_GRIEFING);
		rules.get(net.minecraft.world.GameRules.DO_MOB_GRIEFING).set(false, context.getWorld().getServer());
		context.setBlockState(3, 1, 3, net.minecraft.block.Blocks.GLASS);
		var trooper = context.spawnEntity(ModEntities.STORMTROOPER, 4, 2, 3);
		trooper.setAiDisabled(true);
		var zombie = context.spawnEntity(net.minecraft.entity.EntityType.ZOMBIE, 3, 2, 4);
		zombie.setAiDisabled(true);
		float trooperHealth = trooper.getHealth();
		float zombieHealth = zombie.getHealth();
		var owner = ModEntities.STAR_DESTROYER.create(context.getWorld());
		var laser = new com.tunombre.stormcraft.entity.LaserBoltEntity(ModEntities.LASER_BOLT, context.getWorld());
		laser.setOwner(owner);
		laser.setPosition(context.getAbsolute(new Vec3d(3.5, 3, 3.5)));
		laser.setVelocity(0, -2, 0);
		context.getWorld().spawnEntity(laser); laser.tick();
		context.assertTrue(laser.isRemoved() && zombie.getHealth() < zombieHealth, "El impacto contra tierra debe explotar y dañar a los mobs cercanos");
		context.assertTrue(trooper.getHealth() == trooperHealth && trooper.getVelocity().lengthSquared() == 0,
				"La explosión no debe dañar ni empujar a los troopers");
		context.assertTrue(context.getBlockState(new BlockPos(3, 1, 3)).isOf(net.minecraft.block.Blocks.GLASS),
				"mobGriefing=false debe conservar el terreno");
		rules.get(net.minecraft.world.GameRules.DO_MOB_GRIEFING).set(true, context.getWorld().getServer());
		var second = new com.tunombre.stormcraft.entity.LaserBoltEntity(ModEntities.LASER_BOLT, context.getWorld());
		second.setOwner(owner); second.setPosition(context.getAbsolute(new Vec3d(3.5, 3, 3.5))); second.setVelocity(0, -2, 0);
		context.getWorld().spawnEntity(second); second.tick();
		context.assertTrue(context.getBlockState(new BlockPos(3, 1, 3)).isAir(), "Con mobGriefing activo, el impacto debe romper el bloque frágil");
		rules.get(net.minecraft.world.GameRules.DO_MOB_GRIEFING).set(griefing, context.getWorld().getServer());
		trooper.discard(); zombie.discard(); owner.discard();
		context.complete();
	}

	private static StarDestroyerEntity highShip(TestContext context) {
		StarDestroyerEntity ship = readyShip(context);
		ship.refreshPositionAndAngles(ship.getX(), 250, ship.getZ(), 0, 0);
		ship.resetFlightLifetime();
		return ship;
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "shipLifecycle")
	public void helmetStopsAttackAndRemovingItReengages(TestContext context) {
		StarDestroyerEntity ship = highShip(context);
		var player = survivalTarget(context, ship);
		for (int i = 0; i < 20; i++) ship.tick();
		context.assertTrue(ship.isAttacking(), "Sin casco debe estar en modo ataque y activar la sirena");
		player.equipStack(net.minecraft.entity.EquipmentSlot.HEAD, new net.minecraft.item.ItemStack(com.tunombre.stormcraft.item.ModItems.TROOPER_HEAD));
		ship.tick();
		context.assertFalse(ship.isAttacking(), "El casco debe detener el ataque y devolver el sonido a motores");
		float health = player.getHealth();
		var bolt = new com.tunombre.stormcraft.entity.LaserBoltEntity(ModEntities.LASER_BOLT, context.getWorld());
		bolt.setOwner(ship); bolt.setPosition(player.getPos().add(0, .8, -2)); bolt.setVelocity(0, 0, 3);
		context.getWorld().spawnEntity(bolt); bolt.tick();
		context.getWorld().createExplosion(bolt, player.getX(), player.getY(), player.getZ(), 1.5F, false, net.minecraft.world.World.ExplosionSourceType.MOB);
		context.assertTrue(player.getHealth() == health, "El casco debe proteger también de láseres ya disparados y sus explosiones");
		bolt.discard();
		player.equipStack(net.minecraft.entity.EquipmentSlot.HEAD, net.minecraft.item.ItemStack.EMPTY);
		ship.tick();
		context.assertTrue(ship.isAttacking() && !ship.isSearching(), "Quitarse el casco dentro del alcance debe reactivar el ataque");
		context.getWorld().createExplosion(bolt, player.getX(), player.getY(), player.getZ(), .5F, false, net.minecraft.world.World.ExplosionSourceType.MOB);
		context.assertTrue(player.getHealth() < health, "Sin el casco la misma fuente de explosión debe poder dañar al jugador");
		cleanEncounter(context, ship, player);
		context.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "shipLifecycle")
	public void returningToAttackRangeCancelsTheSearchTimer(TestContext context) {
		StarDestroyerEntity ship = highShip(context);
		var player = survivalTarget(context, ship);
		Vec3d initial = player.getPos();
		player.setPosition(initial.add(0, 0, 500)); ship.tick();
		context.assertTrue(ship.isSearching(), "Fuera del alcance debe buscar al jugador");
		player.setPosition(initial); ship.tick();
		context.assertTrue(ship.isAttacking() && !ship.isSearching(), "Al volver al alcance y estar visible debe atacar inmediatamente");
		context.assertFalse(com.tunombre.stormcraft.world.ShipPursuitState.get(context.getWorld()).isPending(ship.getUuid()),
				"Retomar el combate debe cancelar el regreso programado");
		cleanEncounter(context, ship, player);
		context.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "shipLifecycle")
	public void singletonBlocksCommandsSummonsAndUnloadedDuplicates(TestContext context) {
		StarDestroyerEntity ship = highShip(context);
		var player = survivalTarget(context, ship);
		context.assertFalse(StarDestroyerSpawner.spawnEncounter(context.getWorld(), player), "El comando no debe crear una segunda nave");
		var duplicate = ModEntities.STAR_DESTROYER.create(context.getWorld());
		duplicate.setPosition(ship.getPos()); context.getWorld().spawnEntity(duplicate);
		context.assertTrue(duplicate.isRemoved(), "Una invocación directa también debe rechazar la segunda nave");
		ship.remove(net.minecraft.entity.Entity.RemovalReason.UNLOADED_TO_CHUNK);
		context.assertFalse(StarDestroyerSpawner.spawnEncounter(context.getWorld(), player), "Descargar el chunk no debe liberar el cupo para otra nave");
		var saved = com.tunombre.stormcraft.world.ShipLifecycleState.get(context.getWorld()).writeNbt(new NbtCompound());
		context.assertTrue(ship.getUuid().equals(com.tunombre.stormcraft.world.ShipLifecycleState.fromNbt(saved).getActiveShip()),
				"La reserva de la única nave debe sobrevivir a guardar y cargar");
		player.equipStack(net.minecraft.entity.EquipmentSlot.HEAD, new net.minecraft.item.ItemStack(com.tunombre.stormcraft.item.ModItems.TROOPER_HEAD));
		com.tunombre.stormcraft.world.ShipPursuitState.get(context.getWorld()).tick(context.getWorld(), context.getWorld().getTime());
		context.assertTrue(com.tunombre.stormcraft.world.ShipLifecycleState.get(context.getWorld()).getActiveShip() == null,
				"El casco debe cancelar un regreso descargado sin dejar bloqueado el cupo de la nave");
		context.assertTrue(com.tunombre.stormcraft.world.ShipPursuitState.get(context.getWorld()).isRetired(ship.getUuid()),
				"La nave anterior debe retirarse para no duplicarse al cargar su zona");
		cleanEncounter(context, ship, player);
		context.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "shipLifecycle")
	public void destroyedShipCannotReturnUntilTheNextNight(TestContext context) {
		long originalTime = context.getWorld().getTimeOfDay();
		context.getWorld().setTimeOfDay(61000);
		StarDestroyerEntity ship = highShip(context);
		var player = survivalTarget(context, ship);
		ship.damage(context.getWorld().getDamageSources().generic(), 100);
		var state = com.tunombre.stormcraft.world.ShipLifecycleState.get(context.getWorld());
		context.assertTrue(ship.isRemoved() && state.getBlockedUntil() == 85000, "Destruirla debe bloquear el resto de esa noche y el día siguiente");
		context.assertFalse(StarDestroyerSpawner.spawnEncounter(context.getWorld(), player), "Tampoco debe reaparecer mediante el comando");
		for (int i = 0; i < 6; i++) StarDestroyerSpawner.onTrooperKilled(context.getWorld(), player);
		context.assertTrue(state.getActiveShip() == null, "Matar seis troopers no debe saltarse el bloqueo tras destruirla");
		var restored = com.tunombre.stormcraft.world.ShipLifecycleState.fromNbt(state.writeNbt(new NbtCompound()));
		context.assertFalse(restored.canSpawn(84999), "El bloqueo debe persistir después de guardar");
		context.assertTrue(restored.shouldAppearTonight(85000), "Debe poder volver en la noche siguiente");
		context.getWorld().setTimeOfDay(85000);
		context.assertTrue(StarDestroyerSpawner.spawnEncounter(context.getWorld(), player), "La noche siguiente debe permitir una nueva nave");
		var replacement = (StarDestroyerEntity) context.getWorld().getEntity(state.getActiveShip());
		cleanEncounter(context, replacement, player);
		context.getWorld().setTimeOfDay(originalTime);
		context.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "shipLifecycle")
	public void sixthActualTrooperKillCallsTheOnlyShip(TestContext context) {
		long originalTime = context.getWorld().getTimeOfDay();
		context.getWorld().setTimeOfDay(1000);
		var reference = ModEntities.STAR_DESTROYER.create(context.getWorld());
		reference.setPosition(context.getAbsolute(new Vec3d(3, 100, 3)));
		var player = survivalTarget(context, reference);
		var state = com.tunombre.stormcraft.world.ShipLifecycleState.get(context.getWorld());
		for (int i = 0; i < 6; i++) {
			var trooper = context.spawnEntity(ModEntities.STORMTROOPER, 3, 1, 3);
			trooper.damage(context.getWorld().getDamageSources().playerAttack(player), 1000);
			if (i < 5) context.assertTrue(state.getActiveShip() == null, "Cinco bajas todavía no deben llamar al destructor");
			trooper.discard();
		}
		context.assertTrue(state.getActiveShip() != null, "La sexta baja real debe llamar al destructor incluso de día");
		var ship = (StarDestroyerEntity) context.getWorld().getEntity(state.getActiveShip());
		context.assertTrue(ship != null && ship.getArrivalTicks() > 0, "Debe llegar una nave desde el hiperespacio");
		cleanEncounter(context, ship, player);
		context.getWorld().setTimeOfDay(originalTime);
		context.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "shipLifecycle")
	public void victoryDepartsAndSchedulesTheFollowingNight(TestContext context) {
		long originalTime = context.getWorld().getTimeOfDay();
		context.getWorld().setTimeOfDay(61000);
		StarDestroyerEntity ship = highShip(context);
		var player = survivalTarget(context, ship);
		ship.abandonPursuit(); // Also cover a collateral player kill while the ship was attacking another mob.
		var killingLaser = new com.tunombre.stormcraft.entity.LaserBoltEntity(ModEntities.LASER_BOLT, context.getWorld());
		killingLaser.setOwner(ship);
		player.damage(context.getWorld().getDamageSources().thrown(killingLaser, ship), 1000);
		context.assertTrue(ship.getDepartureTicks() == StarDestroyerEntity.ARRIVAL_TICKS && !ship.isAttacking(), "La retirada debe animarse sin sirena ni ataques");
		for (int tick = 0; tick < StarDestroyerEntity.ARRIVAL_TICKS; tick++) ship.tick();
		var state = com.tunombre.stormcraft.world.ShipLifecycleState.get(context.getWorld());
		context.assertTrue(ship.isRemoved() && state.getActiveShip() == null, "Al acabar el salto debe irse del mundo");
		context.assertFalse(state.shouldAppearTonight(70000), "No debe regresar esa misma noche");
		context.assertTrue(state.shouldAppearTonight(85000), "Debe volver la siguiente noche");
		cleanEncounter(context, ship, player);
		context.getWorld().setTimeOfDay(originalTime);
		context.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "shipFocus")
	public void playerPriorityAndAlarmSendNearbyTroopersAfterThePlayer(TestContext context) {
		StarDestroyerEntity ship = highShip(context);
		var player = survivalTarget(context, ship);
		int x = net.minecraft.util.math.MathHelper.floor(ship.getX());
		int z = net.minecraft.util.math.MathHelper.floor(ship.getZ());
		int floor = net.minecraft.util.math.MathHelper.floor(player.getY())-1;
		for (int dx = -3; dx <= 3; dx++) for (int dz = 80; dz <= 130; dz++) {
			context.getWorld().setBlockState(new BlockPos(x+dx, floor, z+dz), net.minecraft.block.Blocks.STONE.getDefaultState());
		}
		var cow = net.minecraft.entity.EntityType.COW.create(context.getWorld());
		cow.setPosition(ship.getX(), player.getY(), ship.getZ()+80); cow.setAiDisabled(true);
		context.getWorld().spawnEntity(cow);
		var trooper = ModEntities.STORMTROOPER.create(context.getWorld());
		trooper.setPosition(ship.getX(), player.getY(), ship.getZ()+85); trooper.setOnGround(true);
		context.getWorld().spawnEntity(trooper);
		context.assertTrue(trooper.squaredDistanceTo(player) > 32*32, "La prueba debe superar el alcance habitual del trooper");
		for (int tick = 0; tick < 20; tick++) ship.tick();
		var bolts = context.getWorld().getEntitiesByClass(com.tunombre.stormcraft.entity.LaserBoltEntity.class,
				ship.getBoundingBox().expand(160), bolt -> bolt.getOwner() == ship);
		context.assertTrue(!bolts.isEmpty(), "Debe disparar al jugador aunque haya otro mob más cerca");
		context.assertTrue(bolts.get(0).getVelocity().normalize().dotProduct(player.getEyePos().subtract(bolts.get(0).getPos()).normalize()) > .99,
				"El disparo debe hacer focus al jugador y no a la vaca cercana");
		context.assertTrue(ship.isSirenActive() && trooper.getTarget() == player, "La sirena debe alertar a los troopers cercanos");
		double initialDistance = trooper.squaredDistanceTo(player);
		for (int tick = 0; tick < 40; tick++) trooper.tick();
		context.assertTrue(trooper.getTarget() == player && trooper.squaredDistanceTo(player) < initialDistance-1,
				"El trooper debe conservar la orden y acercarse al jugador fuera de su alcance habitual");
		player.equipStack(net.minecraft.entity.EquipmentSlot.HEAD, new net.minecraft.item.ItemStack(com.tunombre.stormcraft.item.ModItems.TROOPER_HEAD));
		ship.tick(); trooper.tick();
		context.assertTrue(!ship.isSirenActive() && trooper.getTarget() != player, "El casco debe apagar la alarma y cancelar la persecución del trooper");
		cow.discard(); trooper.discard();
		for (int dx = -3; dx <= 3; dx++) for (int dz = 80; dz <= 130; dz++) {
			context.getWorld().setBlockState(new BlockPos(x+dx, floor, z+dz), net.minecraft.block.Blocks.AIR.getDefaultState());
		}
		cleanEncounter(context, ship, player);
		context.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "shipFocus")
	public void losingSightOfThePlayerSilencesTheAlarmImmediately(TestContext context) {
		StarDestroyerEntity ship = highShip(context);
		var player = survivalTarget(context, ship);
		ship.tick();
		context.assertTrue(ship.isSirenActive(), "Un jugador visible debe activar la sirena");
		BlockPos feet = player.getBlockPos();
		for (int dx = -2; dx <= 2; dx++) for (int dy = 0; dy <= 12; dy++) {
			context.getWorld().setBlockState(feet.add(dx, dy, -1), net.minecraft.block.Blocks.STONE.getDefaultState());
		}
		ship.tick();
		context.assertFalse(ship.isSirenActive(), "Al perder de vista al jugador debe sonar solo el motor, sin esperar el temporizador de búsqueda");
		for (int dx = -2; dx <= 2; dx++) for (int dy = 0; dy <= 12; dy++) {
			context.getWorld().setBlockState(feet.add(dx, dy, -1), net.minecraft.block.Blocks.AIR.getDefaultState());
		}
		cleanEncounter(context, ship, player);
		context.complete();
	}

	private static void raiseShield(net.minecraft.server.network.ServerPlayerEntity player) {
		player.setStackInHand(net.minecraft.util.Hand.OFF_HAND, new net.minecraft.item.ItemStack(net.minecraft.item.Items.SHIELD));
		player.setYaw(0); player.setPitch(0);
		player.setVelocity(Vec3d.ZERO);
		player.setCurrentHand(net.minecraft.util.Hand.OFF_HAND);
		for (int tick = 0; tick < 8; tick++) player.playerTick();
	}

	private static void fireTestLaser(TestContext context, net.minecraft.entity.Entity owner, net.minecraft.server.network.ServerPlayerEntity player) {
		var laser = new com.tunombre.stormcraft.entity.LaserBoltEntity(ModEntities.LASER_BOLT, context.getWorld());
		laser.setOwner(owner);
		laser.setPosition(player.getPos().add(0, .8, 2)); laser.setVelocity(0, 0, -3);
		context.getWorld().spawnEntity(laser); laser.tick(); laser.discard();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "shipWeapons")
	public void shieldsBlockEveryLaserOriginAndWearMoreThanAnArrow(TestContext context) {
		boolean pvp = context.getWorld().getServer().isPvpEnabled();
		context.getWorld().getServer().setPvpEnabled(true);
		StarDestroyerEntity ship = highShip(context);
		var player = survivalTarget(context, ship);
		raiseShield(player);
		context.assertTrue(player.isBlocking(), "El escudo debe haber terminado su preparación");
		float health = player.getHealth();
		var arrow = new net.minecraft.entity.projectile.ArrowEntity(context.getWorld(), player);
		arrow.setPosition(player.getPos().add(0, .8, 2));
		player.damage(context.getWorld().getDamageSources().arrow(arrow, ship), 6);
		int arrowWear = player.getOffHandStack().getDamage();
		context.assertTrue(arrowWear > 0 && player.getHealth() == health, "La flecha de referencia debe bloquearse");
		raiseShield(player);
		var trooper = ModEntities.STORMTROOPER.create(context.getWorld());
		var shooter = context.createMockSurvivalPlayer();
		for (var owner : new net.minecraft.entity.Entity[]{ship, trooper, shooter}) {
			int before = player.getOffHandStack().getDamage();
			fireTestLaser(context, owner, player);
			context.assertTrue(player.getHealth() == health, "Todo láser debe bloquearse sin quitar vida: "+owner.getType());
			context.assertTrue(player.getOffHandStack().getDamage()-before > arrowWear,
					"Cada láser debe gastar más escudo que la flecha: "+owner.getType());
		}
		player.clearActiveItem();
		fireTestLaser(context, ship, player);
		context.assertTrue(player.getHealth() < health, "Sin levantar el escudo el láser debe seguir dañando al jugador");
		cleanEncounter(context, ship, player); trooper.discard(); arrow.discard();
		context.getWorld().getServer().setPvpEnabled(pvp);
		context.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "shipWeapons")
	public void aNearlyBrokenShieldStopsTheLaserThenBreaks(TestContext context) {
		StarDestroyerEntity ship = highShip(context);
		var player = survivalTarget(context, ship);
		raiseShield(player);
		float health = player.getHealth();
		player.getOffHandStack().setDamage(player.getOffHandStack().getMaxDamage()-1);
		fireTestLaser(context, ship, player);
		context.assertTrue(player.getHealth() == health && player.getOffHandStack().isEmpty(),
				"El último bloqueo debe parar el láser y romper el escudo");
		cleanEncounter(context, ship, player);
		context.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "shipWeapons")
	public void directionalSensorsCoverTheUndersideAndLongForwardRange(TestContext context) {
		StarDestroyerEntity ship = highShip(context);
		Vec3d center = ship.getPos();
		context.assertTrue(ship.canDetectPosition(center.add(0, -30, 500)), "Debe detectar a larga distancia delante");
		context.assertTrue(ship.canDetectPosition(center.add(45, -310, -60)), "Debe cubrir toda la zona inferior del casco");
		context.assertTrue(ship.canDetectPosition(center.add(120, -20, -10)), "Debe conservar detección lateral reducida");
		context.assertFalse(ship.canDetectPosition(center.add(0, -20, -270)), "La detección trasera debe ser más corta");
		context.assertFalse(ship.canDetectPosition(center.add(0, -330, 0)), "El alcance inferior debe ser finito");
		ship.setYaw(90);
		context.assertTrue(ship.canDetectPosition(center.add(-500, -30, 0)), "El sensor frontal debe girar con la nave");
		ship.discard(); context.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "shipWeapons")
	public void turnsAccelerateSlowlyWithoutSnappingTowardThePlayer(TestContext context) {
		StarDestroyerEntity ship = highShip(context);
		var player = survivalTarget(context, ship);
		player.setPosition(ship.getPos().add(100, -30, 100));
		ship.tick();
		float first = Math.abs(ship.getTurnVelocity());
		context.assertTrue(first > 0 && first <= StarDestroyerEntity.TURN_ACCELERATION+.001F,
				"El giro debe empezar con aceleración gradual");
		float initialYaw = ship.getYaw();
		for (int tick = 0; tick < 19; tick++) ship.tick();
		context.assertTrue(Math.abs(ship.getTurnVelocity()) <= StarDestroyerEntity.MAX_TURN_RATE+.001F
				&& Math.abs(net.minecraft.util.math.MathHelper.wrapDegrees(ship.getYaw()-initialYaw)) < 5,
				"Una nave enorme no debe girar decenas de grados en un segundo");
		cleanEncounter(context, ship, player); context.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "shipWeapons")
	public void attackFiresTwoParallelPairsSeparatedBySixTicks(TestContext context) {
		StarDestroyerEntity ship = highShip(context);
		var player = survivalTarget(context, ship);
		for (int tick = 0; tick < 20; tick++) ship.tick();
		var first = context.getWorld().getEntitiesByClass(com.tunombre.stormcraft.entity.LaserBoltEntity.class,
				ship.getBoundingBox().expand(400), bolt -> bolt.getOwner() == ship);
		context.assertTrue(first.size() == 2, "La primera ráfaga debe contener dos láseres");
		context.assertTrue(first.get(0).getVelocity().squaredDistanceTo(first.get(1).getVelocity()) < .000001
				&& Math.abs(first.get(0).getPos().distanceTo(first.get(1).getPos())-StarDestroyerEntity.CANNON_SPACING) < .001,
				"Cada pareja debe viajar en paralelo desde cañones separados");
		for (int tick = 0; tick < 5; tick++) ship.tick();
		context.assertTrue(context.getWorld().getEntitiesByClass(com.tunombre.stormcraft.entity.LaserBoltEntity.class,
				ship.getBoundingBox().expand(400), bolt -> bolt.getOwner() == ship).size() == 2, "La segunda pareja no debe salir antes de tiempo");
		ship.tick();
		context.assertTrue(context.getWorld().getEntitiesByClass(com.tunombre.stormcraft.entity.LaserBoltEntity.class,
				ship.getBoundingBox().expand(400), bolt -> bolt.getOwner() == ship).size() == 4, "Deben salir cuatro láseres en total");
		cleanEncounter(context, ship, player); context.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "ship110")
	public void survivalPlayerIsPursuedWithoutProvokingTheShip(TestContext context) {
		StarDestroyerEntity ship = highShip(context);
		var reference = ModEntities.STAR_DESTROYER.create(context.getWorld());
		reference.setPosition(ship.getPos());
		var survivor = survivalTarget(context, reference);
		survivor.setPosition(ship.getPos().add(220, -30, -100));
		var creative = survivalTarget(context, reference);
		creative.setPosition(ship.getPos().add(0, -30, 100));
		creative.changeGameMode(net.minecraft.world.GameMode.CREATIVE);
		context.assertTrue(ship.getTargetPlayerId() == null, "La nave debe comenzar sin un objetivo ni provocación");
		ship.tick();
		context.assertTrue(survivor.getUuid().equals(ship.getTargetPlayerId()) && ship.isSirenActive(),
				"Debe localizar por sí sola al jugador en supervivencia y omitir al jugador creativo más cercano");
		context.assertTrue(Math.abs(ship.getTurnVelocity()) > 0, "Debe empezar a orientarse hacia el superviviente sin recibir daño");
		cleanEncounter(context, ship, survivor);
		context.getWorld().getServer().getPlayerManager().remove(creative);
		reference.discard(); context.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "ship110")
	public void firingWhileDisguisedExposesThePlayerUntilDeath(TestContext context) {
		StarDestroyerEntity ship = highShip(context);
		var player = survivalTarget(context, ship);
		player.equipStack(net.minecraft.entity.EquipmentSlot.HEAD, new net.minecraft.item.ItemStack(com.tunombre.stormcraft.item.ModItems.TROOPER_HEAD));
		context.assertTrue(com.tunombre.stormcraft.item.TrooperHeadItem.isDisguised(player), "Antes de disparar el casco debe proteger");
		var shot = new com.tunombre.stormcraft.entity.LaserBoltEntity(context.getWorld(), player);
		shot.setPosition(ship.getPos().add(0, 5, 90)); shot.setVelocity(0, 0, -12);
		context.getWorld().getChunk(shot.getBlockX() >> 4, shot.getBlockZ() >> 4);
		context.getWorld().spawnEntity(shot); shot.tick();
		context.assertTrue(ship.getHealth() < 100 && !com.tunombre.stormcraft.item.TrooperHeadItem.isDisguised(player),
				"Acertar a la nave llevando el casco debe descubrir el disfraz");
		player.equipStack(net.minecraft.entity.EquipmentSlot.HEAD, net.minecraft.item.ItemStack.EMPTY);
		player.equipStack(net.minecraft.entity.EquipmentSlot.HEAD, new net.minecraft.item.ItemStack(com.tunombre.stormcraft.item.ModItems.TROOPER_HEAD));
		context.assertFalse(com.tunombre.stormcraft.item.TrooperHeadItem.isDisguised(player), "Volver a ponerse el casco no debe borrar la identificación");
		ship.tick();
		context.assertTrue(ship.isAttacking() && ship.isSirenActive() && StormtrooperEntity.isEnemy(player),
				"La nave y los troopers deben reconocer al atacante aunque lleve el casco");
		var saved = com.tunombre.stormcraft.world.ImperialHostilityState.get(context.getWorld()).writeNbt(new NbtCompound());
		context.assertTrue(com.tunombre.stormcraft.world.ImperialHostilityState.fromNbt(saved).isIdentified(player),
				"Guardar y cargar no debe permitir recuperar el disfraz");
		int deaths = com.tunombre.stormcraft.world.ShipPursuitState.deaths(player);
		player.getStatHandler().setStat(player, net.minecraft.stat.Stats.CUSTOM.getOrCreateStat(net.minecraft.stat.Stats.DEATHS), deaths+1);
		context.assertTrue(com.tunombre.stormcraft.item.TrooperHeadItem.isDisguised(player), "Una muerte debe permitir usar un nuevo disfraz");
		cleanEncounter(context, ship, player); shot.discard(); context.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "ship110")
	public void maskedShotRemembersTheHelmetAfterEquipmentAndNbtChanges(TestContext context) {
		StarDestroyerEntity ship = highShip(context);
		var player = survivalTarget(context, ship);
		player.equipStack(net.minecraft.entity.EquipmentSlot.HEAD, new net.minecraft.item.ItemStack(com.tunombre.stormcraft.item.ModItems.TROOPER_HEAD));
		var original = new com.tunombre.stormcraft.entity.LaserBoltEntity(context.getWorld(), player);
		NbtCompound saved = original.writeNbt(new NbtCompound());
		var shot = ModEntities.LASER_BOLT.create(context.getWorld()); shot.readNbt(saved);
		player.equipStack(net.minecraft.entity.EquipmentSlot.HEAD, net.minecraft.item.ItemStack.EMPTY);
		context.assertTrue(shot.wasFiredWithTrooperHead(), "El proyectil debe recordar el casco que llevaba el atacante al disparar");
		shot.setPosition(ship.getPos().add(0, 5, 90)); shot.setVelocity(0, 0, -12);
		context.getWorld().getChunk(shot.getBlockX() >> 4, shot.getBlockZ() >> 4);
		context.getWorld().spawnEntity(shot); shot.tick();
		context.assertTrue(com.tunombre.stormcraft.world.ImperialHostilityState.get(context.getWorld()).isIdentified(player),
				"Quitarse el casco mientras el láser viaja no debe impedir la identificación");
		cleanEncounter(context, ship, player); original.discard(); shot.discard(); context.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "ship110")
	public void shipAndTroopersOnlyTargetGroundMobsAndDrowned(TestContext context) {
		for (var type : new net.minecraft.entity.EntityType<?>[]{net.minecraft.entity.EntityType.ZOMBIE,
				net.minecraft.entity.EntityType.COW, net.minecraft.entity.EntityType.CHICKEN, net.minecraft.entity.EntityType.DROWNED}) {
			var mob = (net.minecraft.entity.LivingEntity) type.create(context.getWorld());
			context.assertTrue(StarDestroyerEntity.isEnemyMob(mob) && StormtrooperEntity.isEnemy(mob), "Deben atacar a terrestres y ahogados: "+type);
		}
		for (var type : new net.minecraft.entity.EntityType<?>[]{net.minecraft.entity.EntityType.BAT, net.minecraft.entity.EntityType.BEE,
				net.minecraft.entity.EntityType.PARROT, net.minecraft.entity.EntityType.ALLAY, net.minecraft.entity.EntityType.GHAST,
				net.minecraft.entity.EntityType.PHANTOM, net.minecraft.entity.EntityType.VEX, net.minecraft.entity.EntityType.WITHER,
				net.minecraft.entity.EntityType.COD, net.minecraft.entity.EntityType.SQUID, net.minecraft.entity.EntityType.DOLPHIN,
				net.minecraft.entity.EntityType.AXOLOTL, ModEntities.STORMTROOPER}) {
			var mob = (net.minecraft.entity.LivingEntity) type.create(context.getWorld());
			context.assertFalse(StarDestroyerEntity.isEnemyMob(mob) || StormtrooperEntity.isEnemy(mob), "No deben atacar a voladores, acuáticos o troopers: "+type);
		}
		context.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "ship110")
	public void hyperspaceRemovesTheHullAndRejectsAnOldSavedCopy(TestContext context) {
		StarDestroyerEntity ship = highShip(context);
		var id = ship.getUuid();
		NbtCompound oldSave = ship.writeNbt(new NbtCompound());
		ship.beginDeparture();
		for (int tick = 0; tick < StarDestroyerEntity.ARRIVAL_TICKS; tick++) ship.tick();
		context.assertTrue(ship.isRemoved() && context.getWorld().getEntity(id) == null,
				"Después de la animación no debe quedar ninguna nave en el mapa del servidor");
		context.assertTrue(com.tunombre.stormcraft.world.ShipPursuitState.get(context.getWorld()).isRetired(id), "El identificador debe quedar retirado");
		var oldCopy = ModEntities.STAR_DESTROYER.create(context.getWorld()); oldCopy.readNbt(oldSave);
		context.getWorld().spawnEntity(oldCopy);
		context.assertTrue(oldCopy.isRemoved() && context.getWorld().getEntity(id) == null, "Una copia guardada no debe resucitar la nave al cargar el chunk");
		context.complete();
	}
}
