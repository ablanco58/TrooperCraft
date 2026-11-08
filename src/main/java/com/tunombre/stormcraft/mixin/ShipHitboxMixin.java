package com.tunombre.stormcraft.mixin;

import com.tunombre.stormcraft.entity.StarDestroyerEntity;
import com.tunombre.stormcraft.entity.StormtrooperEntity;
import com.tunombre.stormcraft.entity.LaserBoltEntity;
import com.tunombre.stormcraft.item.TrooperHeadItem;
import com.tunombre.stormcraft.entity.GroundTargetRules;
import net.minecraft.entity.LivingEntity;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.util.TypeFilter;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Include oversized hulls beyond vanilla's two-block section search margin. */
@Mixin(World.class)
public abstract class ShipHitboxMixin {
	@Inject(method = "getOtherEntities", at = @At("RETURN"))
	private void includeHull(Entity except, Box box, Predicate<? super Entity> predicate,
			CallbackInfoReturnable<List<Entity>> result) {
		// Both projectile collision and vanilla explosions query using the laser as their source.
		if (except instanceof LaserBoltEntity laser && laser.isShipShot()) {
			result.getReturnValue().removeIf(entity -> entity instanceof StormtrooperEntity || entity instanceof StarDestroyerEntity
					|| entity instanceof PlayerEntity player && TrooperHeadItem.isDisguised(player)
					|| entity instanceof LivingEntity living && !(entity instanceof PlayerEntity) && !GroundTargetRules.isGroundMob(living));
			return;
		}
		if (except != null && !(except instanceof PlayerEntity) && !(except instanceof ProjectileEntity)) return;
		World world = (World) (Object) this;
		for (StarDestroyerEntity ship : world.getEntitiesByType(TypeFilter.instanceOf(StarDestroyerEntity.class),
				box.expand(100, 52, 100), ship -> ship != except && ship.isAlive())) {
			if (ship.getBoundingBox().intersects(box) && predicate.test(ship) && !result.getReturnValue().contains(ship)) {
				result.getReturnValue().add(ship);
			}
		}
	}
}
