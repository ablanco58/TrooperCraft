package com.tunombre.stormcraft.client;

import net.fabricmc.api.ClientModInitializer;
import com.tunombre.stormcraft.entity.ModEntities;
import com.tunombre.stormcraft.item.ModItems;
import net.fabricmc.fabric.api.client.rendering.v1.ArmorRenderer;
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

public class TroperCraftClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		EntityRendererRegistry.register(ModEntities.STORMTROOPER, StormtrooperRenderer::new);
		EntityRendererRegistry.register(ModEntities.LASER_BOLT, LaserBoltRenderer::new);
		EntityRendererRegistry.register(ModEntities.STAR_DESTROYER, StarDestroyerRenderer::new);
		StarDestroyerSounds.register();
		ArmorRenderer.register(new TrooperHeadArmorRenderer(), ModItems.TROOPER_HEAD);
		ArmorRenderer.register(new SalvagedArmorRenderer(), ModItems.SALVAGED_HELMET, ModItems.SALVAGED_CHESTPLATE,
				ModItems.SALVAGED_LEGGINGS, ModItems.SALVAGED_BOOTS);
		ColorProviderRegistry.ITEM.register((stack, tint) -> 0xA6A87A, ModItems.SALVAGED_HELMET,
				ModItems.SALVAGED_CHESTPLATE, ModItems.SALVAGED_LEGGINGS, ModItems.SALVAGED_BOOTS);
	}
}
