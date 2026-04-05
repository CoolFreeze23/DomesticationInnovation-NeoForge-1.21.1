package com.github.alexthe668.domesticationinnovation.server.misc.trades;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;

import javax.annotation.Nullable;

/**
 * Sells an enchanted book with a specific pet enchantment.
 * Updated for 1.21.1 data-driven enchantments using ResourceKey lookup.
 */
public class SellingEnchantedBook implements VillagerTrades.ItemListing {
    private final ResourceKey<Enchantment> enchantmentKey;
    private final int maxLevels;
    private final int emeraldCount;
    private final int maxUses;
    private final int xpValue;
    private final float priceMultiplier;

    public SellingEnchantedBook(ResourceKey<Enchantment> enchantmentKey, int maxLevels, int emeraldCount, int maxUses, int xpValue, float priceMultiplier) {
        this.enchantmentKey = enchantmentKey;
        this.maxLevels = maxLevels;
        this.emeraldCount = emeraldCount;
        this.maxUses = maxUses;
        this.xpValue = xpValue;
        this.priceMultiplier = priceMultiplier;
    }

    @Nullable
    @Override
    public MerchantOffer getOffer(Entity trader, RandomSource rand) {
        // Look up the enchantment holder from the data-driven registry
        var registry = trader.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        var holder = registry.get(enchantmentKey);
        if (holder.isEmpty()) return null;

        int level = maxLevels > 1 ? 1 + rand.nextInt(maxLevels - 1) : 1;
        ItemStack book = EnchantedBookItem.createForEnchantment(new EnchantmentInstance(holder.get(), level));

        return new MerchantOffer(
                new ItemCost(Items.EMERALD, this.emeraldCount),
                book,
                this.maxUses, this.xpValue, this.priceMultiplier);
    }
}
