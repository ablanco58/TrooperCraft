package com.tunombre.stormcraft.client;

import com.google.gson.Gson;
import com.tunombre.stormcraft.TroperCraftMod;
import com.tunombre.stormcraft.entity.StarDestroyerEntity;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** The ship is native geometry, without external animation/model dependencies. */
public final class StarDestroyerRenderer extends EntityRenderer<StarDestroyerEntity> {
	private record MeshPart(String name, float[][] vertices, int[][] faces, String material, boolean emissive) { }
	private record ShipModel(Map<String, String> materials, List<MeshPart> parts) { }
	private record Face(float[][] points, float[][] uv, Vector3f normal) { }
	private record RenderPart(Identifier texture, boolean emissive, List<Face> faces) { }
	private static final ShipModel MODEL = loadModel();
	private static final List<RenderPart> PARTS = prepareParts();

	public StarDestroyerRenderer(EntityRendererFactory.Context context) {
		super(context);
		shadowRadius = 0;
	}

	private static List<RenderPart> prepareParts() {
		return MODEL.parts().stream().map(part -> new RenderPart(
				new Identifier(MODEL.materials().get(part.material())), part.emissive(),
				java.util.Arrays.stream(part.faces()).map(indices -> {
					float[][] points = new float[4][];
					for (int i = 0; i < 4; i++) points[i] = part.vertices()[indices[i]];
					float[] a = points[0], b = points[1], c = points[2];
					Vector3f normal = new Vector3f(b[0]-a[0], b[1]-a[1], b[2]-a[2])
							.cross(c[0]-a[0], c[1]-a[1], c[2]-a[2]).normalize();
					// Project onto the dominant plane at four blocks per tile, avoiding stretched armor.
					float[][] uv = new float[4][2];
					for (int i = 0; i < 4; i++) {
						float[] p = points[i];
						if (Math.abs(normal.y) >= Math.max(Math.abs(normal.x), Math.abs(normal.z))) {
							uv[i] = new float[]{p[0] / 4, p[2] / 4};
						} else if (Math.abs(normal.x) >= Math.abs(normal.z)) {
							uv[i] = new float[]{p[2] / 4, p[1] / 4};
						} else {
							uv[i] = new float[]{p[0] / 4, p[1] / 4};
						}
					}
					return new Face(points, uv, normal);
				}).toList())).toList();
	}

	private static ShipModel loadModel() {
		try (InputStream input = TroperCraftMod.class.getResourceAsStream(
				"/assets/tropercraft-mod/models/entity/star_destroyer.json")) {
			if (input == null) throw new IllegalStateException("Missing Star Destroyer mesh");
			return new Gson().fromJson(new InputStreamReader(input, StandardCharsets.UTF_8), ShipModel.class);
		} catch (IOException exception) {
			throw new IllegalStateException("Cannot load Star Destroyer mesh", exception);
		}
	}

	@Override
	public void render(StarDestroyerEntity ship, float yaw, float tickDelta, MatrixStack matrices,
			VertexConsumerProvider vertices, int light) {
		matrices.push();
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180 - yaw));
		float remaining = Math.max(0, ship.getArrivalTicks()-tickDelta)/StarDestroyerEntity.ARRIVAL_TICKS;
		if (remaining > 0) {
			matrices.translate(0, 0, 500*remaining*remaining*remaining);
			matrices.scale(1, 1, 1+5*remaining*remaining);
		}
		if (ship.getDepartureTicks() > 0) {
			float progress = 1-Math.max(0, ship.getDepartureTicks()-tickDelta)/StarDestroyerEntity.ARRIVAL_TICKS;
			matrices.translate(0, 0, -600*progress*progress*progress);
			matrices.scale(1, 1, 1+5*progress*progress);
		}
		boolean hurt = ship.getHurtTicks() > 0;
		Matrix4f position = matrices.peek().getPositionMatrix();
		Matrix3f normalMatrix = matrices.peek().getNormalMatrix();
		for (RenderPart part : PARTS) {
			VertexConsumer consumer = vertices.getBuffer(RenderLayer.getEntityCutoutNoCull(part.texture()));
			int partLight = part.emissive() ? LightmapTextureManager.MAX_LIGHT_COORDINATE : light;
			for (Face face : part.faces()) {
				Vector3f normal = face.normal();
				for (int index = 0; index < 4; index++) {
					float[] point = face.points()[index];
					consumer.vertex(position, point[0], point[1], point[2]).color(255, hurt ? 75 : 255, hurt ? 75 : 255, 255)
							.texture(face.uv()[index][0], face.uv()[index][1])
							.overlay(hurt ? OverlayTexture.getUv(0, true) : OverlayTexture.DEFAULT_UV).light(partLight)
							.normal(normalMatrix, normal.x, normal.y, normal.z).next();
				}
			}
		}
		matrices.pop();
		super.render(ship, yaw, tickDelta, matrices, vertices, light);
	}

	@Override
	public Identifier getTexture(StarDestroyerEntity ship) {
		return new Identifier(MODEL.materials().get("hull"));
	}
}
