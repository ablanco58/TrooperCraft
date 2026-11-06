package com.tunombre.stormcraft.client;

import com.tunombre.stormcraft.entity.LaserBoltEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Untextured luminous geometry stays visible in dark caves. */
public final class LaserBoltRenderer extends EntityRenderer<LaserBoltEntity> {
	public LaserBoltRenderer(EntityRendererFactory.Context context) {
		super(context);
	}

	@Override
	public void render(LaserBoltEntity bolt, float yaw, float tickDelta, MatrixStack matrices,
			VertexConsumerProvider vertices, int light) {
		matrices.push();
		Vec3d velocity = bolt.getVelocity();
		if (velocity.lengthSquared() > 0.0001) {
			matrices.multiply(new Quaternionf().rotationTo(new Vector3f(0, 0, 1),
					new Vector3f((float) velocity.x, (float) velocity.y, (float) velocity.z).normalize()));
		}
		VertexConsumer consumer = vertices.getBuffer(RenderLayer.getLightning());
		Matrix4f matrix = matrices.peek().getPositionMatrix();
		beam(consumer, matrix, 0.055F, 255, 15, 5, 180);
		beam(consumer, matrix, 0.022F, 255, 235, 210, 255);
		matrices.pop();
		super.render(bolt, yaw, tickDelta, matrices, vertices, light);
	}

	private static void beam(VertexConsumer vertices, Matrix4f matrix, float radius, int r, int g, int b, int a) {
		float[][] corners = {
			{-radius, -radius, -0.6F}, {radius, -radius, -0.6F}, {radius, radius, -0.6F}, {-radius, radius, -0.6F},
			{-radius, -radius, 0.1F}, {radius, -radius, 0.1F}, {radius, radius, 0.1F}, {-radius, radius, 0.1F}
		};
		int[][] faces = {{0, 3, 2, 1}, {4, 5, 6, 7}, {0, 1, 5, 4}, {3, 7, 6, 2}, {0, 4, 7, 3}, {1, 2, 6, 5}};
		for (int[] face : faces) {
			for (int index : face) {
				float[] point = corners[index];
				vertices.vertex(matrix, point[0], point[1], point[2]).color(r, g, b, a).next();
			}
		}
	}

	@Override
	public Identifier getTexture(LaserBoltEntity entity) {
		return new Identifier("minecraft", "textures/block/white_concrete.png");
	}
}
