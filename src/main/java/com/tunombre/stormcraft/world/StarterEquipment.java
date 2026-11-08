package com.tunombre.stormcraft.world;

import com.tunombre.stormcraft.item.ModItems;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/** One issued loadout per player/save; ordinary reconnects and deaths never duplicate it. */
public final class StarterEquipment {
	public static final String SAVE_KEY = "tropercraft_starter_equipment";
	private static final String[] PAGES = {
		"ALIANZA PARA RESTAURAR LA REPÚBLICA\nRegistro de contingencia\n\nTu cápsula ha aterrizado en un mundo fuera de nuestras rutas seguras. El enlace con la flota se ha interrumpido. No hay confirmación de rescate.",
		"ALERTA DE OCUPACIÓN\n\nLas últimas lecturas detectaron un destructor estelar imperial en aproximación y transportes de tropas. Considera inminente el despliegue de stormtroopers. Evita las zonas expuestas y conserva una vía de retirada.",
		"EQUIPO DE EMERGENCIA\n\nDispones de una DL-44 ajustada a baja potencia y de placas de protección recuperadas. No reveles tu posición sin necesidad. Construye refugio, asegura suministros y conserva el equipo.",
		"REGLAS DE SUPERVIVENCIA\n\nRompe el contacto si la nave te localiza. Un casco imperial puede engañar a las patrullas, pero disparar al destructor revela tu identidad. Cambiar de casco no borrará esa identificación.",
		"ÚLTIMA DIRECTIVA\n\nNo podemos verificar tu ubicación ni establecer una ventana de extracción. Mantente con vida, observa los movimientos enemigos y busca una oportunidad para restablecer las comunicaciones.\n\nLa misión continúa mientras sobrevivas."
	};

	private StarterEquipment() { }

	public static void register() {
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> giveOnce(handler.player));
	}

	public static boolean giveOnce(ServerPlayerEntity player) {
		FirstEncounterState issued = player.getServer().getOverworld().getPersistentStateManager()
				.getOrCreate(FirstEncounterState::fromNbt, FirstEncounterState::new, SAVE_KEY);
		if (issued.hasEncountered(player.getUuid())) return false;
		player.getInventory().offerOrDrop(new ItemStack(ModItems.DL44));
		equipOrStore(player, EquipmentSlot.HEAD, ModItems.SALVAGED_HELMET);
		equipOrStore(player, EquipmentSlot.CHEST, ModItems.SALVAGED_CHESTPLATE);
		equipOrStore(player, EquipmentSlot.LEGS, ModItems.SALVAGED_LEGGINGS);
		equipOrStore(player, EquipmentSlot.FEET, ModItems.SALVAGED_BOOTS);
		player.getInventory().offerOrDrop(createBriefing());
		issued.markEncountered(player.getUuid());
		player.getInventory().markDirty();
		player.currentScreenHandler.sendContentUpdates();
		return true;
	}

	private static void equipOrStore(ServerPlayerEntity player, EquipmentSlot slot, Item item) {
		ItemStack stack = new ItemStack(item);
		if (player.getEquippedStack(slot).isEmpty()) player.equipStack(slot, stack);
		else player.getInventory().offerOrDrop(stack);
	}

	public static ItemStack createBriefing() {
		ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
		NbtCompound tag = book.getOrCreateNbt();
		tag.putString("title", "Protocolo de supervivencia");
		tag.putString("author", "Alianza Rebelde");
		tag.putBoolean("resolved", true);
		NbtList pages = new NbtList();
		for (String page : PAGES) pages.add(NbtString.of(Text.Serializer.toJson(Text.literal(page))));
		tag.put("pages", pages);
		return book;
	}
}
