package com.github.alexthe668.domesticationinnovation.compat.jade;

import com.github.alexthe668.domesticationinnovation.DomesticationMod;
import com.github.alexthe668.domesticationinnovation.server.entity.TameableUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IEntityComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * Appends the claimed pet bed's coordinates to Jade's entity overlay.
 * Shown only to the pet's owner so bed locations aren't broadcast to
 * every passerby on a server.
 */
public enum JadePetBedProvider implements IEntityComponentProvider {
    INSTANCE;

    private static final ResourceLocation UID =
            ResourceLocation.fromNamespaceAndPath(DomesticationMod.MODID, "pet_bed");

    @Override
    public void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config) {
        if (!(accessor.getEntity() instanceof LivingEntity living)
                || !TameableUtils.isTamed(living)
                || !TameableUtils.isPetOf(accessor.getPlayer(), living)) {
            return;
        }
        BlockPos bedPos = TameableUtils.getPetBedPos(living);
        if (bedPos == null) {
            return;
        }
        tooltip.add(Component.translatableWithFallback(
                        "message.domesticationinnovation.jade_pet_bed",
                        "Pet bed: %s",
                        bedPos.toShortString())
                .withStyle(ChatFormatting.GOLD));
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }
}
