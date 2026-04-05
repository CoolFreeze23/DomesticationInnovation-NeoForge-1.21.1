package com.github.alexthe668.domesticationinnovation.client.model;

import com.github.alexthe668.domesticationinnovation.server.entity.RecallBallEntity;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

public class RecallBallModel extends EntityModel<RecallBallEntity> {
    private final ModelPart bottom;
    private final ModelPart top;

    public RecallBallModel(ModelPart root) {
        this.bottom = root.getChild("bottom");
        this.top = this.bottom.getChild("top");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition partDef = mesh.getRoot();
        PartDefinition bottomDef = partDef.addOrReplaceChild("bottom", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-4.5F, -6.0F, -4.5F, 9.0F, 6.0F, 9.0F),
                PartPose.offset(0.0F, 24.0F, 0.0F));
        bottomDef.addOrReplaceChild("top", CubeListBuilder.create()
                .texOffs(0, 15).addBox(-4.5F, -3, -9, 9.0F, 3.0F, 9.0F),
                PartPose.offset(0.0F, -6F, 4.5F));
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void setupAnim(RecallBallEntity entity, float limbSwing, float limbSwingAmount, float age, float yaw, float pitch) {
        bottom.xRot = 0;
        top.xRot = 0;
        bottom.xScale = 1;
        bottom.yScale = 1;
        bottom.zScale = 1;
    }

    public void animateBall(RecallBallEntity entity, float partialTick) {
        float open = entity.getOpenProgress(partialTick);
        this.top.xRot = (float) (-open * Math.PI * 0.75F);
        this.bottom.xRot = (float) (open * Math.PI * 0.25F);
        if (entity.isFinished()) {
            this.bottom.xScale = open;
            this.bottom.yScale = open * open;
            this.bottom.zScale = open;
        }
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int light, int overlay, int color) {
        bottom.render(poseStack, buffer, light, overlay, color);
    }
}
