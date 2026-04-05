package com.github.alexthe668.domesticationinnovation.server.entity;

import com.github.alexthe668.domesticationinnovation.DomesticationMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/**
 * NeoForge entity attachment system - replaces Citadel's CitadelEntityData.
 *
 * All pet enchantment data, collar state, bed positions, etc. are stored
 * in a single CompoundTag attachment on each entity. This data automatically
 * persists across saves and syncs client/server via NeoForge's attachment system.
 *
 * Usage:
 *   CompoundTag tag = entity.getData(DIAttachments.PET_DATA);
 *   tag.putInt("SomeKey", value);
 *   entity.setData(DIAttachments.PET_DATA, tag);
 */
public class DIAttachments {

    public static final DeferredRegister<AttachmentType<?>> DEF_REG =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, DomesticationMod.MODID);

    /**
     * Main pet data attachment. Stores all collar enchantments, timers, bed positions,
     * and other per-entity state that was previously in Citadel's entity data.
     *
     * Serialized as CompoundTag - automatically saved/loaded.
     */
    public static final Supplier<AttachmentType<CompoundTag>> PET_DATA = DEF_REG.register("pet_data",
            () -> AttachmentType.builder(() -> new CompoundTag())
                    .serialize(net.minecraft.nbt.CompoundTag.CODEC)
                    .copyOnDeath()
                    .build());
}
