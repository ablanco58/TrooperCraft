package com.tunombre.stormcraft;

import com.tunombre.stormcraft.entity.LaserBoltEntity;
import com.tunombre.stormcraft.entity.ModEntities;
import com.tunombre.stormcraft.entity.StormtrooperEntity;
import com.tunombre.stormcraft.entity.ai.PatrolMarchGoal;
import com.tunombre.stormcraft.item.ModItems;
import com.tunombre.stormcraft.world.PatrolSpawner;
import com.tunombre.stormcraft.world.PatrolProgression;
import com.tunombre.stormcraft.config.PatrolConfig;
import com.tunombre.stormcraft.sound.TrooperSpeech;
import com.tunombre.stormcraft.sound.TrooperVoices;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvent;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;

public class StormtrooperGameTests implements FabricGameTest {
	private static void floor(TestContext context) {
		context.getWorld().getServer().setDifficulty(Difficulty.NORMAL, true);
		context.getWorld().getGameRules().get(GameRules.DO_MOB_SPAWNING).set(false, context.getWorld().getServer());
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) context.setBlockState(x, 0, z, Blocks.STONE);
		}
	}

	private static StormtrooperEntity trooper(TestContext context, int x, int z) {
		// spawnMob() clears goals permanently; spawnEntity() preserves the actual production AI.
		StormtrooperEntity trooper = context.spawnEntity(ModEntities.STORMTROOPER, x, 1, z);
		trooper.setAiDisabled(true);
		trooper.setOnGround(true);
		return trooper;
	}

	private static List<StormtrooperEntity> patrolAt(TestContext context, BlockPos origin) {
		return context.getWorld().getEntitiesByClass(StormtrooperEntity.class, new Box(origin).expand(5), Entity::isAlive);
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void patrolPressureGrowsByNightAndStopsAtThird(TestContext context) {
		PatrolConfig config = new PatrolConfig();
		var first = PatrolProgression.forTime(13000, config);
		var second = PatrolProgression.forTime(37000, config);
		var third = PatrolProgression.forTime(61000, config);
		context.assertTrue(first.perPlayerCap() == 6 && second.perPlayerCap() == 12 && third.perPlayerCap() == 24,
				"La población debe pasar de pocas patrullas a una presión alta pero limitada");
		context.assertTrue(first.intervalSeconds() > second.intervalSeconds()
				&& second.intervalSeconds() > third.intervalSeconds()
				&& first.chance() < second.chance() && second.chance() < third.chance(),
				"Las búsquedas deben aumentar en frecuencia y probabilidad cada noche");
		context.assertTrue(PatrolProgression.forTime(0, config).equals(first)
				&& PatrolProgression.forTime(36999, config).equals(first)
				&& PatrolProgression.forTime(60999, config).equals(second),
				"El día inicial debe ser tranquilo y cada etapa debe cambiar al comenzar la noche siguiente");
		context.assertTrue(PatrolProgression.forTime(24000L * 100 + 13000, config).equals(third),
				"La dificultad no debe seguir aumentando después de la tercera noche");
		context.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void progressionRespectsConfiguredPopulationCeilings(TestContext context) {
		PatrolConfig config = new PatrolConfig();
		config.maxTroopersPerPlayer = 5;
		config.maxTroopersPerWorld = 10;
		config.patrolsPerCycle = 1;
		config.spawnChance = 0;
		for (long time : new long[] {13000, 37000, 61000}) {
			var profile = PatrolProgression.forTime(time, config);
			context.assertTrue(profile.perPlayerCap() == 5 && profile.worldCap() == 10
					&& profile.patrolsPerCycle() == 1 && profile.chance() == 0,
					"La progresión debe respetar los límites bajos y la desactivación de aparición configurados");
		}
		context.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void healthEquipmentAndTargets(TestContext context) {
		floor(context);
		StormtrooperEntity trooper = trooper(context, 2, 2);
		StormtrooperEntity ally = trooper(context, 3, 2);
		context.assertTrue(trooper.getMaxHealth() == 20 && trooper.getHealth() == 20, "Debe tener 20 puntos de vida");
		context.assertTrue(trooper.getMainHandStack().isOf(ModItems.BLASTER), "Debe llevar el bláster");
		context.assertTrue(trooper.isTeammate(ally), "Todos los troopers deben ser aliados");
		trooper.setTarget(ally);
		context.assertTrue(trooper.getTarget() == null, "No puede seleccionar a un aliado");
		context.assertFalse(trooper.damage(context.getWorld().getDamageSources().mobAttack(ally), 3), "No debe recibir fuego amigo");
		context.assertFalse(StormtrooperEntity.isEnemy(context.createMockCreativePlayer()), "Creativo no debe ser un objetivo");
		context.assertTrue(StormtrooperEntity.isEnemy(context.createMockSurvivalPlayer()), "Supervivencia sí debe ser un objetivo");
		var player = context.createMockSurvivalPlayer();
		player.damage(context.getWorld().getDamageSources().mobProjectile(new LaserBoltEntity(context.getWorld(), trooper), trooper), 3);
		context.assertTrue(player.getHealth() == 17, "El daño contra un jugador sin armadura debe ser 3 en Normal");
		context.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void completePatrolsAndSavedFormation(TestContext context) {
		floor(context);
		BlockPos origin = context.getAbsolutePos(new BlockPos(3, 1, 3));
		for (int size = 3; size <= 5; size++) {
			context.assertTrue(PatrolSpawner.spawnPatrol(context.getWorld(), origin, size, false) == size, "La patrulla debe aparecer completa");
			List<StormtrooperEntity> patrol = patrolAt(context, origin);
			context.assertTrue(patrol.size() == size, "El número de miembros debe coincidir");
			UUID squad = patrol.get(0).getPatrolId();
			context.assertTrue(patrol.stream().allMatch(member -> member.getPatrolId().equals(squad)), "Deben compartir patrulla");
			context.assertTrue(patrol.stream().map(StormtrooperEntity::getFormationSlot).distinct().count() == size, "Cada miembro necesita una posición distinta");
			StormtrooperEntity member = patrol.get(size - 1);
			NbtCompound nbt = new NbtCompound();
			member.writeCustomDataToNbt(nbt);
			StormtrooperEntity restored = ModEntities.STORMTROOPER.create(context.getWorld());
			restored.readCustomDataFromNbt(nbt);
			context.assertTrue(restored.getPatrolId().equals(squad)
					&& restored.getFormationSlot() == member.getFormationSlot(), "La formación debe sobrevivir al guardado");
			patrol.forEach(Entity::discard);
		}
		context.assertTrue(PatrolSpawner.spawnPatrol(context.getWorld(), origin, 2, false) == 0, "No se permiten patrullas de 2");
		context.assertTrue(PatrolSpawner.spawnPatrol(context.getWorld(), origin, 6, false) == 0, "No se permiten patrullas de 6");
		context.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void noPartialPatrolWithoutSpace(TestContext context) {
		floor(context);
		BlockPos origin = context.getAbsolutePos(new BlockPos(3, 22, 3));
		context.getWorld().setBlockState(origin.down(), Blocks.STONE.getDefaultState());
		context.assertTrue(PatrolSpawner.spawnPatrol(context.getWorld(), origin, 5, false) == 0, "Sin espacio no debe crear una patrulla parcial");
		context.assertTrue(patrolAt(context, origin).isEmpty(), "No deben quedar miembros sueltos");
		context.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void roofedCaveAndSpawnRules(TestContext context) {
		floor(context);
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) context.setBlockState(x, 4, z, Blocks.STONE);
		}
		BlockPos origin = context.getAbsolutePos(new BlockPos(3, 1, 3));
		context.assertTrue(PatrolSpawner.isSafePosition(context.getWorld(), origin), "El suelo de una cueva debe ser válido");
		context.assertFalse(PatrolSpawner.canSpawnNaturally(context.getWorld()), "doMobSpawning=false debe bloquear la aparición");
		context.getWorld().getGameRules().get(GameRules.DO_MOB_SPAWNING).set(true, context.getWorld().getServer());
		context.assertTrue(PatrolSpawner.canSpawnNaturally(context.getWorld()), "Debe permitir aparecer en el Overworld normal");
		context.assertFalse(PatrolSpawner.canSpawnNaturally(context.getWorld().getServer().getWorld(World.NETHER)), "No debe aparecer en el Nether");
		context.assertFalse(PatrolSpawner.canSpawnNaturally(context.getWorld().getServer().getWorld(World.END)), "No debe aparecer en el End");
		context.assertTrue(PatrolSpawner.spawnPatrol(context.getWorld(), origin, 5, true) == 5, "Debe aparecer una patrulla completa bajo techo");
		context.getWorld().getGameRules().get(GameRules.DO_MOB_SPAWNING).set(false, context.getWorld().getServer());
		context.setBlockState(6, 0, 6, Blocks.MAGMA_BLOCK);
		context.assertFalse(PatrolSpawner.isSafePosition(context.getWorld(), context.getAbsolutePos(new BlockPos(6, 1, 6))), "No debe aparecer sobre magma");
		context.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void naturalPatrolsSpawnInDaylightAndAtNight(TestContext context) {
		floor(context);
		BlockPos origin = context.getAbsolutePos(new BlockPos(3, 1, 3));
		long previousTime = context.getWorld().getTimeOfDay();
		context.getWorld().getGameRules().get(GameRules.DO_MOB_SPAWNING).set(true, context.getWorld().getServer());
		try {
			for (int time : new int[] {6000, 18000}) {
				context.getWorld().setTimeOfDay(time);
				context.assertTrue(PatrolSpawner.canSpawnNaturally(context.getWorld()), "La hora no debe bloquear la aparición");
				context.assertTrue(PatrolSpawner.spawnPatrol(context.getWorld(), origin, 5, true) == 5,
						"Debe aparecer una patrulla natural completa tanto de día como de noche");
				patrolAt(context, origin).forEach(Entity::discard);
			}
		} finally {
			context.getWorld().setTimeOfDay(previousTime);
			context.getWorld().getGameRules().get(GameRules.DO_MOB_SPAWNING).set(false, context.getWorld().getServer());
		}
		context.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void searchesSurfaceAndDeepCavesNearPlayer(TestContext context) {
		floor(context);
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) context.setBlockState(x, 4, z, Blocks.STONE);
		}
		BlockPos above = context.getAbsolutePos(new BlockPos(3, 65, 3));
		// Sky visibility uses propagated light; allow the newly placed roof to update first.
		context.waitAndRun(5, () -> {
			BlockPos cave = PatrolSpawner.findCandidate(context.getWorld(), above, above.getX(), above.getZ(), true);
			context.assertTrue(cave != null && above.getY() - cave.getY() > 48,
					"Debe encontrar cuevas fuera del margen anterior: foco=" + above + ", cueva=" + cave);
			context.assertTrue(PatrolSpawner.isSafePosition(context.getWorld(), cave), "La cueva encontrada debe tener sitio válido");
			BlockPos below = context.getAbsolutePos(new BlockPos(3, -20, 3));
			BlockPos surface = PatrolSpawner.findCandidate(context.getWorld(), below, below.getX(), below.getZ(), false);
			context.assertTrue(surface != null && context.getWorld().isSkyVisible(surface),
					"Debe encontrar la superficie desde un nivel subterráneo cercano");
			context.complete();
		});
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void patrolsAcceptPathsFarmlandSlabsAndStairs(TestContext context) {
		floor(context);
		context.setBlockState(1, 0, 3, Blocks.DIRT_PATH);
		context.setBlockState(2, 0, 3, Blocks.FARMLAND);
		context.setBlockState(3, 0, 3, Blocks.STONE_SLAB);
		context.setBlockState(4, 0, 3, Blocks.STONE_STAIRS);
		for (int x = 1; x <= 4; x++) {
			context.assertTrue(PatrolSpawner.isSafePosition(context.getWorld(),
					context.getAbsolutePos(new BlockPos(x, 1, 3))), "El suelo transitable no necesita ser un cubo completo");
		}
		context.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 140)
	public void daylightDoesNotBurn(TestContext context) {
		floor(context);
		context.setTime(6000);
		StormtrooperEntity trooper = trooper(context, 3, 3);
		context.waitAndRun(100, () -> {
			context.assertFalse(trooper.isOnFire(), "El sol no debe prenderles fuego");
			context.assertTrue(trooper.getHealth() == 20, "El sol no debe quitarles vida");
			context.complete();
		});
	}

	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 40)
	public void laserDamageAndFriendlyPassThrough(TestContext context) {
		floor(context);
		StormtrooperEntity shooter = trooper(context, 1, 3);
		StormtrooperEntity ally = trooper(context, 3, 3);
		MobEntity target = context.spawnMob(EntityType.PIG, 6, 1, 3);
		target.setAiDisabled(true);
		float initial = target.getHealth();
		shooter.attack(target, 1);
		context.waitAndRun(8, () -> {
			context.assertTrue(target.getHealth() == initial - 3, "El láser debe causar exactamente 3 puntos en Normal");
			context.assertTrue(ally.getHealth() == 20, "El aliado en medio no debe recibir daño");
			context.complete();
		});
	}

	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 40)
	public void wallsStopLaserBolts(TestContext context) {
		floor(context);
		StormtrooperEntity shooter = trooper(context, 1, 3);
		MobEntity target = context.spawnMob(EntityType.PIG, 6, 1, 3);
		target.setAiDisabled(true);
		for (int y = 1; y <= 3; y++) context.setBlockState(4, y, 3, Blocks.STONE);
		LaserBoltEntity bolt = new LaserBoltEntity(context.getWorld(), shooter);
		Vec3d direction = target.getPos().add(0, 0.5, 0).subtract(bolt.getPos());
		bolt.setVelocity(direction.x, direction.y, direction.z, 2.4F, 0);
		context.getWorld().spawnEntity(bolt);
		float initial = target.getHealth();
		context.waitAndRun(8, () -> {
			context.assertTrue(bolt.isRemoved(), "El láser debe desaparecer al chocar con la pared");
			context.assertTrue(target.getHealth() == initial, "El láser no debe atravesar la pared");
			context.complete();
		});
	}

	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 120)
	public void aiFindsAndShootsOtherMobs(TestContext context) {
		floor(context);
		StormtrooperEntity shooter = trooper(context, 1, 3);
		shooter.setAiDisabled(false);
		MobEntity target = context.spawnMob(EntityType.COW, 6, 1, 3);
		target.setAiDisabled(true);
		float initial = target.getHealth();
		context.waitAndRun(90, () -> {
			context.assertTrue(target.getHealth() < initial, "La IA debe encontrar y disparar a otro mob por sí sola");
			context.complete();
		});
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void followerNavigationAndLeaderReplacement(TestContext context) {
		floor(context);
		StormtrooperEntity leader = trooper(context, 5, 5);
		StormtrooperEntity follower = trooper(context, 1, 1);
		StormtrooperEntity backup = trooper(context, 2, 1);
		UUID id = UUID.randomUUID();
		leader.setPatrol(id, leader.getUuid(), 0);
		follower.setPatrol(id, leader.getUuid(), 2);
		backup.setPatrol(id, leader.getUuid(), 1);
		PatrolMarchGoal goal = new PatrolMarchGoal(follower);
		context.assertTrue(goal.canStart(), "El seguidor debe iniciar la marcha");
		goal.start();
		goal.tick();
		context.assertFalse(follower.getNavigation().isIdle(), "La marcha debe crear una ruta hacia la formación");
		goal.stop();
		leader.discard();
		context.assertTrue(follower.getPatrolLeader() == backup, "El miembro de menor posición debe reemplazar al líder");
		context.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100)
	public void followerActuallyMarchesTowardsLeader(TestContext context) {
		floor(context);
		// An elevated floor isolates this squad from targets in neighboring test structures.
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) context.setBlockState(x, 20, z, Blocks.STONE);
		}
		StormtrooperEntity leader = context.spawnEntity(ModEntities.STORMTROOPER, 5, 21, 5);
		StormtrooperEntity follower = context.spawnEntity(ModEntities.STORMTROOPER, 1, 21, 1);
		leader.setAiDisabled(true);
		leader.setOnGround(true);
		follower.setOnGround(true);
		UUID id = UUID.randomUUID();
		leader.setPatrol(id, leader.getUuid(), 0);
		follower.setPatrol(id, leader.getUuid(), 1);
		double initialDistance = follower.squaredDistanceTo(leader);
		context.waitAndRun(60, () -> {
			context.assertTrue(follower.isAlive() && follower.squaredDistanceTo(leader) < initialDistance - 4,
					"El seguidor debe desplazarse realmente hacia la formación");
			context.assertTrue(follower.getTarget() == null, "La marcha debe funcionar sin un objetivo de combate");
			context.complete();
		});
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void voiceTimingAndCombatFrequency(TestContext context) {
		context.assertTrue(TrooperVoices.lines().size() > 1, "Debe haber varias voces registradas");
		context.assertTrue(TrooperVoices.lines().stream().allMatch(line ->
				Registries.SOUND_EVENT.containsId(line.sound().getId())), "Cada clip necesita un evento de sonido registrado");
		int patrolCount = simulateSpeech(context, false);
		int combatCount = simulateSpeech(context, true);
		context.assertTrue(combatCount > patrolCount * 3, "En combate deben hablar mucho más a menudo que patrullando");
		context.complete();
	}

	private static int simulateSpeech(TestContext context, boolean combat) {
		Random random = Random.create(20261106);
		TrooperSpeech speech = new TrooperSpeech(random);
		int count = 0;
		int previousEnd = -1;
		String previousId = "";
		for (int tick = 0; tick < 12000; tick++) {
			TrooperVoices.VoiceLine line = speech.tick(combat, random, () -> true);
			if (line == null) continue;
			context.assertTrue(line.matches(combat), "La frase debe corresponder al contexto");
			context.assertFalse(line.id().equals(previousId), "No debe repetir inmediatamente el mismo clip");
			if (previousEnd >= 0) {
				int minimum = combat ? PatrolConfig.get().minCombatVoicePauseSeconds
						: PatrolConfig.get().minPatrolVoicePauseSeconds;
				context.assertTrue(tick >= previousEnd + minimum * 20, "Debe terminar la frase y respetar la pausa mínima");
			}
			previousEnd = tick + line.durationTicks();
			previousId = line.id();
			count++;
		}
		return count;
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void combatTransitionDoesNotInterruptSpeech(TestContext context) {
		Random random = Random.create(42);
		TrooperSpeech speech = new TrooperSpeech(random);
		int started = -1;
		int duration = 0;
		int response = -1;
		for (int tick = 0; tick < 500; tick++) {
			boolean combat = started >= 0;
			TrooperVoices.VoiceLine line = speech.tick(combat, random, () -> true);
			if (line == null) continue;
			if (started < 0) {
				started = tick;
				duration = line.durationTicks();
			} else {
				response = tick;
				context.assertTrue(line.matches(true), "Debe elegir una respuesta de combate");
				break;
			}
		}
		context.assertTrue(started >= 0 && response >= started + duration, "Entrar en combate no debe solapar la frase actual");
		context.assertTrue(response <= started + duration + 25, "La reacción al combate debe acelerarse al terminar la frase");
		context.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void mutedOrBusyVoiceDoesNotStart(TestContext context) {
		Random random = Random.create(7);
		TrooperSpeech busy = new TrooperSpeech(random);
		for (int tick = 0; tick < 1000; tick++) {
			context.assertTrue(busy.tick(true, random, () -> false) == null,
					"Debe esperar cuando ya están hablando los vecinos permitidos");
		}
		boolean previous = PatrolConfig.get().enableVoices;
		try {
			PatrolConfig.get().enableVoices = false;
			for (int tick = 0; tick < 1000; tick++) {
				context.assertTrue(busy.tick(true, random, () -> true) == null,
						"Desactivar voces debe detener las frases nuevas");
			}
		} finally {
			PatrolConfig.get().enableVoices = previous;
		}
		context.complete();
	}

	public static class RecordingTrooper extends StormtrooperEntity {
		private int voiceCount;

		public RecordingTrooper(World world) {
			super(ModEntities.STORMTROOPER, world);
		}

		@Override
		public void playSound(SoundEvent sound, float volume, float pitch) {
			if (TrooperVoices.lines().stream().anyMatch(line -> line.sound() == sound)) voiceCount++;
			super.playSound(sound, volume, pitch);
		}
	}

	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 400)
	public void entityActuallyEmitsVoice(TestContext context) {
		floor(context);
		RecordingTrooper trooper = new RecordingTrooper(context.getWorld());
		Vec3d footprint = context.getAbsolute(new Vec3d(3.5, 1, 3.5));
		// Keep the speaker away from neighboring tests and outside their speech-slot limits.
		Vec3d pos = new Vec3d(footprint.x, context.getWorld().getTopY() - 4, footprint.z);
		trooper.refreshPositionAndAngles(pos.x, pos.y, pos.z, 0, 0);
		trooper.setAiDisabled(true);
		trooper.setNoGravity(true);
		trooper.setPersistent();
		context.getWorld().spawnEntity(trooper);
		context.addInstantFinalTask(() -> {
			context.assertTrue(trooper.voiceCount > 0,
					"La entidad debe emitir una frase desde el servidor; edad=" + trooper.age + ", vida=" + trooper.getHealth());
			trooper.discard();
		});
	}
}
