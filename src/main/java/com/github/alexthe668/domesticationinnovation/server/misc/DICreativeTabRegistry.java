package com.github.alexthe668.domesticationinnovation.server.misc;

import com.github.alexthe668.domesticationinnovation.DomesticationMod;
import com.github.alexthe668.domesticationinnovation.server.item.CustomTabBehavior;
import com.github.alexthe668.domesticationinnovation.server.item.DIItemRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class DICreativeTabRegistry {

    public static final DeferredRegister<CreativeModeTab> DEF_REG = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, DomesticationMod.MODID);

    public static final Supplier<CreativeModeTab> TAB = DEF_REG.register(DomesticationMod.MODID, () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup." + DomesticationMod.MODID))
            .icon(() -> new ItemStack(DIItemRegistry.COLLAR_TAG.get()))
            .displayItems((enabledFeatures, output) -> {
                for (Supplier<Item> item : DIItemRegistry.DEF_REG.getEntries().stream().map(e -> (Supplier<Item>) e).toList()) {
                    Item resolved = item.get();
                    if (resolved instanceof CustomTabBehavior customTabBehavior) {
                        customTabBehavior.fillItemCategory(output);
                    } else {
                        output.accept(resolved);
                    }
                }
                // Note: Enchanted books for pet enchantments should be added via
                // data-driven enchantment tags or a BuildCreativeModeTabContentsEvent listener
                // that queries the enchantment registry for our namespace.
            })
            .build());
}
