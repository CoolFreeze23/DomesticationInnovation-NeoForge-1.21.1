package com.github.alexthe668.domesticationinnovation.server.misc;

import com.github.alexthe668.domesticationinnovation.DomesticationMod;
import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElementType;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

public class DIVillagePieceRegistry {

    public static final DeferredRegister<StructurePoolElementType<?>> DEF_REG =
            DeferredRegister.create(Registries.STRUCTURE_POOL_ELEMENT, DomesticationMod.MODID);

    public static final Supplier<StructurePoolElementType<PetshopStructurePoolElement>> PETSHOP =
            DEF_REG.register("petshop", () -> () -> PetshopStructurePoolElement.CODEC);

    private static final String[][] VILLAGE_POOLS = {
            {"minecraft:village/plains/houses",  "domesticationinnovation:plains_petshop"},
            {"minecraft:village/desert/houses",  "domesticationinnovation:desert_petshop"},
            {"minecraft:village/savanna/houses", "domesticationinnovation:savanna_petshop"},
            {"minecraft:village/snowy/houses",   "domesticationinnovation:snowy_petshop"},
            {"minecraft:village/taiga/houses",   "domesticationinnovation:taiga_petshop"},
    };

    @SuppressWarnings("unchecked")
    public static void registerHouses(MinecraftServer server) {
        int weight = DomesticationMod.CONFIG.petstoreVillageWeight.get();
        if (weight <= 0) return;

        Registry<StructureTemplatePool> poolRegistry =
                server.registryAccess().registryOrThrow(Registries.TEMPLATE_POOL);

        Optional<Holder.Reference<StructureProcessorList>> emptyProcessors = server.registryAccess()
                .registryOrThrow(Registries.PROCESSOR_LIST)
                .getHolder(ResourceKey.create(
                        Registries.PROCESSOR_LIST, ResourceLocation.withDefaultNamespace("empty")));
        if (emptyProcessors.isEmpty()) {
            DomesticationMod.LOGGER.error("Processor list minecraft:empty is missing; petshops will not be added to villages");
            return;
        }

        Field templatesField;
        Field rawField;
        try {
            templatesField = StructureTemplatePool.class.getDeclaredField("templates");
            templatesField.setAccessible(true);
            rawField = StructureTemplatePool.class.getDeclaredField("rawTemplates");
            rawField.setAccessible(true);
        } catch (Exception e) {
            DomesticationMod.LOGGER.error("Cannot access StructureTemplatePool fields; petshops will not be added to villages", e);
            return;
        }

        int injected = 0;
        for (String[] entry : VILLAGE_POOLS) {
            ResourceLocation poolId = ResourceLocation.parse(entry[0]);
            Optional<StructureTemplatePool> maybePool = poolRegistry.getOptional(poolId);
            if (maybePool.isEmpty()) {
                DomesticationMod.LOGGER.debug("Village pool {} is absent; skipping petshop injection", entry[0]);
                continue;
            }
            StructureTemplatePool pool = maybePool.get();
            PetshopStructurePoolElement element = new PetshopStructurePoolElement(
                    ResourceLocation.parse(entry[1]), emptyProcessors.get());
            try {
                ObjectArrayList<StructurePoolElement> templates =
                        (ObjectArrayList<StructurePoolElement>) templatesField.get(pool);
                for (int i = 0; i < weight; i++) {
                    templates.add(element);
                }

                List<Pair<StructurePoolElement, Integer>> raw =
                        (List<Pair<StructurePoolElement, Integer>>) rawField.get(pool);
                List<Pair<StructurePoolElement, Integer>> mutableRaw = new java.util.ArrayList<>(raw);
                mutableRaw.add(Pair.of(element, weight));
                rawField.set(pool, mutableRaw);
                injected++;
            } catch (Exception e) {
                DomesticationMod.LOGGER.error("Failed to inject petshop into village pool {}", entry[0], e);
            }
        }
        DomesticationMod.LOGGER.info("Registered petshop village pieces in {} of {} pools with weight {}", injected, VILLAGE_POOLS.length, weight);
    }
}
