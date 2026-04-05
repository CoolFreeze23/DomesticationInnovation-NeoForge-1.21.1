package com.github.alexthe668.domesticationinnovation.server.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import java.util.function.Supplier;


public class DIBlockItem extends BlockItem {

    private final Supplier<Block> blockSupplier;

    public DIBlockItem(Supplier<Block> blockSupplier, Item.Properties props) {
        super(null, props);
        this.blockSupplier = blockSupplier;
    }

    @Override
    public Block getBlock() {
        return blockSupplier.get();
    }

    public boolean canFitInsideContainerItems() {
        return !(blockSupplier.get() instanceof ShulkerBoxBlock);
    }

    @Override
    public void onDestroyed(ItemEntity p_150700_) {
        if (this.blockSupplier.get() instanceof ShulkerBoxBlock) {
            ItemStack itemstack = p_150700_.getItem();
            net.minecraft.world.item.component.CustomData customData = itemstack.get(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA);
            if (customData != null) {
                CompoundTag compoundtag = customData.copyTag();
                if (compoundtag.contains("Items", 9)) {
                    ListTag listtag = compoundtag.getList("Items", 10);
                    ItemUtils.onContainerDestroyed(p_150700_, listtag.stream().map(CompoundTag.class::cast).map(p -> ItemStack.parseOptional(p_150700_.level().registryAccess(), p)).toList());
                }
            }
        }
    }
}
