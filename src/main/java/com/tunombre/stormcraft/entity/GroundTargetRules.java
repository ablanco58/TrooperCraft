package com.tunombre.stormcraft.entity;

import net.minecraft.entity.Flutterer;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.ai.control.FlightMoveControl;
import net.minecraft.entity.ai.pathing.BirdNavigation;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.mob.AmbientEntity;
import net.minecraft.entity.mob.DrownedEntity;
import net.minecraft.entity.mob.FlyingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.VexEntity;
import net.minecraft.entity.mob.WaterCreatureEntity;

/** Shared by targeting and projectile/explosion collision, including modded ground mobs. */
public final class GroundTargetRules {
	private GroundTargetRules() { }

	public static boolean isGroundMob(LivingEntity entity) {
		if (!(entity instanceof MobEntity mob) || !entity.isAlive() || entity instanceof StormtrooperEntity) return false;
		if (entity instanceof DrownedEntity) return true;
		SpawnGroup group = entity.getType().getSpawnGroup();
		if (entity instanceof WaterCreatureEntity || group == SpawnGroup.WATER_CREATURE || group == SpawnGroup.WATER_AMBIENT
				|| group == SpawnGroup.UNDERGROUND_WATER_CREATURE || group == SpawnGroup.AXOLOTLS) return false;
		return !(entity instanceof FlyingEntity || entity instanceof AmbientEntity || entity instanceof Flutterer
				|| entity instanceof VexEntity || entity instanceof WitherEntity || entity instanceof EnderDragonEntity
				|| mob.getNavigation() instanceof BirdNavigation || mob.getMoveControl() instanceof FlightMoveControl);
	}
}
