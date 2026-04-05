package com.github.alexthe668.domesticationinnovation.client.model;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.entity.LivingEntity;

public class BlazingBarModel extends EntityModel<LivingEntity> {
    private final ModelPart bar;

    public BlazingBarModel(ModelPart root) {
        this.bar = root.getChild("bar");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition partDef = mesh.getRoot();
        partDef.addOrReplaceChild("bar", CubeListBuilder.create()
                .texOffs(0, 16).addBox(-1.0F, -4.0F, -1.0F, 2.0F, 8.0F, 2.0F),
                PartPose.offset(0.0F, 14.0F, 0.0F));
        return LayerDefinition.create(mesh, 64, 32);
    }

    @Override
    public void setupAnim(LivingEntity entity, float limbSwing, float limbSwingAmount, float age, float yaw, float pitch) {
        bar.yRot = 0;
    }

    public void animateBar(float rotY) {
        this.bar.yRot = -(float) Math.toRadians(rotY);
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int light, int overlay, int color) {
        bar.render(poseStack, buffer, light, overlay, color);
    }
}
