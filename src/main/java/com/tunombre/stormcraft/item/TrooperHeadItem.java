package com.tunombre.stormcraft.item;

import java.util.List;
import net.minecraft.server.world.ServerWorld;
import com.tunombre.stormcraft.world.ImperialHostilityState;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterials;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/** Iron helmet statistics with a trooper disguise while equipped in the head slot. */
public final class TrooperHeadItem extends ArmorItem {
	public TrooperHeadItem(Settings settings) {
		super(ArmorMaterials.IRON, ArmorItem.Type.HELMET, settings);
	}

	public static boolean isDisguised(PlayerEntity player) {
		return isWearingTrooperHead(player) && (!(player.getWorld() instanceof ServerWorld world)
				|| !ImperialHostilityState.get(world).isIdentified(player));
	}

	public static boolean isWearingTrooperHead(PlayerEntity player) {
		return player.getEquippedStack(EquipmentSlot.HEAD).isOf(ModItems.TROOPER_HEAD);
	}

	@Override
	public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
		super.appendTooltip(stack, world, tooltip, context);
		tooltip.add(Text.translatable("item.tropercraft-mod.trooper_head.disguise").formatted(Formatting.GRAY));
	}
}
