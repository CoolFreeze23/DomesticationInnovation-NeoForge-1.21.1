package com.github.alexthe668.domesticationinnovation.server.misc;

import com.github.alexthe668.domesticationinnovation.DomesticationMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

public class DITagRegistry {
    // Entity tags
    public static final TagKey<EntityType<?>> PETSTORE_FISHTANK = registerEntity("petstore_fishtank");
    public static final TagKey<EntityType<?>> PETSTORE_CAGE_0 = registerEntity("petstore_cage_0");
    public static final TagKey<EntityType<?>> PETSTORE_CAGE_1 = registerEntity("petstore_cage_1");
    public static final TagKey<EntityType<?>> PETSTORE_CAGE_2 = registerEntity("petstore_cage_2");
    public static final TagKey<EntityType<?>> PETSTORE_CAGE_3 = registerEntity("petstore_cage_3");
    public static final TagKey<EntityType<?>> REFUSES_COLLAR_TAGS = registerEntity("refuses_collar_tags");
    public static final TagKey<EntityType<?>> REFUSES_PET_BEDS = registerEntity("refuses_pet_beds");

    // Item tags
    public static final TagKey<Item> TAME_FROGS_WITH = registerItem("tame_frogs_with");

    // ATM10: Convention ore tags for Ore Scenting compatibility with modded ores
    public static final TagKey<Block> CONVENTION_ORES = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath("c", "ores"));

    private static TagKey<EntityType<?>> registerEntity(String name) {
        return TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(DomesticationMod.MODID, name));
    }

    private static TagKey<Item> registerItem(String name) {
        return TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(DomesticationMod.MODID, name));
    }
}
