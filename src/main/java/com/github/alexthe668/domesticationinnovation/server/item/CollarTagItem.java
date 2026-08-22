package com.github.alexthe668.domesticationinnovation.server.item;

import com.github.alexthe668.domesticationinnovation.DIConfig;
import com.github.alexthe668.domesticationinnovation.DomesticationMod;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class CollarTagItem extends Item {

    private static final int DEFAULT_ENCHANTABILITY = 10;

    public CollarTagItem() {
        super(new Item.Properties());
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return true;
    }

    @Override
    public int getEnchantmentValue() {
        // Guarded because item properties can be queried before the config file loads
        if (DomesticationMod.CONFIG != null && DIConfig.CONFIG_SPEC.isLoaded()) {
            return DomesticationMod.CONFIG.collarTagEnchantability.get();
        }
        return DEFAULT_ENCHANTABILITY;
    }
}
