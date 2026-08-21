package com.github.alexthe668.domesticationinnovation.server.misc;

import com.github.alexthe668.domesticationinnovation.DomesticationMod;
import com.github.alexthe668.domesticationinnovation.server.item.CustomTabBehavior;
import com.github.alexthe668.domesticationinnovation.server.item.DIItemRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class DICreativeTabRegistry {

    public static final DeferredRegister<CreativeModeTab> DEF_REG = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, DomesticationMod.MODID);

    public static final Supplier<CreativeModeTab> TAB = DEF_REG.register(DomesticationMod.MODID, () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup." + DomesticationMod.MODID))
            .icon(() -> new ItemStack(DIItemRegistry.COLLAR_TAG.get()))
            .displayItems((parameters, output) -> {
                for (Supplier<Item> item : DIItemRegistry.DEF_REG.getEntries().stream().map(e -> (Supplier<Item>) e).toList()) {
                    Item resolved = item.get();
                    if (resolved instanceof CustomTabBehavior customTabBehavior) {
                        customTabBehavior.fillItemCategory(output);
                    } else {
                        output.accept(resolved);
                    }
                }
                parameters.holders().lookup(Registries.ENCHANTMENT).ifPresent(registry -> registry.listElements().forEach(holder -> {
                    ResourceLocation loc = holder.key().location();
                    if (loc.getNamespace().equals(DomesticationMod.MODID) && DomesticationMod.CONFIG.isEnchantEnabled(loc.getPath())) {
                        output.accept(EnchantedBookItem.createForEnchantment(new EnchantmentInstance(holder, holder.value().getMaxLevel())));
                    }
                }));
            })
            .build());
}
