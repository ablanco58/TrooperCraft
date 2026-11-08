package com.tunombre.stormcraft.item;

import com.tunombre.stormcraft.TroperCraftMod;
import com.tunombre.stormcraft.entity.ModEntities;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterials;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.item.ToolMaterials;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

public final class ModItems {
	public static final Item BLASTER = Registry.register(Registries.ITEM, TroperCraftMod.id("blaster"),
			new BlasterItem(new Item.Settings().maxDamage(ToolMaterials.GOLD.getDurability())));
	public static final Item DL44 = Registry.register(Registries.ITEM, TroperCraftMod.id("dl44"),
			new Dl44Item(new Item.Settings().maxDamage(250)));
	public static final Item SALVAGED_HELMET = armor("salvaged_helmet", ArmorItem.Type.HELMET);
	public static final Item SALVAGED_CHESTPLATE = armor("salvaged_chestplate", ArmorItem.Type.CHESTPLATE);
	public static final Item SALVAGED_LEGGINGS = armor("salvaged_leggings", ArmorItem.Type.LEGGINGS);
	public static final Item SALVAGED_BOOTS = armor("salvaged_boots", ArmorItem.Type.BOOTS);
	public static final Item TROOPER_HEAD = Registry.register(Registries.ITEM, TroperCraftMod.id("trooper_head"),
			new TrooperHeadItem(new Item.Settings()));
	public static final Item STORMTROOPER_SPAWN_EGG = Registry.register(Registries.ITEM,
			TroperCraftMod.id("stormtrooper_spawn_egg"),
			new SpawnEggItem(ModEntities.STORMTROOPER, 0xEEEEEE, 0x171717, new Item.Settings()));

	private ModItems() { }

	private static Item armor(String id, ArmorItem.Type type) {
		return Registry.register(Registries.ITEM, TroperCraftMod.id(id), new ArmorItem(ArmorMaterials.CHAIN, type, new Item.Settings()));
	}

	public static void register() {
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(entries -> {
			entries.add(BLASTER);
			entries.add(DL44);
			entries.add(SALVAGED_HELMET);
			entries.add(SALVAGED_CHESTPLATE);
			entries.add(SALVAGED_LEGGINGS);
			entries.add(SALVAGED_BOOTS);
			entries.add(TROOPER_HEAD);
		});
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.SPAWN_EGGS)
				.register(entries -> entries.add(STORMTROOPER_SPAWN_EGG));
	}
}
