package com.github.alexthe668.domesticationinnovation.server.entity;

import com.github.alexthe668.domesticationinnovation.DomesticationMod;
import com.github.alexthe668.domesticationinnovation.server.misc.DIPOIRegistry;
import com.github.alexthe668.domesticationinnovation.server.misc.DISoundRegistry;
import com.google.common.collect.ImmutableSet;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Predicate;
import java.util.function.Supplier;

public class DIVillagerRegistry {

    public static final DeferredRegister<VillagerProfession> DEF_REG = DeferredRegister.create(BuiltInRegistries.VILLAGER_PROFESSION, DomesticationMod.MODID);

    public static final Supplier<VillagerProfession> ANIMAL_TAMER = DEF_REG.register("animal_tamer", () -> {
        Predicate<Holder<PoiType>> jobSitePredicate = holder -> holder.value() == DIPOIRegistry.PET_BED.get();
        return new VillagerProfession(
                "animal_tamer",
                jobSitePredicate,
                jobSitePredicate,
                ImmutableSet.of(),
                ImmutableSet.of(),
                DISoundRegistry.PET_BED_USE.get()
        );
    });
}
