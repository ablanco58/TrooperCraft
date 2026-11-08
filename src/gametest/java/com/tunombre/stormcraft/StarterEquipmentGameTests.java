package com.tunombre.stormcraft;

import com.mojang.authlib.GameProfile;
import com.tunombre.stormcraft.config.PatrolConfig;
import com.tunombre.stormcraft.entity.LaserBoltEntity;
import com.tunombre.stormcraft.entity.ModEntities;
import com.tunombre.stormcraft.item.Dl44Item;
import com.tunombre.stormcraft.item.ModItems;
import com.tunombre.stormcraft.world.FirstEncounterState;
import com.tunombre.stormcraft.world.StarterEquipment;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;

public class StarterEquipmentGameTests implements FabricGameTest {
	private static ServerPlayerEntity join(TestContext context) {
		var world = context.getWorld();
		var player = new ServerPlayerEntity(world.getServer(), world,
				new GameProfile(UUID.randomUUID(), "kit_"+UUID.randomUUID().toString().substring(0,8)));
		world.getServer().getPlayerManager().onPlayerConnect(new ClientConnection(NetworkSide.SERVERBOUND), player);
		player.changeGameMode(GameMode.SURVIVAL);
		return player;
	}

	private static int count(ServerPlayerEntity player, net.minecraft.item.Item item) {
		int count = 0;
		for (int slot = 0; slot < player.getInventory().size(); slot++) {
			ItemStack stack = player.getInventory().getStack(slot);
			if (stack.isOf(item)) count += stack.getCount();
		}
		return count;
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "starterKit")
	public void joinAutomaticallyIssuesTheCompleteLoadoutAndBriefingOnce(TestContext context) {
		ServerPlayerEntity player = join(context);
		try {
			context.assertTrue(count(player,ModItems.DL44) == 1 && count(player,Items.WRITTEN_BOOK) == 1,
					"El primer ingreso debe entregar la pistola y el libro");
			context.assertTrue(player.getEquippedStack(EquipmentSlot.HEAD).isOf(ModItems.SALVAGED_HELMET)
					&& player.getEquippedStack(EquipmentSlot.CHEST).isOf(ModItems.SALVAGED_CHESTPLATE)
					&& player.getEquippedStack(EquipmentSlot.LEGS).isOf(ModItems.SALVAGED_LEGGINGS)
					&& player.getEquippedStack(EquipmentSlot.FEET).isOf(ModItems.SALVAGED_BOOTS), "La armadura completa debe venir equipada");
			context.assertFalse(StarterEquipment.giveOnce(player), "Reconectarse no debe duplicar el equipo");
			var issued = context.getWorld().getPersistentStateManager().getOrCreate(FirstEncounterState::fromNbt,FirstEncounterState::new,StarterEquipment.SAVE_KEY);
			var restored = FirstEncounterState.fromNbt(issued.writeNbt(new NbtCompound()));
			context.assertTrue(restored.hasEncountered(player.getUuid()), "La entrega debe recordarse al guardar y cargar");
			context.getWorld().getPersistentStateManager().set(StarterEquipment.SAVE_KEY,restored);
			player.getInventory().clear();
			context.assertFalse(StarterEquipment.giveOnce(player), "Perder el equipo al morir no debe crear otro kit");
		} finally { context.getWorld().getServer().getPlayerManager().remove(player); }
		context.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "starterKit")
	public void existingArmorIsPreservedAndTheIssuedPieceGoesToInventory(TestContext context) {
		ServerPlayerEntity player = join(context);
		try {
			player.getInventory().clear();
			player.equipStack(EquipmentSlot.CHEST,new ItemStack(Items.DIAMOND_CHESTPLATE));
			context.getWorld().getPersistentStateManager().set(StarterEquipment.SAVE_KEY,new FirstEncounterState());
			StarterEquipment.giveOnce(player);
			context.assertTrue(player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.DIAMOND_CHESTPLATE)
					&& count(player,ModItems.SALVAGED_CHESTPLATE) == 1, "No debe sobrescribir una pechera existente ni perder la nueva");
		} finally { context.getWorld().getServer().getPlayerManager().remove(player); }
		context.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "starterKit")
	public void armorMatchesChainmailAndTheBriefingContainsTheEmergencyOrders(TestContext context) {
		for (var pair : new net.minecraft.item.Item[][]{
			{ModItems.SALVAGED_HELMET,Items.CHAINMAIL_HELMET},{ModItems.SALVAGED_CHESTPLATE,Items.CHAINMAIL_CHESTPLATE},
			{ModItems.SALVAGED_LEGGINGS,Items.CHAINMAIL_LEGGINGS},{ModItems.SALVAGED_BOOTS,Items.CHAINMAIL_BOOTS}}) {
			context.assertTrue(pair[0].getMaxDamage() == pair[1].getMaxDamage()
					&& ((ArmorItem)pair[0]).getProtection() == ((ArmorItem)pair[1]).getProtection(), "Cada pieza debe igualar la cota de malla");
		}
		ItemStack book = StarterEquipment.createBriefing();
		var pages = book.getNbt().getList("pages",NbtElement.STRING_TYPE);
		StringBuilder text = new StringBuilder();
		for (int i = 0; i < pages.size(); i++) text.append(Text.Serializer.fromJson(pages.getString(i)).getString());
		context.assertTrue(pages.size() == 5 && text.toString().contains("enlace con la flota")
				&& text.toString().contains("stormtroopers") && text.toString().contains("Mantente con vida"),
				"El informe debe explicar la pérdida de contacto, la invasión y la orden de sobrevivir");
		context.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "starterKit")
	public void dl44KillsAHealthyTrooperOnTheSixthActualShot(TestContext context) {
		var world = context.getWorld();
		world.getServer().setDifficulty(Difficulty.NORMAL,true);
		world.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(false,world.getServer());
		ServerPlayerEntity player = join(context);
		var target = context.spawnEntity(ModEntities.STORMTROOPER,4,1,4);
		target.setAiDisabled(true);
		try {
			player.setPosition(target.getX(),target.getY(),target.getZ()-3);
			player.setYaw(0); player.setPitch(0);
			player.setStackInHand(Hand.MAIN_HAND,new ItemStack(ModItems.DL44));
			context.assertTrue(Dl44Item.cooldownTicks() < PatrolConfig.get().shotIntervalTicks, "Debe disparar algo más rápido que el bláster imperial");
			for (int shot = 1; shot <= 6; shot++) {
				target.timeUntilRegen = 0; // Each actual shot is separated by a full weapon cooldown below.
				context.assertTrue(ModItems.DL44.use(world,player,Hand.MAIN_HAND).getResult().isAccepted(), "Debe aceptar el disparo "+shot);
				var bolts = world.getEntitiesByClass(LaserBoltEntity.class,player.getBoundingBox().expand(10),bolt -> bolt.getOwner() == player);
				context.assertTrue(bolts.size() == 1,"Cada uso debe crear un único proyectil");
				LaserBoltEntity bolt = bolts.get(0);
				for (int step = 0; step < 4 && !bolt.isRemoved(); step++) bolt.tick();
				context.assertTrue(Math.abs(target.getHealth()-Math.max(0,20-shot*3.5F)) < .001,
						"El daño real debe ser 3,5 por impacto; disparo="+shot+" vida="+target.getHealth());
				if (shot < 6) context.assertTrue(target.isAlive(),"Debe sobrevivir a los primeros cinco disparos");
				for (int tick = 0; tick < Dl44Item.cooldownTicks(); tick++) player.getItemCooldownManager().update();
			}
			context.assertFalse(target.isAlive(), "El sexto impacto debe matar al trooper");
			context.assertTrue(player.getMainHandStack().getDamage() == 6,"Cada disparo debe consumir un uso de la pistola");
		} finally { target.discard(); world.getServer().getPlayerManager().remove(player); }
		context.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "starterKit")
	public void cooldownPreventsDuplicateShotsAndDamageSurvivesSaving(TestContext context) {
		ServerPlayerEntity player = join(context);
		try {
			player.setPosition(context.getAbsolute(new Vec3d(3.5,2,3.5)));
			player.setStackInHand(Hand.MAIN_HAND,new ItemStack(ModItems.DL44));
			ModItems.DL44.use(context.getWorld(),player,Hand.MAIN_HAND);
			context.assertFalse(ModItems.DL44.use(context.getWorld(),player,Hand.MAIN_HAND).getResult().isAccepted(),"El cooldown debe impedir disparos duplicados");
			var bolts = context.getWorld().getEntitiesByClass(LaserBoltEntity.class,player.getBoundingBox().expand(10),bolt -> bolt.getOwner() == player);
			context.assertTrue(bolts.size() == 1,"Un intento durante el cooldown no debe crear otro proyectil");
			LaserBoltEntity restored = ModEntities.LASER_BOLT.create(context.getWorld());
			restored.readNbt(bolts.get(0).writeNbt(new NbtCompound()));
			context.assertTrue(restored.getDamageOverride() == Dl44Item.DAMAGE,"Guardar un láser no debe convertirlo en un disparo de ocho puntos");
			bolts.forEach(LaserBoltEntity::discard); restored.discard();
		} finally { context.getWorld().getServer().getPlayerManager().remove(player); }
		context.complete();
	}
}
