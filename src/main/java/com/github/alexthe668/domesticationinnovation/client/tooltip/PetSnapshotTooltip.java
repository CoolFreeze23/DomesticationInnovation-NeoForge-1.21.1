package com.github.alexthe668.domesticationinnovation.client.tooltip;

import com.mojang.datafixers.util.Either;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;

import javax.annotation.Nullable;

/**
 * Tooltip data for an item stack whose NBT carries a captured pet snapshot -
 * the spawn egg picked off a recall ball, or a bucketed tamed axolotl.
 * Drawn by {@link PetSnapshotTooltipRenderer} as a live spinning model.
 */
public record PetSnapshotTooltip(ResourceLocation entityTypeId, CompoundTag snapshot) implements TooltipComponent {

    /**
     * Same key DIAttachments.writePetDataTo stamps into entity snapshots; kept
     * private over there so every write funnels through the carry helpers.
     */
    private static final String PET_SNAPSHOT_KEY = "DIPetData";

    /**
     * Game-bus listener appending the preview to tooltips of snapshot-carrying
     * stacks. Registered from ClientProxy.clientInit.
     */
    public static void onGatherTooltipComponents(RenderTooltipEvent.GatherComponents event) {
        PetSnapshotTooltip preview = fromStack(event.getItemStack());
        if (preview != null) {
            event.getTooltipElements().add(Either.right(preview));
        }
    }

    @Nullable
    private static PetSnapshotTooltip fromStack(ItemStack stack) {
        CompoundTag entityData = stack.getOrDefault(DataComponents.ENTITY_DATA, CustomData.EMPTY).getUnsafe();
        if (entityData.contains(PET_SNAPSHOT_KEY)) {
            ResourceLocation typeId = ResourceLocation.tryParse(entityData.getString("id"));
            return typeId == null ? null : new PetSnapshotTooltip(typeId, entityData);
        }
        CompoundTag bucketData = stack.getOrDefault(DataComponents.BUCKET_ENTITY_DATA, CustomData.EMPTY).getUnsafe();
        if (bucketData.contains(PET_SNAPSHOT_KEY) && stack.is(Items.AXOLOTL_BUCKET)) {
            // bucket tags carry no "id"; the axolotl is the only bucketable pet
            return new PetSnapshotTooltip(BuiltInRegistries.ENTITY_TYPE.getKey(EntityType.AXOLOTL), bucketData);
        }
        return null;
    }
}
