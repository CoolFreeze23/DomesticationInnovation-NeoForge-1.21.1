package com.github.alexthe668.domesticationinnovation.server.misc.trades;

import com.github.alexthe668.domesticationinnovation.DomesticationMod;
import net.minecraft.Util;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.WeightedRandom;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Villager trade that sells an item with random pet enchantments applied.
 * Uses enchanting-table style selection: the trade tier's XP budget gates
 * which enchantment levels are reachable via each enchantment's cost window,
 * picks are rarity-weighted, and follow-up picks are compatibility-filtered.
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
        ItemStack result = enchant(entity, random, new ItemStack(this.item), xp, enchantmentCount);
        int cost = Math.min(this.baseEmeraldCost + xp, 64);
        return new MerchantOffer(
                new ItemCost(Items.EMERALD, cost),
                result,
                this.maxUses, this.villagerXp, this.priceMultiplier);
    }

    private static ItemStack enchant(Entity entity, RandomSource random, ItemStack stack, int xpBudget, int howManyEnchants) {
        for (EnchantmentInstance instance : selectEnchantments(entity, random, stack, xpBudget, howManyEnchants)) {
            stack.enchant(instance.enchantment, instance.level);
        }
        return stack;
    }

    private static List<EnchantmentInstance> selectEnchantments(Entity entity, RandomSource random, ItemStack stack, int xpBudget, int enchantmentCount) {
        List<EnchantmentInstance> list = new ArrayList<>();
        int enchantability = stack.getEnchantmentValue();
        if (enchantability <= 0) {
            return list;
        }
        xpBudget += 1 + random.nextInt(enchantability / 4 + 1) + random.nextInt(enchantability / 4 + 1);
        float noise = (random.nextFloat() + random.nextFloat() - 1.0F) * 0.15F;
        xpBudget = Mth.clamp(Math.round((float) xpBudget + (float) xpBudget * noise), 1, Integer.MAX_VALUE);
        List<EnchantmentInstance> available = EnchantmentHelper.getAvailableEnchantmentResults(xpBudget, stack, tradeableEnchantments(entity));
        if (!available.isEmpty()) {
            WeightedRandom.getRandomItem(random, available).ifPresent(list::add);
            int enchantmentsSoFar = 0;
            while (enchantmentsSoFar < enchantmentCount && random.nextInt(25) != 0) {
                if (!list.isEmpty()) {
                    EnchantmentHelper.filterCompatibleEnchantments(available, Util.lastOf(list));
                }
                if (available.isEmpty()) {
                    break;
                }
                WeightedRandom.getRandomItem(random, available).ifPresent(list::add);
                enchantmentsSoFar++;
                xpBudget /= 2;
            }
        }
        return list;
    }

    /**
     * Pet enchantments eligible for trades: config-enabled, inclusive of curses,
     * not of treasure. The curse check runs before the treasure exclusion because
     * curses are also treasure-tagged.
     */
    private static Stream<Holder<Enchantment>> tradeableEnchantments(Entity entity) {
        var registry = entity.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        return registry.listElements()
                .filter(holder -> {
                    ResourceLocation loc = holder.key().location();
                    if (!loc.getNamespace().equals(DomesticationMod.MODID)
                            || !DomesticationMod.CONFIG.isEnchantEnabled(loc.getPath())) {
                        return false;
                    }
                    if (holder.is(EnchantmentTags.CURSE)) {
                        return true;
                    }
                    return !holder.is(EnchantmentTags.TREASURE);
                })
                .map(holder -> (Holder<Enchantment>) holder);
    }
}
