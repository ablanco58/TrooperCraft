package com.tunombre.stormcraft.client;

import com.google.gson.Gson;
import com.tunombre.stormcraft.TroperCraftMod;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.rendering.v1.ArmorRenderer;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;

/** Articulated native armor geometry follows the player's head, arms and legs. */
public final class SalvagedArmorRenderer implements ArmorRenderer {
	private record Part(String slot, String bone, float[] from, float[] size, String material) { }
	private record ArmorMesh(List<Part> parts) { }
	private record Layer(BipedEntityModel<LivingEntity> model, float r, float g, float b) { }
	private static final Identifier TEXTURE = new Identifier("minecraft", "textures/block/white_concrete.png");
	private static final Map<String, float[]> COLORS = Map.of("plate", new float[]{.65F,.66F,.48F},
			"joint", new float[]{.13F,.16F,.13F}, "edge", new float[]{.36F,.38F,.31F}, "emblem", new float[]{.65F,.12F,.08F});
	private static final String[] BONES = {"head","hat","body","right_arm","left_arm","right_leg","left_leg"};
	private final Map<EquipmentSlot, List<Layer>> layers = new EnumMap<>(EquipmentSlot.class);

	public SalvagedArmorRenderer() {
		try (InputStream input = TroperCraftMod.class.getResourceAsStream("/assets/tropercraft-mod/models/entity/salvaged_armor.json")) {
			if (input == null) throw new IllegalStateException("Missing salvaged armor geometry");
			ArmorMesh mesh = new Gson().fromJson(new InputStreamReader(input, StandardCharsets.UTF_8), ArmorMesh.class);
			for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
				List<Layer> list = new ArrayList<>();
				for (var color : COLORS.entrySet()) {
					ModelData data = new ModelData();
					Map<String, ModelPartBuilder> builders = new HashMap<>();
					for (String bone : BONES) builders.put(bone, ModelPartBuilder.create().uv(0,0));
					for (Part part : mesh.parts()) {
						if (!part.slot().equals(slot.getName()) || !part.material().equals(color.getKey())) continue;
						float[] a = part.from(), size = part.size();
						builders.get(part.bone()).cuboid(a[0],a[1],a[2],size[0],size[1],size[2]);
					}
					for (String bone : BONES) data.getRoot().addChild(bone, builders.get(bone), pivot(bone));
					float[] rgb = color.getValue();
					list.add(new Layer(new BipedEntityModel<>(TexturedModelData.of(data,64,64).createModel()), rgb[0],rgb[1],rgb[2]));
				}
				layers.put(slot,list);
			}
		} catch (java.io.IOException exception) { throw new IllegalStateException("Cannot load salvaged armor",exception); }
	}

	private static ModelTransform pivot(String bone) {
		return switch (bone) {
			case "right_arm" -> ModelTransform.pivot(-5,2,0);
			case "left_arm" -> ModelTransform.pivot(5,2,0);
			case "right_leg" -> ModelTransform.pivot(-1.9F,12,0);
			case "left_leg" -> ModelTransform.pivot(1.9F,12,0);
			default -> ModelTransform.NONE;
		};
	}

	@Override
	public void render(MatrixStack matrices, VertexConsumerProvider vertices, ItemStack stack, LivingEntity entity,
			EquipmentSlot slot, int light, BipedEntityModel<LivingEntity> contextModel) {
		for (Layer layer : layers.getOrDefault(slot,List.of())) {
			BipedEntityModel<LivingEntity> model = layer.model();
			contextModel.copyBipedStateTo(model);
			var consumer = ItemRenderer.getArmorGlintConsumer(vertices, RenderLayer.getArmorCutoutNoCull(TEXTURE), false, stack.hasGlint());
			model.render(matrices,consumer,light,OverlayTexture.DEFAULT_UV,layer.r(),layer.g(),layer.b(),1);
		}
	}
}
