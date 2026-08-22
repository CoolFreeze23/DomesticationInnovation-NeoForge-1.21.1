package com.github.alexthe668.domesticationinnovation.compat.jade;

import com.github.alexthe668.domesticationinnovation.DomesticationMod;
import com.github.alexthe668.domesticationinnovation.server.entity.TameableUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IEntityComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

import java.util.Map;
import java.util.TreeMap;

/**
 * Appends a tamed pet's collar enchantments to Jade's entity overlay:
 * curses in red, everything else in aqua, each with its level.
 */
public enum JadePetEnchantmentsProvider implements IEntityComponentProvider {
    INSTANCE;

    private static final ResourceLocation UID =
            ResourceLocation.fromNamespaceAndPath(DomesticationMod.MODID, "pet_enchantments");

    @Override
    public void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config) {
        if (!(accessor.getEntity() instanceof LivingEntity living)
                || !TameableUtils.isTamed(living)
                || !TameableUtils.hasAnyEnchantsCheap(living)) {
            return;
        }
        Map<ResourceLocation, Integer> enchants = TameableUtils.getEnchants(living);
        if (enchants == null || enchants.isEmpty()) {
            return;
        }
        // Sorted so the overlay never reorders lines between looks
        for (Map.Entry<ResourceLocation, Integer> entry : new TreeMap<>(enchants).entrySet()) {
            ResourceLocation id = entry.getKey();
            boolean curse = id.getPath().contains("curse");
            tooltip.add(Component.translatable("enchantment." + id.getNamespace() + "." + id.getPath())
                    .append(" ")
                    .append(Component.translatable("enchantment.level." + entry.getValue()))
                    .withStyle(curse ? ChatFormatting.RED : ChatFormatting.AQUA));
        }
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }
}
