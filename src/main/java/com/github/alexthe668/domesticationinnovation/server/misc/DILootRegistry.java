package com.github.alexthe668.domesticationinnovation.server.misc;

import com.github.alexthe668.domesticationinnovation.DomesticationMod;
import com.mojang.serialization.MapCodec;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

public class DILootRegistry {

    public static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> DEF_REG =
            DeferredRegister.create(NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, DomesticationMod.MODID);

    // Note: In NeoForge 1.21.1, loot modifier serializers use MapCodec instead of Codec
    public static final Supplier<MapCodec<DILootModifier>> LOOT_FRAGMENT =
            DEF_REG.register("di_loot_modifier", () -> DILootModifier.CODEC);
}
