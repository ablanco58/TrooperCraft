package com.tunombre.stormcraft.client;

import com.tunombre.stormcraft.TroperCraftMod;
import net.fabricmc.fabric.api.client.rendering.v1.ArmorRenderer;
import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;

/** Reuse the existing skin's head and overlay, slightly expanded over the wearer's head. */
public final class TrooperHeadArmorRenderer implements ArmorRenderer {
	private static final Identifier TEXTURE = TroperCraftMod.id("textures/entity/skin_trooper.png");
	private final BipedEntityModel<LivingEntity> model = new BipedEntityModel<>(TexturedModelData.of(
			BipedEntityModel.getModelData(new Dilation(0.6F), 0.0F), 64, 64).createModel());

	@Override
	public void render(MatrixStack matrices, VertexConsumerProvider vertices, ItemStack stack, LivingEntity entity,
			EquipmentSlot slot, int light, BipedEntityModel<LivingEntity> contextModel) {
		if (slot != EquipmentSlot.HEAD) return;
		contextModel.copyBipedStateTo(model);
		model.setVisible(false);
		model.head.visible = true;
		model.hat.visible = true;
		ArmorRenderer.renderPart(matrices, vertices, light, stack, model, TEXTURE);
	}
}
