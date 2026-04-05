package com.github.alexthe668.domesticationinnovation.client.model;

import com.github.alexthe668.domesticationinnovation.server.entity.HighlightedBlockEntity;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

public class HighlightedBlockModel extends EntityModel<HighlightedBlockEntity> {
    private final ModelPart box;

    public HighlightedBlockModel(ModelPart root) {
        this.box = root.getChild("box");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition partDef = mesh.getRoot();
        partDef.addOrReplaceChild("box", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-8, -8, -8, 16.0F, 16.0F, 16.0F),
                PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void setupAnim(HighlightedBlockEntity entity, float limbSwing, float limbSwingAmount, float age, float yaw, float pitch) {
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int light, int overlay, int color) {
        box.render(poseStack, buffer, light, overlay, color);
    }
}
