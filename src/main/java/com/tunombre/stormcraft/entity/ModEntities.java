package com.tunombre.stormcraft.entity;

import com.tunombre.stormcraft.TroperCraftMod;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

public final class ModEntities {
	public static final EntityType<StormtrooperEntity> STORMTROOPER = Registry.register(
			Registries.ENTITY_TYPE, TroperCraftMod.id("stormtrooper"),
			FabricEntityTypeBuilder.create(SpawnGroup.MONSTER, StormtrooperEntity::new)
					.dimensions(EntityDimensions.fixed(0.6F, 1.8F)).trackRangeBlocks(80).build());
	public static final EntityType<LaserBoltEntity> LASER_BOLT = Registry.register(
			Registries.ENTITY_TYPE, TroperCraftMod.id("laser_bolt"),
			FabricEntityTypeBuilder.<LaserBoltEntity>create(SpawnGroup.MISC, LaserBoltEntity::new)
					.dimensions(EntityDimensions.fixed(0.15F, 0.15F))
					.trackRangeBlocks(96).trackedUpdateRate(1).forceTrackedVelocityUpdates(true).build());
	public static final EntityType<StarDestroyerEntity> STAR_DESTROYER = Registry.register(
			Registries.ENTITY_TYPE, TroperCraftMod.id("star_destroyer"),
			FabricEntityTypeBuilder.<StarDestroyerEntity>create(SpawnGroup.MISC, StarDestroyerEntity::new)
					// Physics stays local; StarDestroyerEntity supplies the full rotated hull hitbox.
					.dimensions(EntityDimensions.fixed(2.0F, 2.0F))
					.trackRangeBlocks(256).trackedUpdateRate(3).fireImmune().build());

	private ModEntities() { }

	public static void register() {
		FabricDefaultAttributeRegistry.register(STORMTROOPER, StormtrooperEntity.createAttributes());
	}
}
