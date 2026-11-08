package com.tunombre.stormcraft.item;

import com.tunombre.stormcraft.config.PatrolConfig;
import com.tunombre.stormcraft.entity.LaserBoltEntity;
import com.tunombre.stormcraft.sound.ModSounds;
import java.util.List;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/** A low-output survival calibration of the canon DL-44 silhouette. */
public final class Dl44Item extends Item {
	public static final float DAMAGE = 3.5F; // A healthy 20-HP trooper survives five hits and dies on the sixth.

	public Dl44Item(Settings settings) { super(settings); }

	public static int cooldownTicks() { return Math.max(1, Math.round(PatrolConfig.get().shotIntervalTicks*.8F)); }

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		if (user.getItemCooldownManager().isCoolingDown(this)) return TypedActionResult.fail(stack);
		if (!world.isClient) {
			LaserBoltEntity laser = new LaserBoltEntity(world, user);
			laser.setDamageOverride(DAMAGE);
			laser.setVelocity(user, user.getPitch(), user.getYaw(), 0, 2.4F, .35F);
			if (!world.spawnEntity(laser)) return TypedActionResult.fail(stack);
			world.playSound(null, user.getX(), user.getY(), user.getZ(), ModSounds.BLASTER_SHOOT,
					SoundCategory.PLAYERS, .7F, 1.15F);
			user.getItemCooldownManager().set(this, cooldownTicks());
			stack.damage(1, user, player -> player.sendToolBreakStatus(hand));
		}
		return TypedActionResult.success(stack, world.isClient);
	}

	@Override
	public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
		super.appendTooltip(stack, world, tooltip, context);
		tooltip.add(Text.translatable("item.tropercraft-mod.dl44.calibration").formatted(Formatting.GRAY));
	}
}
