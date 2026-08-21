package com.github.alexthe668.domesticationinnovation.server.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;

import java.util.List;
import java.util.UUID;

public class DeedOfOwnershipItem extends Item {

    public DeedOfOwnershipItem() {
        super(new Item.Properties().stacksTo(1));
    }

    public static boolean isBound(ItemStack stack) {
        return getReadOnlyTag(stack).getBoolean("HasBoundEntity");
    }

    public static UUID getBoundEntity(ItemStack stack) {
        CompoundTag tag = getReadOnlyTag(stack);
        return tag.hasUUID("BoundEntity") ? tag.getUUID("BoundEntity") : null;
    }

    public static void bindToEntity(ItemStack stack, UUID entityUUID, String entityName) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("HasBoundEntity", true);
        tag.putUUID("BoundEntity", entityUUID);
        tag.putString("BoundEntityName", entityName);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static void clearBinding(ItemStack stack) {
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(new CompoundTag()));
    }

    /**
     * Read-only view of the stack's custom data - never mutate the returned tag.
     * Write paths build a fresh tag and set the component instead.
     */
    private static CompoundTag getReadOnlyTag(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).getUnsafe();
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return super.isFoil(stack) || isBound(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> list, TooltipFlag flags) {
        CompoundTag tag = getReadOnlyTag(stack);
        if (tag.getBoolean("HasBoundEntity") && tag.contains("BoundEntityName")) {
            list.add(Component.translatable("item.domesticationinnovation.deed_of_ownership.desc",
                    tag.getString("BoundEntityName")).withStyle(ChatFormatting.GRAY));
        }
    }
}
