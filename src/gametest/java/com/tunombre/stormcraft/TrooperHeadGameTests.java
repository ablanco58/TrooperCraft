package com.tunombre.stormcraft;

import com.tunombre.stormcraft.entity.LaserBoltEntity;
import com.tunombre.stormcraft.entity.ModEntities;
import com.tunombre.stormcraft.entity.StormtrooperEntity;
import com.tunombre.stormcraft.item.ModItems;
import com.tunombre.stormcraft.item.TrooperHeadItem;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.CraftingInventory;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.loot.context.LootContextTypes;
import net.minecraft.recipe.RecipeType;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameRules;

public class TrooperHeadGameTests implements FabricGameTest {
	private static void floor(TestContext context) {
		context.getWorld().getServer().setDifficulty(Difficulty.NORMAL, true);
		context.getWorld().getGameRules().get(GameRules.DO_MOB_SPAWNING).set(false, context.getWorld().getServer());
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) context.setBlockState(x, 0, z, Blocks.STONE);
		}
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void disguiseRequiresTheHeadSlot(TestContext context) {
		PlayerEntity player = context.createMockSurvivalPlayer();
		context.assertTrue(StormtrooperEntity.isEnemy(player), "Un jugador normal debe seguir siendo enemigo");
		player.setStackInHand(Hand.MAIN_HAND, new ItemStack(ModItems.TROOPER_HEAD));
		context.assertTrue(StormtrooperEntity.isEnemy(player), "Tener la cabeza en la mano no debe disfrazar");
		player.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
		context.assertTrue(StormtrooperEntity.isEnemy(player), "Un casco de hierro normal no sirve de disfraz");
		player.equipStack(EquipmentSlot.HEAD, new ItemStack(ModItems.TROOPER_HEAD));
		context.assertTrue(TrooperHeadItem.isDisguised(player), "La cabeza equipada debe activar el disfraz");
		context.assertFalse(StormtrooperEntity.isEnemy(player), "Los troopers deben ignorar al jugador disfrazado");
		player.equipStack(EquipmentSlot.HEAD, ItemStack.EMPTY);
		context.assertTrue(StormtrooperEntity.isEnemy(player), "Quitarse la cabeza debe terminar el disfraz");
		context.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void headEquipsOnUseWithIronHelmetStatistics(TestContext context) {
		PlayerEntity player = context.createMockSurvivalPlayer();
		player.setStackInHand(Hand.MAIN_HAND, new ItemStack(ModItems.TROOPER_HEAD));
		var result = ModItems.TROOPER_HEAD.use(context.getWorld(), player, Hand.MAIN_HAND);
		context.assertTrue(result.getResult().isAccepted(), "Debe poder equiparse con clic derecho");
		context.assertTrue(player.getEquippedStack(EquipmentSlot.HEAD).isOf(ModItems.TROOPER_HEAD),
				"El clic derecho debe colocar el objeto en la cabeza");
		ArmorItem head = (ArmorItem) ModItems.TROOPER_HEAD;
		context.assertTrue(head.getSlotType() == EquipmentSlot.HEAD
				&& head.getProtection() == ((ArmorItem) Items.IRON_HELMET).getProtection()
				&& head.getMaxDamage() == Items.IRON_HELMET.getMaxDamage(), "Debe tener protección y durabilidad de casco de hierro");
		context.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void puttingOnHeadCancelsPursuitAndShots(TestContext context) {
		floor(context);
		StormtrooperEntity trooper = context.spawnEntity(ModEntities.STORMTROOPER, 1, 1, 3);
		trooper.setAiDisabled(true);
		trooper.setOnGround(true);
		PlayerEntity player = context.createMockSurvivalPlayer();
		Vec3d position = context.getAbsolute(new Vec3d(6.5, 1, 3.5));
		player.setPosition(position);
		trooper.setTarget(player);
		context.assertTrue(trooper.getTarget() == player, "Primero debe adquirir al jugador como objetivo");
		trooper.getNavigation().startMovingTo(player, 1.0);
		player.equipStack(EquipmentSlot.HEAD, new ItemStack(ModItems.TROOPER_HEAD));
		trooper.tickMovement();
		context.assertTrue(trooper.getTarget() == null && trooper.getNavigation().isIdle(),
				"Ponerse la cabeza durante el combate debe cancelar objetivo y persecución");
		trooper.setTarget(player);
		context.assertTrue(trooper.getTarget() == null, "No debe volver a aceptar el objetivo disfrazado");
		trooper.attack(player, 1);
		context.assertTrue(ownedBolts(context, trooper).isEmpty(), "No debe crear disparos contra el jugador disfrazado");
		player.equipStack(EquipmentSlot.HEAD, ItemStack.EMPTY);
		trooper.setTarget(player);
		trooper.attack(player, 1);
		context.assertTrue(trooper.getTarget() == player && ownedBolts(context, trooper).size() == 1,
				"Tras quitarse la cabeza debe poder ser atacado de nuevo");
		ownedBolts(context, trooper).forEach(LaserBoltEntity::discard);
		context.complete();
	}

	private static List<LaserBoltEntity> ownedBolts(TestContext context, StormtrooperEntity owner) {
		return context.getWorld().getEntitiesByClass(LaserBoltEntity.class, owner.getBoundingBox().expand(12),
				bolt -> !bolt.isRemoved() && bolt.getOwner() == owner);
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void headRecipeUsesFiveIronAndOneCoal(TestContext context) {
		ScreenHandler handler = new ScreenHandler(null, 0) {
			@Override
			public ItemStack quickMove(PlayerEntity player, int slot) { return ItemStack.EMPTY; }
			@Override
			public boolean canUse(PlayerEntity player) { return true; }
		};
		CraftingInventory inventory = new CraftingInventory(handler, 3, 3);
		for (int slot : new int[] {0, 1, 2, 3, 5}) inventory.setStack(slot, new ItemStack(Items.IRON_INGOT));
		inventory.setStack(4, new ItemStack(Items.COAL));
		var recipes = context.getWorld().getRecipeManager();
		var recipe = recipes.getFirstMatch(RecipeType.CRAFTING, inventory, context.getWorld());
		context.assertTrue(recipe.isPresent(), "Debe encontrar la receta de casco con carbón en el centro");
		ItemStack result = recipe.orElseThrow().craft(inventory, context.getWorld().getRegistryManager());
		context.assertTrue(result.isOf(ModItems.TROOPER_HEAD) && result.getCount() == 1,
				"Cinco hierros y un carbón deben producir una cabeza");
		inventory.setStack(4, ItemStack.EMPTY);
		context.assertFalse(recipes.getFirstMatch(RecipeType.CRAFTING, inventory, context.getWorld())
				.filter(match -> match.getId().equals(TroperCraftMod.id("trooper_head"))).isPresent(),
				"La receta del casco sin carbón no debe dar una cabeza");
		inventory.setStack(4, new ItemStack(Items.CHARCOAL));
		context.assertFalse(recipes.getFirstMatch(RecipeType.CRAFTING, inventory, context.getWorld())
				.filter(match -> match.getId().equals(TroperCraftMod.id("trooper_head"))).isPresent(),
				"La receta requiere carbón mineral, no carbón vegetal");
		context.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void headLootIsThirtyPercentOnlyForPlayerKills(TestContext context) {
		StormtrooperEntity trooper = ModEntities.STORMTROOPER.create(context.getWorld());
		PlayerEntity player = context.createMockSurvivalPlayer();
		LootTable table = context.getWorld().getServer().getLootManager()
				.getLootTable(TroperCraftMod.id("entities/stormtrooper"));
		context.assertTrue(table != LootTable.EMPTY && table.pools.length > 0, "Debe cargar la tabla real del stormtrooper");
		LootContextParameterSet playerKill = new LootContextParameterSet.Builder(context.getWorld())
				.add(LootContextParameters.THIS_ENTITY, trooper)
				.add(LootContextParameters.ORIGIN, trooper.getPos())
				.add(LootContextParameters.DAMAGE_SOURCE, context.getWorld().getDamageSources().playerAttack(player))
				.add(LootContextParameters.LAST_DAMAGE_PLAYER, player)
				.add(LootContextParameters.KILLER_ENTITY, player)
				.build(LootContextTypes.ENTITY);
		int dropped = 0;
		// Adjacent small LCG seeds have correlated first draws; use well-separated deterministic seeds.
		Random seeds = Random.create(20261106);
		for (int sample = 0; sample < 2000; sample++) {
			List<ItemStack> loot = table.generateLoot(playerKill, seeds.nextLong());
			context.assertTrue(loot.size() <= 1 && loot.stream().allMatch(stack -> stack.isOf(ModItems.TROOPER_HEAD)
					&& stack.getCount() == 1), "Solo debe poder soltar una cabeza por baja");
			dropped += loot.stream().mapToInt(ItemStack::getCount).sum();
		}
		context.assertTrue(dropped > 500 && dropped < 700,
				"El muestreo del botín debe aproximarse al 30 %: " + dropped + "/2000");
		LootContextParameterSet environmentalDeath = new LootContextParameterSet.Builder(context.getWorld())
				.add(LootContextParameters.THIS_ENTITY, trooper)
				.add(LootContextParameters.ORIGIN, trooper.getPos())
				.add(LootContextParameters.DAMAGE_SOURCE, context.getWorld().getDamageSources().generic())
				.build(LootContextTypes.ENTITY);
		for (int sample = 0; sample < 100; sample++) {
			context.assertTrue(table.generateLoot(environmentalDeath, seeds.nextLong()).isEmpty(),
					"Las muertes sin jugador no deben soltar la cabeza");
		}
		context.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 40)
	public void playerBlasterDamagesTroopersWithAndWithoutDisguise(TestContext context) {
		floor(context);
		StormtrooperEntity first = context.spawnEntity(ModEntities.STORMTROOPER, 6, 1, 2);
		StormtrooperEntity second = context.spawnEntity(ModEntities.STORMTROOPER, 6, 1, 5);
		first.setAiDisabled(true);
		second.setAiDisabled(true);
		for (int row : new int[] {2, 5}) {
			PlayerEntity player = context.createMockSurvivalPlayer();
			player.setPosition(context.getAbsolute(new Vec3d(1.5, 1, row + 0.5)));
			player.setYaw(-90);
			player.setPitch(0);
			if (row == 5) player.equipStack(EquipmentSlot.HEAD, new ItemStack(ModItems.TROOPER_HEAD));
			player.setStackInHand(Hand.MAIN_HAND, new ItemStack(ModItems.BLASTER));
			ModItems.BLASTER.use(context.getWorld(), player, Hand.MAIN_HAND);
		}
		context.waitAndRun(8, () -> {
			context.assertTrue(first.getHealth() == 17, "El bláster del jugador debe hacer 3 de daño al trooper");
			context.assertTrue(second.getHealth() == 17, "Llevar el disfraz no debe impedir dañar troopers con el bláster");
			context.complete();
		});
	}

	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 40)
	public void playerShotKeepsItsOriginAfterSaving(TestContext context) {
		floor(context);
		StormtrooperEntity target = context.spawnEntity(ModEntities.STORMTROOPER, 6, 1, 3);
		target.setAiDisabled(true);
		PlayerEntity player = context.createMockSurvivalPlayer();
		player.setPosition(context.getAbsolute(new Vec3d(1.5, 1, 3.5)));
		LaserBoltEntity original = new LaserBoltEntity(context.getWorld(), player);
		original.setVelocity(1, 0, 0, 2.4F, 0);
		NbtCompound saved = new NbtCompound();
		original.writeNbt(saved);
		LaserBoltEntity restored = ModEntities.LASER_BOLT.create(context.getWorld());
		restored.readNbt(saved);
		context.getWorld().spawnEntity(restored);
		context.waitAndRun(8, () -> {
			context.assertTrue(target.getHealth() == 17,
					"Guardar y cargar un disparo del jugador no debe convertirlo en fuego amigo de trooper");
			context.complete();
		});
	}
}
