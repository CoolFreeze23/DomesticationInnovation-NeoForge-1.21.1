package com.github.alexthe668.domesticationinnovation.client.render;

import com.github.alexthe668.domesticationinnovation.server.entity.ChainLightningEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import org.joml.Vector4f;

public class ChainLightningRender extends EntityRenderer<ChainLightningEntity> {

    private static final Vector4f LIGHTNING_COLOR = new Vector4f(0.1F, 0.3F, 0.5F, 0.5F);

    public ChainLightningRender(EntityRendererProvider.Context renderManagerIn) {
        super(renderManagerIn);
    }

    @Override
    public boolean shouldRender(ChainLightningEntity entity, Frustum frustum, double x, double y, double z) {
        Entity next = entity.getFromEntity();
        return (next != null && frustum.isVisible(entity.getBoundingBox().minmax(next.getBoundingBox())))
                || super.shouldRender(entity, frustum, x, y, z);
    }

    @Override
    public void render(ChainLightningEntity entity, float yaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int light) {
        super.render(entity, yaw, partialTicks, poseStack, buffer, light);
        Entity from = entity.getFromEntity();
        if (from == null) return;

        poseStack.pushPose();
        float x = (float) Mth.lerp(partialTicks, entity.xo, entity.getX());
        float y = (float) Mth.lerp(partialTicks, entity.yo, entity.getY());
        float z = (float) Mth.lerp(partialTicks, entity.zo, entity.getZ());
        poseStack.translate(-x, -y, -z);

        SimpleLightningRender.renderBolt(
                from.getEyePosition(partialTicks),
                entity.position(),
                LIGHTNING_COLOR,
                0.1F,
                5,
                0.45F,
                entity.getId() + entity.tickCount,
                poseStack, buffer, light
        );
        poseStack.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(ChainLightningEntity entity) {
        return null;
    }
}
