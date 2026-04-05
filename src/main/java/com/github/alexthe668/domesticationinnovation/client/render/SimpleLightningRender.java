package com.github.alexthe668.domesticationinnovation.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * Simple lightning bolt renderer replacing Citadel's LightningRender.
 * Draws jagged electric arcs between two 3D points.
 */
public class SimpleLightningRender {

    /**
     * Render a lightning bolt between two world positions.
     *
     * @param from       Start position (world space)
     * @param to         End position (world space)
     * @param color      RGBA color vector
     * @param width      Line thickness
     * @param segments   Number of segments in the bolt
     * @param jitter     Random displacement per segment
     * @param seed       Random seed for deterministic rendering
     * @param poseStack  Current pose stack (should be translated to world origin)
     * @param buffer     Buffer source
     * @param light      Packed light value
     */
    public static void renderBolt(Vec3 from, Vec3 to, Vector4f color, float width,
                                   int segments, float jitter, long seed,
                                   PoseStack poseStack, MultiBufferSource buffer, int light) {
        VertexConsumer consumer = buffer.getBuffer(RenderType.lightning());
        Matrix4f matrix = poseStack.last().pose();
        RandomSource random = RandomSource.create(seed);

        Vec3 direction = to.subtract(from);
        double length = direction.length();
        Vec3 step = direction.scale(1.0 / segments);

        // Generate jagged points
        Vec3[] points = new Vec3[segments + 1];
        points[0] = from;
        points[segments] = to;

        // Create perpendicular vectors for width
        Vec3 norm = direction.normalize();
        Vec3 perp1, perp2;
        if (Math.abs(norm.y) < 0.9) {
            perp1 = norm.cross(new Vec3(0, 1, 0)).normalize();
        } else {
            perp1 = norm.cross(new Vec3(1, 0, 0)).normalize();
        }
        perp2 = norm.cross(perp1).normalize();

        for (int i = 1; i < segments; i++) {
            float progress = (float) i / segments;
            float jitterScale = jitter * (1.0f - Math.abs(progress - 0.5f) * 2.0f); // Less jitter at endpoints
            double jx = (random.nextFloat() - 0.5f) * 2.0f * jitterScale;
            double jy = (random.nextFloat() - 0.5f) * 2.0f * jitterScale;
            points[i] = from.add(step.scale(i))
                    .add(perp1.scale(jx))
                    .add(perp2.scale(jy));
        }

        // Render each segment as a quad strip
        float halfWidth = width * 0.5f;
        int r = (int) (color.x * 255);
        int g = (int) (color.y * 255);
        int b = (int) (color.z * 255);
        int a = (int) (color.w * 255);

        for (int i = 0; i < segments; i++) {
            Vec3 p1 = points[i];
            Vec3 p2 = points[i + 1];
            Vec3 segDir = p2.subtract(p1).normalize();
            Vec3 offset = perp1.scale(halfWidth);

            consumer.addVertex(matrix, (float) (p1.x + offset.x), (float) (p1.y + offset.y), (float) (p1.z + offset.z))
                    .setColor(r, g, b, a);
            consumer.addVertex(matrix, (float) (p1.x - offset.x), (float) (p1.y - offset.y), (float) (p1.z - offset.z))
                    .setColor(r, g, b, a);
            consumer.addVertex(matrix, (float) (p2.x - offset.x), (float) (p2.y - offset.y), (float) (p2.z - offset.z))
                    .setColor(r, g, b, a);
            consumer.addVertex(matrix, (float) (p2.x + offset.x), (float) (p2.y + offset.y), (float) (p2.z + offset.z))
                    .setColor(r, g, b, a);
        }
    }
}
