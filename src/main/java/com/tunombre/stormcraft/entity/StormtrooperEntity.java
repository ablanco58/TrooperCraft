package com.tunombre.stormcraft.entity;

import com.tunombre.stormcraft.config.PatrolConfig;
import com.tunombre.stormcraft.entity.ai.PatrolMarchGoal;
import com.tunombre.stormcraft.item.ModItems;
import com.tunombre.stormcraft.item.TrooperHeadItem;
import com.tunombre.stormcraft.sound.ModSounds;
import com.tunombre.stormcraft.sound.TrooperSpeech;
import com.tunombre.stormcraft.sound.TrooperVoices;
import java.util.Comparator;
import java.util.EnumSet;
import net.minecraft.entity.ai.goal.Goal;
import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.RangedAttackMob;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.ProjectileAttackGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public class StormtrooperEntity extends HostileEntity implements RangedAttackMob {
	public static final double MAX_HEALTH = 20.0;
	public static final float ATTACK_RANGE = 24.0F;
	private UUID patrolId = getUuid();
	private UUID leaderId = getUuid();
	private int formationSlot;
	private UUID alertedPlayerId;
	private long alertUntil;
	private StormtrooperEntity cachedLeader;
	private int nextLeaderLookup;
	private final TrooperSpeech speech = new TrooperSpeech(random);

	public StormtrooperEntity(EntityType<? extends StormtrooperEntity> type, World world) {
		super(type, world);
		experiencePoints = 5;
		setCanPickUpLoot(false);
		equipStack(EquipmentSlot.MAINHAND, new ItemStack(ModItems.BLASTER));
		setEquipmentDropChance(EquipmentSlot.MAINHAND, 0.0F);
	}

	public static DefaultAttributeContainer.Builder createAttributes() {
		return HostileEntity.createHostileAttributes()
				.add(EntityAttributes.GENERIC_MAX_HEALTH, MAX_HEALTH)
				.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.23)
				.add(EntityAttributes.GENERIC_FOLLOW_RANGE, 32.0)
				.add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 3.0);
	}

	@Override
	protected void initGoals() {
		goalSelector.add(0, new SwimGoal(this));
		goalSelector.add(1, new ProjectileAttackGoal(this, 1.0,
				PatrolConfig.get().shotIntervalTicks, ATTACK_RANGE));
		goalSelector.add(2, new PatrolMarchGoal(this));
		WanderAroundFarGoal march = new WanderAroundFarGoal(this, 0.8) {
			@Override
			public boolean canStart() {
				return getPatrolLeader() == StormtrooperEntity.this && super.canStart();
			}
		};
		march.setChance(20);
		goalSelector.add(3, march);
		goalSelector.add(4, new LookAtEntityGoal(this, PlayerEntity.class, 8.0F));
		goalSelector.add(5, new LookAroundGoal(this));
		targetSelector.add(0, new Goal() {
			{ setControls(EnumSet.of(Control.TARGET)); }
			@Override public boolean canStart() { return getAlertedPlayer() != null; }
			@Override public boolean shouldContinue() { return getAlertedPlayer() != null; }
			@Override public void start() { setTarget(getAlertedPlayer()); }
			@Override public void tick() { setTarget(getAlertedPlayer()); }
			@Override public void stop() {
				if (getTarget() != null && getTarget().getUuid().equals(alertedPlayerId)) {
					setTarget(null);
					getNavigation().stop();
				}
				alertedPlayerId = null;
			}
		});
		targetSelector.add(1, new ActiveTargetGoal<>(this, LivingEntity.class, 10, true, false,
				StormtrooperEntity::isEnemy));
	}

	/** Alarm orders override local mob targets and the normal 32-block acquisition range. */
	public void alertToPlayer(PlayerEntity player) {
		if (getWorld().isClient || !isEnemy(player)) return;
		alertedPlayerId = player.getUuid();
		alertUntil = getWorld().getTime()+200;
		setTarget(player);
	}

	@Nullable
	private PlayerEntity getAlertedPlayer() {
		if (alertedPlayerId == null || getWorld().getTime() >= alertUntil) return null;
		PlayerEntity player = getWorld().getPlayerByUuid(alertedPlayerId);
		return isEnemy(player) ? player : null;
	}

	public static boolean isEnemy(@Nullable LivingEntity entity) {
		if (entity == null || !entity.isAlive() || entity instanceof StormtrooperEntity) return false;
		if (entity instanceof PlayerEntity player) {
			return !player.isCreative() && !player.isSpectator() && !TrooperHeadItem.isDisguised(player);
		}
		return GroundTargetRules.isGroundMob(entity) && entity.isAttackable();
	}

	@Override
	public boolean canTarget(EntityType<?> type) {
		return type != ModEntities.STORMTROOPER;
	}

	@Override
	public boolean canTarget(LivingEntity target) {
		return isEnemy(target) && super.canTarget(target);
	}

	@Override
	public void setTarget(@Nullable LivingEntity target) {
		super.setTarget(isEnemy(target) ? target : null);
		setAttacking(getTarget() != null);
	}

	@Override
	public boolean isTeammate(Entity other) {
		return other instanceof StormtrooperEntity
				|| other instanceof PlayerEntity player && TrooperHeadItem.isDisguised(player)
				|| super.isTeammate(other);
	}

	@Override
	public boolean damage(DamageSource source, float amount) {
		if (source.getAttacker() instanceof StormtrooperEntity
				|| source.getSource() instanceof StormtrooperEntity) return false;
		return super.damage(source, amount);
	}

	@Override
	public void attack(LivingEntity target, float pullProgress) {
		if (getWorld().isClient || !canTarget(target) || !getVisibilityCache().canSee(target)
				|| squaredDistanceTo(target) > ATTACK_RANGE * ATTACK_RANGE) return;
		LaserBoltEntity bolt = new LaserBoltEntity(getWorld(), this);
		double dx = target.getX() - bolt.getX();
		double dy = target.getBodyY(0.55) - bolt.getY();
		double dz = target.getZ() - bolt.getZ();
		bolt.setVelocity(dx, dy, dz, 2.4F, 1.5F);
		getWorld().spawnEntity(bolt);
		playSound(ModSounds.BLASTER_SHOOT, 0.8F, 0.95F + random.nextFloat() * 0.1F);
		swingHand(Hand.MAIN_HAND);
	}

	public void setPatrol(UUID patrolId, UUID leaderId, int slot) {
		this.patrolId = patrolId;
		this.leaderId = leaderId;
		formationSlot = MathHelper.clamp(slot, 0, 4);
		cachedLeader = null;
		nextLeaderLookup = 0;
	}

	public UUID getPatrolId() {
		return patrolId;
	}

	public int getFormationSlot() {
		return formationSlot;
	}

	/** Resolve unloaded/dead leaders without forcing chunks to load. */
	public StormtrooperEntity getPatrolLeader() {
		if (!(getWorld() instanceof ServerWorld world)) return this;
		if (cachedLeader != null && cachedLeader.isAlive() && cachedLeader.getWorld() == world
				&& age < nextLeaderLookup) return cachedLeader;
		nextLeaderLookup = age + 40;
		Entity entity = world.getEntity(leaderId);
		if (entity instanceof StormtrooperEntity leader && leader.isAlive()
				&& patrolId.equals(leader.patrolId)) {
			cachedLeader = leader;
		} else {
			cachedLeader = world.getEntitiesByClass(StormtrooperEntity.class, getBoundingBox().expand(48),
					trooper -> trooper.isAlive() && patrolId.equals(trooper.patrolId)).stream()
					.min(Comparator.comparingInt(StormtrooperEntity::getFormationSlot)
							.thenComparing(trooper -> trooper.getUuid().toString())).orElse(this);
			leaderId = cachedLeader.getUuid();
		}
		return cachedLeader;
	}

	@Override
	public void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
		nbt.putUuid("PatrolId", patrolId);
		nbt.putUuid("PatrolLeader", leaderId);
		nbt.putInt("FormationSlot", formationSlot);
		if (alertedPlayerId != null) nbt.putUuid("AlertedPlayer", alertedPlayerId);
		nbt.putLong("AlertUntil", alertUntil);
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		alertedPlayerId = nbt.containsUuid("AlertedPlayer") ? nbt.getUuid("AlertedPlayer") : null;
		alertUntil = nbt.getLong("AlertUntil");
		setPatrol(nbt.containsUuid("PatrolId") ? nbt.getUuid("PatrolId") : getUuid(),
				nbt.containsUuid("PatrolLeader") ? nbt.getUuid("PatrolLeader") : getUuid(),
				nbt.getInt("FormationSlot"));
	}

	@Override
	public void tickMovement() {
		// Cancel an existing pursuit as soon as the target puts on the disguise.
		if (!getWorld().isClient && getTarget() != null && !isEnemy(getTarget())) {
			setTarget(null);
			getNavigation().stop();
		}
		super.tickMovement();
		if (getWorld().isClient || !isAlive()) return;
		boolean combat = isEnemy(getTarget());
		TrooperVoices.VoiceLine line = speech.tick(combat, random, () -> {
			if (isSilent()) return false;
			int maximumSpeaking = combat ? 2 : 1;
			return getWorld().getEntitiesByClass(StormtrooperEntity.class, getBoundingBox().expand(ModSounds.VOICE_RANGE),
					other -> other != this && other.isAlive() && other.speech.isSpeaking()).size() < maximumSpeaking;
		});
		if (line != null) playSound(line.sound(), PatrolConfig.get().voiceVolume, 1.0F);
	}

	@Override
	@Nullable
	protected SoundEvent getAmbientSound() {
		// Voice timing is explicit; disable vanilla random ambient playback to avoid overlaps.
		return null;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return ModSounds.TROOPER_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return ModSounds.TROOPER_DEATH;
	}
}
