package com.tunombre.stormcraft.item;

import com.tunombre.stormcraft.config.PatrolConfig;
import com.tunombre.stormcraft.entity.LaserBoltEntity;
import com.tunombre.stormcraft.sound.ModSounds;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

public final class BlasterItem extends Item {
	public BlasterItem(Settings settings) {
		super(settings);
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		if (!world.isClient) {
			LaserBoltEntity bolt = new LaserBoltEntity(world, user);
			bolt.setVelocity(user, user.getPitch(), user.getYaw(), 0.0F, 2.4F, 0.4F);
			if (!world.spawnEntity(bolt)) return TypedActionResult.fail(stack);
			world.playSound(null, user.getX(), user.getY(), user.getZ(), ModSounds.BLASTER_SHOOT,
					SoundCategory.PLAYERS, 0.8F, 0.95F + world.random.nextFloat() * 0.1F);
			user.getItemCooldownManager().set(this, PatrolConfig.get().shotIntervalTicks);
			stack.damage(1, user, player -> player.sendToolBreakStatus(hand));
		}
		return TypedActionResult.success(stack, world.isClient);
	}

	@Override
	public boolean postHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
		stack.damage(1, attacker, entity -> entity.sendEquipmentBreakStatus(EquipmentSlot.MAINHAND));
		return true;
	}
}
