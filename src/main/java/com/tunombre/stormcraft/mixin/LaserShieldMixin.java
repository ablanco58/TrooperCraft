package com.tunombre.stormcraft.mixin;

import com.tunombre.stormcraft.entity.LaserBoltEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Keep vanilla facing, wind-up, health protection, sounds and break behavior. */
@Mixin(LivingEntity.class)
public abstract class LaserShieldMixin {
	@Shadow protected abstract void damageShield(float amount);

	@Redirect(method = "damage", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;damageShield(F)V"))
	private void laserShieldWear(LivingEntity defender, float amount, DamageSource source, float incomingDamage) {
		// Vanilla consumes 1 + floor(amount): at least 16 durability per blocked laser.
		damageShield(source.getSource() instanceof LaserBoltEntity ? Math.max(15.0F, amount*2) : amount);
	}
}
