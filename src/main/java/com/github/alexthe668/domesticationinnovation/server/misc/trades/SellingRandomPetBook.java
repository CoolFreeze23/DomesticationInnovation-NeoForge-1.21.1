package com.github.alexthe668.domesticationinnovation.server.misc.trades;

import com.github.alexthe668.domesticationinnovation.DomesticationMod;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
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
import java.util.List;
import java.util.Optional;

/**
 * Sells an enchanted book with a random pet enchantment drawn from the
 * domesticationinnovation:tamer_tradeable tag, making the animal tamer the
 * dedicated vendor for pet enchantment books. Priced with the vanilla
 * librarian book formula: 1 book + 2..(6 + 13 * level) emeralds, doubled for
 * double_trade_price enchantments and capped at 64.
 */
public class SellingRandomPetBook implements VillagerTrades.ItemListing {
    public static final TagKey<Enchantment> TAMER_TRADEABLE = TagKey.create(Registries.ENCHANTMENT,
            ResourceLocation.fromNamespaceAndPath(DomesticationMod.MODID, "tamer_tradeable"));

    private final int xpValue;

    public SellingRandomPetBook(int xpValue) {
        this.xpValue = xpValue;
    }

    @Nullable
    @Override
    public MerchantOffer getOffer(Entity trader, RandomSource rand) {
        var registry = trader.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        boolean cursesLootOnly = DomesticationMod.CONFIG.petCurseEnchantmentsLootOnly.get();
        List<Holder<Enchantment>> pool = registry.get(TAMER_TRADEABLE)
                .map(tag -> tag.stream()
                        .filter(holder -> !cursesLootOnly || !holder.is(EnchantmentTags.CURSE))
                        .filter(holder -> holder.unwrapKey()
                                .map(key -> DomesticationMod.CONFIG.isEnchantEnabled(key.location().getPath()))
                                .orElse(false))
                        .toList())
                .orElse(List.of());
        if (pool.isEmpty()) return null;

        Holder<Enchantment> pick = pool.get(rand.nextInt(pool.size()));
        int level = Mth.nextInt(rand, pick.value().getMinLevel(), pick.value().getMaxLevel());
        ItemStack book = EnchantedBookItem.createForEnchantment(new EnchantmentInstance(pick, level));

        int emeralds = 2 + rand.nextInt(5 + level * 10) + level * 3;
        if (pick.is(EnchantmentTags.DOUBLE_TRADE_PRICE)) {
            emeralds *= 2;
        }

        return new MerchantOffer(
                new ItemCost(Items.EMERALD, Math.min(emeralds, 64)),
                Optional.of(new ItemCost(Items.BOOK)),
                book,
                12, this.xpValue, 0.2F);
    }
}
