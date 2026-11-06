package com.tunombre.stormcraft.item;

import com.tunombre.stormcraft.TroperCraftMod;
import com.tunombre.stormcraft.entity.ModEntities;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

public final class ModItems {
	public static final Item BLASTER = Registry.register(Registries.ITEM, TroperCraftMod.id("blaster"),
			new BlasterItem(new Item.Settings().maxCount(1)));
	public static final Item TROOPER_HEAD = Registry.register(Registries.ITEM, TroperCraftMod.id("trooper_head"),
			new TrooperHeadItem(new Item.Settings()));
	public static final Item STORMTROOPER_SPAWN_EGG = Registry.register(Registries.ITEM,
			TroperCraftMod.id("stormtrooper_spawn_egg"),
			new SpawnEggItem(ModEntities.STORMTROOPER, 0xEEEEEE, 0x171717, new Item.Settings()));

	private ModItems() { }

	public static void register() {
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(entries -> {
			entries.add(BLASTER);
			entries.add(TROOPER_HEAD);
		});
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.SPAWN_EGGS)
				.register(entries -> entries.add(STORMTROOPER_SPAWN_EGG));
	}
}
