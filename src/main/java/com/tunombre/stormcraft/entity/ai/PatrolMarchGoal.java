package com.tunombre.stormcraft.entity.ai;

import com.tunombre.stormcraft.entity.StormtrooperEntity;
import java.util.EnumSet;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.util.math.Vec3d;

/** Followers use pathfinding, so the formation bends around cave walls and terrain. */
public final class PatrolMarchGoal extends Goal {
	private final StormtrooperEntity trooper;
	private StormtrooperEntity leader;
	private int pathCooldown;

	public PatrolMarchGoal(StormtrooperEntity trooper) {
		this.trooper = trooper;
		setControls(EnumSet.of(Control.MOVE, Control.LOOK));
	}

	@Override
	public boolean canStart() {
		leader = trooper.getPatrolLeader();
		return leader != trooper && leader.isAlive() && trooper.getTarget() == null;
	}

	@Override
	public boolean shouldContinue() {
		return leader != null && leader.isAlive() && leader == trooper.getPatrolLeader()
				&& trooper.getTarget() == null;
	}

	@Override
	public void start() {
		pathCooldown = 0;
	}

	@Override
	public boolean shouldRunEveryTick() {
		return true;
	}

	@Override
	public void tick() {
		if (StormtrooperEntity.isEnemy(leader.getTarget())
				&& trooper.squaredDistanceTo(leader.getTarget()) < 32 * 32) {
			trooper.setTarget(leader.getTarget());
			return;
		}
		trooper.getLookControl().lookAt(leader, 30.0F, 30.0F);
		if (--pathCooldown > 0) return;
		pathCooldown = 10;
		int slot = Math.max(1, trooper.getFormationSlot());
		double sideways = (slot % 2 == 1 ? -1 : 1) * 1.25;
		double behind = ((slot + 1) / 2) * 2.0;
		double yaw = Math.toRadians(leader.getYaw());
		Vec3d destination = leader.getPos().add(Math.sin(yaw) * behind + Math.cos(yaw) * sideways,
				0, -Math.cos(yaw) * behind + Math.sin(yaw) * sideways);
		if (trooper.squaredDistanceTo(destination) > 2.25) {
			double speed = trooper.squaredDistanceTo(leader) > 100 ? 1.2 : 0.9;
			boolean foundPath = trooper.getNavigation().startMovingTo(destination.x, destination.y, destination.z, speed);
			if (!foundPath) trooper.getNavigation().startMovingTo(leader, speed);
		} else {
			trooper.getNavigation().stop();
		}
	}

	@Override
	public void stop() {
		trooper.getNavigation().stop();
		leader = null;
	}
}
