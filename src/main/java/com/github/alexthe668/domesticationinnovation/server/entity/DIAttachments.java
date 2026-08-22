package com.github.alexthe668.domesticationinnovation.server.entity;

import com.github.alexthe668.domesticationinnovation.DomesticationMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.function.Supplier;

/**
 * NeoForge entity attachment system - replaces Citadel's CitadelEntityData.
 *
 * All pet enchantment data, collar state, bed positions, etc. are stored
 * in a single CompoundTag attachment on each entity. This data automatically
 * persists across saves; it is NOT synced automatically - the server pushes it
 * to clients via {@link com.github.alexthe668.domesticationinnovation.server.misc.DIPetDataSyncPacket}
 * whenever it changes and when a player starts tracking the entity.
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

    /**
     * Server-side copy of the pet data as last broadcast to tracking clients.
     * Transient (never saved) - used only to skip redundant sync packets when
     * a setter writes an unchanged tag.
     */
    public static final Supplier<AttachmentType<CompoundTag>> LAST_SYNCED_PET_DATA = DEF_REG.register("last_synced_pet_data",
            () -> AttachmentType.builder(() -> new CompoundTag()).build());

    /**
     * Decoded view of a pet's StoredPetEnchantments list, paired with the exact
     * ListTag instance it was decoded from so readers can tell at identity-check
     * speed whether the decode is still current.
     */
    public record DecodedEnchants(ListTag source, Map<ResourceLocation, Integer> levels) {
        static final DecodedEnchants EMPTY = new DecodedEnchants(new ListTag(), Map.of());
    }

    /**
     * Transient (never saved, never synced) decoded-enchant holder, living on
     * the entity so a level query is one attachment lookup plus an identity
     * check instead of an NBT re-parse. Managed exclusively by
     * {@link TameableUtils}; only entities that actually carry an enchant list
     * ever get this attached.
     */
    public static final Supplier<AttachmentType<DecodedEnchants>> DECODED_ENCHANTS = DEF_REG.register("decoded_enchants",
            () -> AttachmentType.builder(() -> DecodedEnchants.EMPTY).build());

    /**
     * Reads the pet data tag without creating the attachment, or null when the
     * entity has none. The zero-allocation primitive behind {@link #readPetData}
     * for hot paths that run for arbitrary living entities every tick/frame.
     */
    @Nullable
    public static CompoundTag peekPetData(LivingEntity entity) {
        return entity.getExistingData(PET_DATA).orElse(null);
    }

    /**
     * Reads the pet data tag without creating the attachment. Unlike
     * {@code entity.getData(PET_DATA)}, which permanently attaches (and saves) a
     * default empty tag on first read, this returns a throwaway empty tag for
     * entities that have no pet data. Use for read-only lookups so the 95% of
     * living entities that are not pets stay attachment-free; write paths should
     * keep using getData/setData.
     */
    public static CompoundTag readPetData(LivingEntity entity) {
        CompoundTag existing = peekPetData(entity);
        return existing == null ? new CompoundTag() : existing;
    }

    public static final String SNAPSHOT_KEY = "DIPetData";

    /**
     * Attachment data is serialized only by {@code Entity.saveWithoutId}, so
     * snapshots built with {@code addAdditionalSaveData} (pet bed respawn,
     * recall ball, zombie pets) lose the collar unless it is carried
     * explicitly. These two are the carry path: call write after taking the
     * snapshot and read after rebuilding from it.
     */
    public static void writePetDataTo(LivingEntity entity, CompoundTag snapshot) {
        CompoundTag pet = readPetData(entity);
        if (!pet.isEmpty()) {
            snapshot.put(SNAPSHOT_KEY, pet.copy());
        }
    }

    public static void readPetDataFrom(LivingEntity entity, CompoundTag snapshot) {
        if (snapshot.contains(SNAPSHOT_KEY)) {
            entity.setData(PET_DATA, snapshot.getCompound(SNAPSHOT_KEY).copy());
        }
    }
}
