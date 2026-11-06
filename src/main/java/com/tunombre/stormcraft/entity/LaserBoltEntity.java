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
	}

	@Override
	protected void initDataTracker() { }

	@Override
	public void tick() {
		super.tick();
		if (++flightTicks > MAX_AGE) {
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
		return !isProtectedTarget(entity) && super.canHit(entity);
	}

	private boolean isProtectedTarget(Entity entity) {
		if (entity instanceof StormtrooperEntity) {
			// Preserve the shot's origin even if its owner unloads before impact.
			return !playerShot && !(getOwner() instanceof PlayerEntity);
		}
		return getOwner() instanceof StormtrooperEntity && entity instanceof PlayerEntity player
				&& TrooperHeadItem.isDisguised(player);
	}

	@Override
	protected void onEntityHit(EntityHitResult hit) {
		if (getWorld().isClient || isProtectedTarget(hit.getEntity())) return;
		Entity owner = getOwner();
		hit.getEntity().damage(owner instanceof LivingEntity living
				? getDamageSources().mobProjectile(this, living) : getDamageSources().thrown(this, owner),
				PatrolConfig.get().laserDamage);
	}

	@Override
	protected void onCollision(HitResult hit) {
		super.onCollision(hit);
		if (getWorld() instanceof ServerWorld world) {
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
	}

	@Override
	protected void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		flightTicks = Math.max(0, nbt.getInt("FlightTicks"));
		playerShot = nbt.getBoolean("PlayerShot");
	}
}
