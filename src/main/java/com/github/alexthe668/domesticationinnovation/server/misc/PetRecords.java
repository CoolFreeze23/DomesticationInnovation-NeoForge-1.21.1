package com.github.alexthe668.domesticationinnovation.server.misc;

import com.github.alexthe668.domesticationinnovation.DomesticationMod;
import com.github.alexthe668.domesticationinnovation.server.entity.TameableUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Where every tamed pet was last seen, for the pet compass. Pets report in every few seconds while
 * they tick, again when they unload or change dimension, and when they die: a pet that died with a
 * bed to come back to is kept as "resting at its bed" until it respawns, any other dead pet is
 * dropped. Pets tamed before this existed show up the first time they're loaded.
 */
public class PetRecords extends SavedData {

    private static final String IDENTIFIER = "domesticationinnovation_pet_records";

    /** One pet. {@code pos} is the bed for a pet that is resting, its last position otherwise. */
    public record Record(UUID pet, UUID owner, String type, String name, String dimension, BlockPos pos,
                         boolean resting, long seen) {
    }

    private final Map<UUID, Record> records = new HashMap<>();

    private PetRecords() {
    }

    public static PetRecords get(MinecraftServer server) {
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        return overworld.getDataStorage().computeIfAbsent(new SavedData.Factory<>(PetRecords::new, PetRecords::load), IDENTIFIER);
    }

    /** The dimension id ("minecraft:the_nether") from either a dimension key's toString or its id. */
    public static String dimensionId(String dimension) {
        if (dimension == null || dimension.isEmpty()) {
            return "minecraft:overworld";
        }
        int slash = dimension.indexOf(" / ");
        if (slash >= 0) {
            String id = dimension.substring(slash + 3);
            return id.endsWith("]") ? id.substring(0, id.length() - 1) : id;
        }
        return dimension;
    }

    /** Records where a living pet is now; a pet that is no longer tamed or owned is forgotten. */
    public void update(LivingEntity pet) {
        UUID owner = TameableUtils.isTamed(pet) ? TameableUtils.getOwnerUUIDOf(pet) : null;
        if (owner == null) {
            remove(pet.getUUID());
            return;
        }
        put(new Record(pet.getUUID(), owner, BuiltInRegistries.ENTITY_TYPE.getKey(pet.getType()).toString(),
                pet.hasCustomName() ? pet.getCustomName().getString() : "",
                pet.level().dimension().location().toString(), pet.blockPosition(), false, pet.level().getGameTime()));
    }

    /** The pet died with a respawn queued at its bed: show it resting there until it's back. */
    public void markResting(LivingEntity pet, BlockPos bedPos, String bedDimension) {
        UUID owner = TameableUtils.getOwnerUUIDOf(pet);
        if (owner == null) {
            remove(pet.getUUID());
            return;
        }
        put(new Record(pet.getUUID(), owner, BuiltInRegistries.ENTITY_TYPE.getKey(pet.getType()).toString(),
                pet.hasCustomName() ? pet.getCustomName().getString() : "",
                dimensionId(bedDimension), bedPos, true, pet.level().getGameTime()));
    }

    /**
     * A bed respawn builds a fresh entity with a new UUID, so the resting record (keyed by the old
     * one) is replaced by the new pet's.
     */
    public void onRespawnedAtBed(Level level, BlockPos bedPos, LivingEntity fresh) {
        String dimension = level.dimension().location().toString();
        UUID owner = TameableUtils.getOwnerUUIDOf(fresh);
        String type = BuiltInRegistries.ENTITY_TYPE.getKey(fresh.getType()).toString();
        if (records.values().removeIf(r -> r.resting() && r.pos().equals(bedPos) && r.dimension().equals(dimension)
                && r.type().equals(type) && (owner == null || owner.equals(r.owner())))) {
            setDirty();
        }
        update(fresh);
    }

    public void remove(UUID pet) {
        if (records.remove(pet) != null) {
            setDirty();
        }
    }

    @Nullable
    public Record get(UUID pet) {
        return records.get(pet);
    }

    public List<Record> forOwner(UUID owner) {
        List<Record> list = new ArrayList<>();
        for (Record record : records.values()) {
            if (record.owner().equals(owner)) {
                list.add(record);
            }
        }
        return list;
    }

    private void put(Record record) {
        Record previous = records.put(record.pet(), record);
        // the last-seen time alone isn't worth a save
        if (previous == null || !previous.pos().equals(record.pos()) || !previous.dimension().equals(record.dimension())
                || !previous.owner().equals(record.owner()) || !previous.name().equals(record.name())
                || previous.resting() != record.resting()) {
            setDirty();
        }
    }

    public static PetRecords load(CompoundTag nbt, HolderLookup.Provider registries) {
        PetRecords data = new PetRecords();
        ListTag list = nbt.getList("Pets", 10);
        for (int i = 0; i < list.size(); i++) {
            try {
                CompoundTag tag = list.getCompound(i);
                Record record = new Record(tag.getUUID("Pet"), tag.getUUID("Owner"), tag.getString("Type"), tag.getString("Name"),
                        tag.getString("Dimension"), new BlockPos(tag.getInt("X"), tag.getInt("Y"), tag.getInt("Z")),
                        tag.getBoolean("Resting"), tag.getLong("Seen"));
                data.records.put(record.pet(), record);
            } catch (Exception e) {
                DomesticationMod.LOGGER.error("Skipping malformed pet compass entry {} in world data", i, e);
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag compound, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Record record : records.values()) {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("Pet", record.pet());
            tag.putUUID("Owner", record.owner());
            tag.putString("Type", record.type());
            tag.putString("Name", record.name());
            tag.putString("Dimension", record.dimension());
            tag.putInt("X", record.pos().getX());
            tag.putInt("Y", record.pos().getY());
            tag.putInt("Z", record.pos().getZ());
            tag.putBoolean("Resting", record.resting());
            tag.putLong("Seen", record.seen());
            list.add(tag);
        }
        compound.put("Pets", list);
        return compound;
    }
}
