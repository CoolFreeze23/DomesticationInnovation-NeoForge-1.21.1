package com.github.alexthe668.domesticationinnovation.client.render;

import com.github.alexthe668.domesticationinnovation.DomesticationMod;
import com.github.alexthe668.domesticationinnovation.client.model.*;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = DomesticationMod.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class DIModelLayers {

    public static final ModelLayerLocation SHADOW_HAND = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(DomesticationMod.MODID, "shadow_hand"), "main");
    public static final ModelLayerLocation BLAZING_BAR = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(DomesticationMod.MODID, "blazing_bar"), "main");
    public static final ModelLayerLocation RECALL_BALL = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(DomesticationMod.MODID, "recall_ball"), "main");
    public static final ModelLayerLocation HIGHLIGHTED_BLOCK = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(DomesticationMod.MODID, "highlighted_block"), "main");

    @SubscribeEvent
    public static void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(SHADOW_HAND, ShadowHandModel::createBodyLayer);
        event.registerLayerDefinition(BLAZING_BAR, BlazingBarModel::createBodyLayer);
        event.registerLayerDefinition(RECALL_BALL, RecallBallModel::createBodyLayer);
        event.registerLayerDefinition(HIGHLIGHTED_BLOCK, HighlightedBlockModel::createBodyLayer);
    }
}
