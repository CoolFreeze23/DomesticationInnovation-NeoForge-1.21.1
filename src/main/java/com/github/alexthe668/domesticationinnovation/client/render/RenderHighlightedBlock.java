package com.github.alexthe668.domesticationinnovation.client.render;

import com.github.alexthe668.domesticationinnovation.DomesticationMod;
import com.github.alexthe668.domesticationinnovation.client.model.HighlightedBlockModel;
import com.github.alexthe668.domesticationinnovation.client.model.RecallBallModel;
import com.github.alexthe668.domesticationinnovation.server.entity.HighlightedBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.OutlineBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class RenderHighlightedBlock extends EntityRenderer<HighlightedBlockEntity> {

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(DomesticationMod.MODID, "textures/highlighted_block.png");
    private final HighlightedBlockModel highlightedBlockModel;

    public RenderHighlightedBlock(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
        this.highlightedBlockModel = new HighlightedBlockModel(context.bakeLayer(DIModelLayers.HIGHLIGHTED_BLOCK));
    }

    public void render(HighlightedBlockEntity entity, float f1, float f2, PoseStack stack, MultiBufferSource source, int packedLight) {
        stack.pushPose();
        stack.translate(0, 0.5F, 0);
        if (source instanceof OutlineBufferSource outlineSource) {
            // The entity is always glowing, so the buffer source is the outline buffer whose
            // color was just set from getTeamColor() - override it with the ore's texture color
            int color = OreColorRegistry.getBlockColor(entity.getBlockState());
            outlineSource.setColor(FastColor.ARGB32.red(color), FastColor.ARGB32.green(color), FastColor.ARGB32.blue(color), 255);
        }
        VertexConsumer vertexconsumer = source.getBuffer(RenderType.outline(this.getTextureLocation(entity)));
        this.highlightedBlockModel.renderToBuffer(stack, vertexconsumer, packedLight, OverlayTexture.NO_OVERLAY, -1);
        stack.popPose();
    }

    public ResourceLocation getTextureLocation(HighlightedBlockEntity block) {
        return TEXTURE;
    }
}