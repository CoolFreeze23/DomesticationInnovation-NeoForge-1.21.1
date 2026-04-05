package com.github.alexthe668.domesticationinnovation.server.block;

import com.github.alexthe668.domesticationinnovation.DomesticationMod;
import com.github.alexthe668.domesticationinnovation.server.item.DIBlockItem;
import com.github.alexthe668.domesticationinnovation.server.item.DIItemRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class DIBlockRegistry {

    public static final DeferredRegister<Block> DEF_REG = DeferredRegister.create(BuiltInRegistries.BLOCK, DomesticationMod.MODID);

    public static final Supplier<Block> WHITE_PET_BED = registerBlockAndItem("pet_bed_white", () -> new PetBedBlock("white", DyeColor.WHITE));
    public static final Supplier<Block> ORANGE_PET_BED = registerBlockAndItem("pet_bed_orange", () -> new PetBedBlock("orange", DyeColor.ORANGE));
    public static final Supplier<Block> MAGENTA_PET_BED = registerBlockAndItem("pet_bed_magenta", () -> new PetBedBlock("magenta", DyeColor.MAGENTA));
    public static final Supplier<Block> LIGHT_BLUE_PET_BED = registerBlockAndItem("pet_bed_light_blue", () -> new PetBedBlock("light_blue", DyeColor.LIGHT_BLUE));
    public static final Supplier<Block> YELLOW_PET_BED = registerBlockAndItem("pet_bed_yellow", () -> new PetBedBlock("yellow", DyeColor.YELLOW));
    public static final Supplier<Block> LIME_PET_BED = registerBlockAndItem("pet_bed_lime", () -> new PetBedBlock("lime", DyeColor.LIME));
    public static final Supplier<Block> PINK_PET_BED = registerBlockAndItem("pet_bed_pink", () -> new PetBedBlock("pink", DyeColor.PINK));
    public static final Supplier<Block> GRAY_PET_BED = registerBlockAndItem("pet_bed_gray", () -> new PetBedBlock("gray", DyeColor.GRAY));
    public static final Supplier<Block> LIGHT_GRAY_PET_BED = registerBlockAndItem("pet_bed_light_gray", () -> new PetBedBlock("light_gray", DyeColor.LIGHT_GRAY));
    public static final Supplier<Block> CYAN_PET_BED = registerBlockAndItem("pet_bed_cyan", () -> new PetBedBlock("cyan", DyeColor.CYAN));
    public static final Supplier<Block> PURPLE_PET_BED = registerBlockAndItem("pet_bed_purple", () -> new PetBedBlock("purple", DyeColor.PURPLE));
    public static final Supplier<Block> BLUE_PET_BED = registerBlockAndItem("pet_bed_blue", () -> new PetBedBlock("blue", DyeColor.BLUE));
    public static final Supplier<Block> BROWN_PET_BED = registerBlockAndItem("pet_bed_brown", () -> new PetBedBlock("brown", DyeColor.BROWN));
    public static final Supplier<Block> GREEN_PET_BED = registerBlockAndItem("pet_bed_green", () -> new PetBedBlock("green", DyeColor.GREEN));
    public static final Supplier<Block> RED_PET_BED = registerBlockAndItem("pet_bed_red", () -> new PetBedBlock("red", DyeColor.RED));
    public static final Supplier<Block> BLACK_PET_BED = registerBlockAndItem("pet_bed_black", () -> new PetBedBlock("black", DyeColor.BLACK));

    public static final Supplier<Block> DRUM = registerBlockAndItem("drum", DrumBlock::new);
    public static final Supplier<Block> WAYWARD_LANTERN = registerBlockAndItem("wayward_lantern", WaywardLanternBlock::new);

    public static Supplier<Block> registerBlockAndItem(String name, Supplier<Block> block) {
        Supplier<Block> blockObj = DEF_REG.register(name, block);
        DIItemRegistry.DEF_REG.register(name, () -> new DIBlockItem(blockObj, new Item.Properties()));
        return blockObj;
    }
}
