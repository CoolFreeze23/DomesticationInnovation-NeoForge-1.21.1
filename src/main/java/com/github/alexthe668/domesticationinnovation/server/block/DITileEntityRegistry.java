package com.github.alexthe668.domesticationinnovation.server.block;

import com.github.alexthe668.domesticationinnovation.DomesticationMod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class DITileEntityRegistry {

    public static final DeferredRegister<BlockEntityType<?>> DEF_REG = DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, DomesticationMod.MODID);

    public static final Supplier<BlockEntityType<PetBedBlockEntity>> PET_BED = DEF_REG.register("pet_bed",
            () -> BlockEntityType.Builder.of(PetBedBlockEntity::new,
                    DIBlockRegistry.WHITE_PET_BED.get(), DIBlockRegistry.ORANGE_PET_BED.get(),
                    DIBlockRegistry.MAGENTA_PET_BED.get(), DIBlockRegistry.LIGHT_BLUE_PET_BED.get(),
                    DIBlockRegistry.YELLOW_PET_BED.get(), DIBlockRegistry.LIME_PET_BED.get(),
                    DIBlockRegistry.PINK_PET_BED.get(), DIBlockRegistry.GRAY_PET_BED.get(),
                    DIBlockRegistry.LIGHT_GRAY_PET_BED.get(), DIBlockRegistry.CYAN_PET_BED.get(),
                    DIBlockRegistry.PURPLE_PET_BED.get(), DIBlockRegistry.BLUE_PET_BED.get(),
                    DIBlockRegistry.BROWN_PET_BED.get(), DIBlockRegistry.GREEN_PET_BED.get(),
                    DIBlockRegistry.RED_PET_BED.get(), DIBlockRegistry.BLACK_PET_BED.get()
            ).build(null));

    public static final Supplier<BlockEntityType<DrumBlockEntity>> DRUM = DEF_REG.register("drum",
            () -> BlockEntityType.Builder.of(DrumBlockEntity::new, DIBlockRegistry.DRUM.get()).build(null));

    public static final Supplier<BlockEntityType<WaywardLanternBlockEntity>> WAYWARD_LANTERN = DEF_REG.register("wayward_lantern",
            () -> BlockEntityType.Builder.of(WaywardLanternBlockEntity::new, DIBlockRegistry.WAYWARD_LANTERN.get()).build(null));
}
