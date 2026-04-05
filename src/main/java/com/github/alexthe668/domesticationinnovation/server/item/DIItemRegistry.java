package com.github.alexthe668.domesticationinnovation.server.item;

import com.github.alexthe668.domesticationinnovation.DomesticationMod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class DIItemRegistry {

    public static final DeferredRegister<Item> DEF_REG = DeferredRegister.create(BuiltInRegistries.ITEM, DomesticationMod.MODID);

    public static final Supplier<Item> COLLAR_TAG = DEF_REG.register("collar_tag", CollarTagItem::new);
    public static final Supplier<Item> FEATHER_ON_A_STICK = DEF_REG.register("feather_on_a_stick", FeatherOnAStickItem::new);
    public static final Supplier<Item> ROTTEN_APPLE = DEF_REG.register("rotten_apple", RottenAppleItem::new);
    public static final Supplier<Item> SINISTER_CARROT = DEF_REG.register("sinister_carrot", SinisterCarrotItem::new);
    public static final Supplier<Item> DEFLECTION_SHIELD = DEF_REG.register("deflection_shield", () -> new InventoryOnlyItem(new Item.Properties()));
    public static final Supplier<Item> MAGNET = DEF_REG.register("magnet", () -> new InventoryOnlyItem(new Item.Properties()));
    public static final Supplier<Item> DEED_OF_OWNERSHIP = DEF_REG.register("deed_of_ownership", DeedOfOwnershipItem::new);
}
