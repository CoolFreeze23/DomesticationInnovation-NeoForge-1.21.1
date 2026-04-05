package com.github.alexthe668.domesticationinnovation.server.misc;

import com.github.alexthe668.domesticationinnovation.DomesticationMod;
import com.github.alexthe668.domesticationinnovation.server.enchantment.DIEnchantmentKeys;
import com.github.alexthe668.domesticationinnovation.server.item.DIItemRegistry;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;

import javax.annotation.Nonnull;
import java.util.Optional;

public class DILootModifier extends LootModifier {

    public static final MapCodec<DILootModifier> CODEC =
            RecordCodecBuilder.mapCodec(inst -> codecStart(inst)
                    .and(com.mojang.serialization.Codec.INT.fieldOf("loot_type").orElse(0).forGetter(lm -> lm.lootType))
                    .apply(inst, DILootModifier::new));

    private final int lootType;

    protected DILootModifier(LootItemCondition[] conditionsIn, int lootType) {
        super(conditionsIn);
        this.lootType = lootType;
    }

    @Nonnull
    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        RandomSource random = context.getRandom();
        switch (lootType) {
            case 0 -> {
                if (random.nextFloat() < DomesticationMod.CONFIG.sinisterCarrotLootChance.get())
                    generatedLoot.add(new ItemStack(DIItemRegistry.SINISTER_CARROT.get(), random.nextInt(1, 2)));
            }
            case 1 -> addEnchantedBookIfChance(generatedLoot, context, DIEnchantmentKeys.BUBBLING,
                    DomesticationMod.CONFIG.bubblingLootChance.get());
            case 2 -> addEnchantedBookIfChance(generatedLoot, context, DIEnchantmentKeys.VAMPIRE,
                    DomesticationMod.CONFIG.vampirismLootChance.get());
            case 3 -> addEnchantedBookIfChance(generatedLoot, context, DIEnchantmentKeys.VOID_CLOUD,
                    DomesticationMod.CONFIG.voidCloudLootChance.get());
            case 4 -> addEnchantedBookIfChance(generatedLoot, context, DIEnchantmentKeys.ORE_SCENTING,
                    DomesticationMod.CONFIG.oreScentingLootChance.get());
            case 5 -> addEnchantedBookIfChance(generatedLoot, context, DIEnchantmentKeys.MUFFLED,
                    DomesticationMod.CONFIG.muffledLootChance.get());
            case 6 -> addEnchantedBookIfChance(generatedLoot, context, DIEnchantmentKeys.BLAZING_PROTECTION,
                    DomesticationMod.CONFIG.blazingProtectionLootChance.get());
        }
        return generatedLoot;
    }

    private void addEnchantedBookIfChance(ObjectArrayList<ItemStack> loot, LootContext ctx,
                                           ResourceKey<Enchantment> key, double chance) {
        if (ctx.getRandom().nextFloat() < chance) {
            var registry = ctx.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
            Optional<Holder.Reference<Enchantment>> holder = registry.get(key);
            if (holder.isPresent()) {
                int max = holder.get().value().getMaxLevel();
                int lvl = max > 1 ? 1 + ctx.getRandom().nextInt(max) : 1;
                loot.add(EnchantedBookItem.createForEnchantment(new EnchantmentInstance(holder.get(), lvl)));
            }
        }
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
}
