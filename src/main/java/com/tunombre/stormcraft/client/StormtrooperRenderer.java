package com.tunombre.stormcraft.client;

import com.tunombre.stormcraft.TroperCraftMod;
import com.tunombre.stormcraft.entity.StormtrooperEntity;
import net.minecraft.client.render.entity.BipedEntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.util.Identifier;

public final class StormtrooperRenderer extends BipedEntityRenderer<StormtrooperEntity, PlayerEntityModel<StormtrooperEntity>> {
	private static final Identifier TEXTURE = TroperCraftMod.id("textures/entity/skin_trooper.png");

	public StormtrooperRenderer(EntityRendererFactory.Context context) {
		super(context, new PlayerEntityModel<>(context.getPart(EntityModelLayers.PLAYER), false), 0.5F);
	}

	@Override
	public void render(StormtrooperEntity entity, float yaw, float tickDelta, MatrixStack matrices,
			VertexConsumerProvider vertices, int light) {
		model.rightArmPose = entity.isAttacking()
				? BipedEntityModel.ArmPose.CROSSBOW_HOLD : BipedEntityModel.ArmPose.ITEM;
		model.leftArmPose = BipedEntityModel.ArmPose.EMPTY;
		super.render(entity, yaw, tickDelta, matrices, vertices, light);
	}

	@Override
	public Identifier getTexture(StormtrooperEntity entity) {
		return TEXTURE;
	}
}
