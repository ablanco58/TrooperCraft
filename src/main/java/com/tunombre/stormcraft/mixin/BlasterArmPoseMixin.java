package com.tunombre.stormcraft.mixin;

import com.tunombre.stormcraft.item.ModItems;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Arm;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps the shared player/trooper blaster pointed along the wearer's gaze. */
@Mixin(BipedEntityModel.class)
public abstract class BlasterArmPoseMixin {
	@Shadow @Final public ModelPart head;
	@Shadow @Final public ModelPart rightArm;
	@Shadow @Final public ModelPart leftArm;

	@Inject(method = "setAngles(Lnet/minecraft/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
	private void tropercraft$aimBlaster(LivingEntity entity, float limbAngle, float limbDistance,
			float animationProgress, float headYaw, float headPitch, CallbackInfo ci) {
		boolean mainBlaster = (entity.getMainHandStack().isOf(ModItems.BLASTER) || entity.getMainHandStack().isOf(ModItems.DL44));
		boolean offBlaster = (entity.getOffHandStack().isOf(ModItems.BLASTER) || entity.getOffHandStack().isOf(ModItems.DL44));
		if (!mainBlaster && !offBlaster) {
			return;
		}

		Arm mainArm = entity.getMainArm();
		if (mainBlaster) {
			tropercraft$pointArm(mainArm);
		}
		if (offBlaster) {
			tropercraft$pointArm(mainArm.getOpposite());
		}

		// Support the barrel only with an empty hand; preserve a held shield/tool.
		if (entity.getMainHandStack().isOf(ModItems.BLASTER) && entity.getOffHandStack().isEmpty()) {
			tropercraft$supportBarrel(mainArm.getOpposite());
		} else if (entity.getOffHandStack().isOf(ModItems.BLASTER) && entity.getMainHandStack().isEmpty()) {
			tropercraft$supportBarrel(mainArm);
		}
	}

	@Unique
	private void tropercraft$pointArm(Arm side) {
		ModelPart arm = side == Arm.RIGHT ? rightArm : leftArm;
		arm.pitch = head.pitch - (float) Math.PI / 2.0F;
		arm.yaw = head.yaw;
		arm.roll = 0.0F;
	}

	@Unique
	private void tropercraft$supportBarrel(Arm side) {
		ModelPart arm = side == Arm.RIGHT ? rightArm : leftArm;
		arm.pitch = head.pitch - 1.35F;
		arm.yaw = head.yaw + (side == Arm.LEFT ? 0.65F : -0.65F);
		arm.roll = 0.0F;
	}
}
