package com.github.alexthe668.domesticationinnovation.server.misc.trades;

import com.github.alexthe668.domesticationinnovation.DomesticationMod;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Villager trade that sells an item with random pet enchantments applied.
 * Reworked for 1.21.1 data-driven enchantments - iterates the enchantment
 * registry at trade generation time (not class load time).
 */
public class EnchantItemTrade implements VillagerTrades.ItemListing {
    private final Item item;
    private final int enchantXp;
    private final int baseEmeraldCost;
    private final int maxUses;
    private final int villagerXp;
    private final int enchantmentCount;
    private final float priceMultiplier;

    public EnchantItemTrade(Item item, int enchantXp, int enchantmentCount, int emeralds, int maxUses, int villagerXp) {
        this(item, enchantXp, enchantmentCount, emeralds, maxUses, villagerXp, 0.05F);
    }

    public EnchantItemTrade(Item item, int enchantXp, int enchantmentCount, int emeralds, int maxUses, int villagerXp, float priceMultiplier) {
        this.item = item;
        this.enchantXp = enchantXp;
        this.baseEmeraldCost = emeralds;
        this.maxUses = maxUses;
        this.villagerXp = villagerXp;
        this.enchantmentCount = enchantmentCount;
        this.priceMultiplier = priceMultiplier;
    }

    @Nullable
    @Override
    public MerchantOffer getOffer(Entity entity, RandomSource random) {
        int xp = Math.max(6, enchantXp + 5 - random.nextInt(5));
        ItemStack result = enchantFromRegistry(entity, random, new ItemStack(this.item), xp);
        int cost = Math.min(this.baseEmeraldCost + xp, 64);
        return new MerchantOffer(
                new ItemCost(Items.EMERALD, cost),
                result,
                this.maxUses, this.villagerXp, this.priceMultiplier);
    }

    private ItemStack enchantFromRegistry(Entity entity, RandomSource random, ItemStack stack, int xpBudget) {
        var registry = entity.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);

        // Get all pet enchantments from our namespace that are tradeable
        List<Holder.Reference<Enchantment>> candidates = new ArrayList<>();
        for (Holder.Reference<Enchantment> holder : registry.listElements().toList()) {
            ResourceLocation loc = holder.key().location();
            if (loc.getNamespace().equals(DomesticationMod.MODID)
                    && DomesticationMod.CONFIG.isEnchantEnabled(loc.getPath())) {
                candidates.add(holder);
            }
        }

        if (candidates.isEmpty()) return stack;

        // Pick random enchantments up to enchantmentCount
        int applied = 0;
        List<Holder.Reference<Enchantment>> remaining = new ArrayList<>(candidates);
        while (applied < enchantmentCount && !remaining.isEmpty()) {
            int idx = random.nextInt(remaining.size());
            Holder.Reference<Enchantment> chosen = remaining.remove(idx);
            Enchantment enchant = chosen.value();
            int level = 1 + random.nextInt(enchant.getMaxLevel());
            stack.enchant(chosen, level);
            applied++;
        }

        return stack;
    }
}
