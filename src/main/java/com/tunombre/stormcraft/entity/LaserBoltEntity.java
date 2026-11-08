package com.tunombre.stormcraft.entity;

import com.tunombre.stormcraft.config.PatrolConfig;
import com.tunombre.stormcraft.sound.ModSounds;
import com.tunombre.stormcraft.item.TrooperHeadItem;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Vector3f;

public final class LaserBoltEntity extends ProjectileEntity {
	public static final int MAX_AGE = 40;
	private int flightTicks;
	private boolean playerShot;
	private boolean shipShot;
	private boolean trooperShot;
	private boolean maskedPlayerShot;
	private float damageOverride;

	public LaserBoltEntity(EntityType<? extends LaserBoltEntity> type, World world) {
		super(type, world);
		setNoGravity(true);
	}

	public LaserBoltEntity(World world, LivingEntity owner) {
		this(ModEntities.LASER_BOLT, world);
		setOwner(owner);
		setPosition(owner.getX(), owner.getEyeY() - 0.15, owner.getZ());
	}

	@Override
	public void setOwner(Entity owner) {
		super.setOwner(owner);
		playerShot = owner instanceof PlayerEntity;
		shipShot = owner instanceof StarDestroyerEntity;
		trooperShot = owner instanceof StormtrooperEntity;
		maskedPlayerShot = owner instanceof PlayerEntity player && TrooperHeadItem.isWearingTrooperHead(player);
	}

	public void setDamageOverride(float damage) {
		if (!Float.isFinite(damage) || damage <= 0 || damage > 100) throw new IllegalArgumentException("Invalid laser damage");
		damageOverride = damage;
	}

	public float getDamageOverride() { return damageOverride; }

	public boolean isShipShot() { return shipShot; }
	public boolean wasFiredWithTrooperHead() { return maskedPlayerShot; }

	@Override
	protected void initDataTracker() { }

	@Override
	public void tick() {
		super.tick();
		if (++flightTicks > (shipShot ? 320 : MAX_AGE)) {
			discard();
			return;
		}
		Vec3d velocity = getVelocity();
		if (!getWorld().isClient) {
			if (!getWorld().isChunkLoaded(getBlockPos())) {
				discard();
				return;
			}
			HitResult hit = ProjectileUtil.getCollision(this, this::canHit);
			if (hit.getType() != HitResult.Type.MISS) {
				if (hit instanceof EntityHitResult entityHit) {
					Vec3d impact = entityHit.getEntity().getBoundingBox().expand(0.3)
							.raycast(getPos(), getPos().add(velocity)).orElse(hit.getPos());
					hit = new EntityHitResult(entityHit.getEntity(), impact);
				}
				setPosition(hit.getPos());
				onCollision(hit);
				return;
			}
		} else {
			getWorld().addParticle(new DustParticleEffect(new Vector3f(1, 0.04F, 0.02F), 0.65F),
					getX(), getY(), getZ(), 0, 0, 0);
		}
		setPosition(getPos().add(velocity));
		updateRotation();
		checkBlockCollision();
	}

	@Override
	protected boolean canHit(Entity entity) {
		return entity != getOwner() && !isProtectedTarget(entity) && super.canHit(entity);
	}

	private boolean isProtectedTarget(Entity entity) {
		if ((shipShot || trooperShot) && entity instanceof LivingEntity living && !(entity instanceof PlayerEntity)
				&& !GroundTargetRules.isGroundMob(living)) return true;
		if (entity instanceof StarDestroyerEntity && (shipShot || getOwner() instanceof StarDestroyerEntity)) return true;
		if (entity instanceof StormtrooperEntity) {
			// Preserve the shot's origin even if its owner unloads before impact.
			return !playerShot && !(getOwner() instanceof PlayerEntity);
		}
		return (getOwner() instanceof StormtrooperEntity || trooperShot || shipShot) && entity instanceof PlayerEntity player
				&& TrooperHeadItem.isDisguised(player);
	}

	@Override
	protected void onEntityHit(EntityHitResult hit) {
		if (getWorld().isClient || isProtectedTarget(hit.getEntity())) return;
		Entity owner = getOwner();
		// Retain player damage even when a saved shot's owner is no longer loaded.
		float damage = damageOverride > 0 ? damageOverride : playerShot || owner instanceof PlayerEntity
				? PatrolConfig.get().playerLaserDamage : shipShot ? 12.0F : PatrolConfig.get().laserDamage;
		hit.getEntity().damage(owner instanceof LivingEntity living
				? getDamageSources().mobProjectile(this, living) : getDamageSources().thrown(this, owner),
				damage);
	}

	@Override
	protected void onCollision(HitResult hit) {
		super.onCollision(hit);
		if (getWorld() instanceof ServerWorld world) {
			if (shipShot && hit.getType() == HitResult.Type.BLOCK) {
				world.createExplosion(this, getX(), getY(), getZ(), 1.5F, false, World.ExplosionSourceType.MOB);
			}
			world.playSound(null, getX(), getY(), getZ(), ModSounds.LASER_IMPACT, SoundCategory.HOSTILE, 0.45F, 1.0F);
			world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY(), getZ(), 5, 0.08, 0.08, 0.08, 0.02);
			discard();
		}
	}

	@Override
	protected void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
		nbt.putInt("FlightTicks", flightTicks);
		nbt.putBoolean("PlayerShot", playerShot);
		nbt.putBoolean("ShipShot", shipShot);
		nbt.putBoolean("TrooperShot", trooperShot);
		nbt.putBoolean("MaskedPlayerShot", maskedPlayerShot);
		nbt.putFloat("DamageOverride", damageOverride);
	}

	@Override
	protected void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		flightTicks = Math.max(0, nbt.getInt("FlightTicks"));
		playerShot = nbt.getBoolean("PlayerShot");
		shipShot = nbt.getBoolean("ShipShot");
		trooperShot = nbt.getBoolean("TrooperShot");
		maskedPlayerShot = nbt.getBoolean("MaskedPlayerShot");
		float savedDamage = nbt.getFloat("DamageOverride");
		damageOverride = Float.isFinite(savedDamage) && savedDamage > 0 && savedDamage <= 100 ? savedDamage : 0;
	}
}
