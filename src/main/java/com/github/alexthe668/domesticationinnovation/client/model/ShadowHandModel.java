package com.github.alexthe668.domesticationinnovation.client.model;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

public class ShadowHandModel extends EntityModel<LivingEntity> {
    private final ModelPart palm;
    private final ModelPart finger1;
    private final ModelPart finger2;
    private final ModelPart finger3;
    private final ModelPart thumb;

    // Default poses for reset
    private static final float PALM_DEF_X = -0.5672F;
    private static final float FINGER1_DEF_X = 0.1745F, FINGER1_DEF_Y = -0.3491F;
    private static final float FINGER2_DEF_X = 0.1745F;
    private static final float FINGER3_DEF_X = 0.1745F, FINGER3_DEF_Y = 0.3491F;
    private static final float THUMB_DEF_X = 0.1745F, THUMB_DEF_Y = -0.8727F;

    public ShadowHandModel(ModelPart root) {
        this.palm = root.getChild("palm");
        this.finger1 = this.palm.getChild("finger1");
        this.finger2 = this.palm.getChild("finger2");
        this.finger3 = this.palm.getChild("finger3");
        this.thumb = this.palm.getChild("thumb");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition partDef = mesh.getRoot();
        PartDefinition palmDef = partDef.addOrReplaceChild("palm", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-3.0F, -2.5F, -2.0F, 6.0F, 3.0F, 5.0F),
                PartPose.offsetAndRotation(0.0F, 19.0F, 0.0F, -0.5672F, 0.0F, 0.0F));
        palmDef.addOrReplaceChild("finger1", CubeListBuilder.create()
                .texOffs(0, 8).addBox(-2.0F, -2.0F, -6.0F, 2.0F, 2.0F, 7.0F),
                PartPose.offsetAndRotation(3.0F, 0.0F, -1.0F, 0.1745F, -0.3491F, 0.0F));
        palmDef.addOrReplaceChild("finger2", CubeListBuilder.create()
                .texOffs(14, 10).addBox(-1.0F, -2.0F, -6.7F, 2.0F, 2.0F, 7.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, -2.0F, 0.1745F, 0.0F, 0.0F));
        palmDef.addOrReplaceChild("finger3", CubeListBuilder.create()
                .texOffs(14, 19).addBox(0.0F, -2.0F, -6.0F, 2.0F, 2.0F, 7.0F),
                PartPose.offsetAndRotation(-3.0F, 0.0F, -1.0F, 0.1745F, 0.3491F, 0.0F));
        palmDef.addOrReplaceChild("thumb", CubeListBuilder.create()
                .texOffs(1, 17).addBox(-2F, -2.0F, -4.0F, 2.0F, 2.0F, 4.0F),
                PartPose.offsetAndRotation(3.0F, 0.0F, 3.0F, 0.1745F, -0.8727F, 0.0F));
        return LayerDefinition.create(mesh, 32, 32);
    }

    private void resetToDefault() {
        palm.xRot = PALM_DEF_X; palm.yRot = 0; palm.zRot = 0;
        finger1.xRot = FINGER1_DEF_X; finger1.yRot = FINGER1_DEF_Y; finger1.zRot = 0;
        finger2.xRot = FINGER2_DEF_X; finger2.yRot = 0; finger2.zRot = 0;
        finger3.xRot = FINGER3_DEF_X; finger3.yRot = FINGER3_DEF_Y; finger3.zRot = 0;
        thumb.xRot = THUMB_DEF_X; thumb.yRot = THUMB_DEF_Y; thumb.zRot = 0;
        thumb.x = 3.0F; thumb.y = 0.0F; thumb.z = 3.0F;
        finger1.x = 3.0F; finger1.y = 0.0F; finger1.z = -1.0F;
        finger2.x = 0.0F; finger2.y = 0.0F; finger2.z = -2.0F;
        finger3.x = -3.0F; finger3.y = 0.0F; finger3.z = -1.0F;
        palm.x = 0.0F; palm.y = 19.0F; palm.z = 0.0F;
    }

    @Override
    public void setupAnim(LivingEntity entity, float limbSwing, float limbSwingAmount, float age, float yaw, float pitch) {
        resetToDefault();
    }

    public void animateShadowHand(float punch, int handIndex, int shadowHandCount, float ageInTicks) {
        resetToDefault();
        boolean left = handIndex >= shadowHandCount / 2F;
        float leftMod = left ? -1 : 1;
        punch = Mth.clamp(punch, 0, 0.25F) * 4F;
        float still = 1F - punch;

        if (left) {
            thumb.x = -1.5F; thumb.z = 1.5F;
            thumb.xRot = THUMB_DEF_X; thumb.yRot = 0.8727F;
        }

        // Idle sway animation
        palm.xRot += Mth.sin(ageInTicks * leftMod * 0.2F + handIndex - 1F) * 0.1F * still;
        finger1.xRot += Mth.sin(ageInTicks * leftMod * 0.2F + 1F + handIndex) * 0.2F * still;
        finger2.xRot += Mth.sin(ageInTicks * leftMod * 0.2F + 3F + handIndex) * 0.2F * still;
        finger3.xRot += Mth.sin(ageInTicks * leftMod * 0.2F + 5F + handIndex) * 0.2F * still;
        thumb.yRot += Mth.sin(ageInTicks * leftMod * 0.2F + 5F + handIndex) * 0.2F * still * leftMod;

        // Punch animation - curl fingers
        float fistRot = (float) Math.toRadians(90) * punch;
        finger1.xRot += fistRot;
        finger2.xRot += fistRot;
        finger3.xRot += fistRot;
        thumb.xRot += fistRot;
        thumb.zRot += (float) Math.toRadians(30) * leftMod * punch;

        // Adjust positions during punch
        finger1.y += -2 * punch; finger1.z += 0.5F * punch;
        finger2.y += -2 * punch; finger2.z += 0.5F * punch;
        finger3.y += -2 * punch; finger3.z += 0.5F * punch;
        thumb.x += leftMod * 2 * punch; thumb.y += -2 * punch; thumb.z += -1F * punch;
        palm.y += 1 * punch; palm.z += 1 * punch;
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int light, int overlay, int color) {
        palm.render(poseStack, buffer, light, overlay, color);
    }
}
