package com.tunombre.stormcraft.client;

import net.fabricmc.api.ClientModInitializer;
import com.tunombre.stormcraft.entity.ModEntities;
import com.tunombre.stormcraft.item.ModItems;
import net.fabricmc.fabric.api.client.rendering.v1.ArmorRenderer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

public class TroperCraftClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		EntityRendererRegistry.register(ModEntities.STORMTROOPER, StormtrooperRenderer::new);
		EntityRendererRegistry.register(ModEntities.LASER_BOLT, LaserBoltRenderer::new);
		ArmorRenderer.register(new TrooperHeadArmorRenderer(), ModItems.TROOPER_HEAD);
	}
}
