package com.github.alexthe668.domesticationinnovation.server.misc;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.core.registries.BuiltInRegistries;

import javax.annotation.Nullable;
import java.util.UUID;

public class LanternRequest {
    private String entityType;
    private long timestamp;
    private String nametag;

    private UUID petUUID;
    private UUID ownerUUID;

    private BlockPos chunkPosition;

    // dimension the pet unloaded in; empty for requests saved before this was tracked
    private String dimension;
    // snapshot of the pet's save data, used to rebuild it if the entity itself is unrecoverable
    @Nullable
    private CompoundTag entitySnapshot;

    // per-request retrieval state, rebuilt each session (never persisted)
    private boolean chunksForced;
    private int ticksWaited;

    public LanternRequest(UUID petUUID, String entityType, UUID ownerUUID, BlockPos chunkPosition, long timestamp, String nametag) {
        this(petUUID, entityType, ownerUUID, chunkPosition, timestamp, nametag, "", null);
    }

    public LanternRequest(UUID petUUID, String entityType, UUID ownerUUID, BlockPos chunkPosition, long timestamp, String nametag, String dimension, @Nullable CompoundTag entitySnapshot) {
        this.petUUID = petUUID;
        this.entityType = entityType;
        this.chunkPosition = chunkPosition;
        this.ownerUUID = ownerUUID;
        this.timestamp = timestamp;
        this.nametag = nametag;
        this.dimension = dimension == null ? "" : dimension;
        this.entitySnapshot = entitySnapshot;
    }

    public UUID getPetUUID() {
        return petUUID;
    }

    public String getEntityTypeLoc() {
        return this.entityType;
    }

    public EntityType getEntityType() {
        return BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse(this.entityType));
    }

    public UUID getOwnerUUID() {
        return ownerUUID;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getNametag() {
        return this.nametag;
    }

    public BlockPos getChunkPosition() {
        return chunkPosition;
    }

    public String getDimension() {
        return this.dimension;
    }

    /**
     * Whether this request may be serviced from the given level. Requests saved
     * before the dimension was recorded match any level, preserving old behavior.
     */
    public boolean matchesDimension(Level level) {
        return this.dimension.isEmpty() || this.dimension.equals(level.dimension().toString());
    }

    @Nullable
    public CompoundTag getEntitySnapshot() {
        return this.entitySnapshot;
    }

    public boolean areChunksForced() {
        return this.chunksForced;
    }

    public void setChunksForced(boolean chunksForced) {
        this.chunksForced = chunksForced;
    }

    /**
     * Counts one tick spent waiting for the pet's chunks to produce the entity.
     * Returns the total ticks waited so far for this request.
     */
    public int tickWaitTime() {
        return ++this.ticksWaited;
    }

    public String toString(){
        if(getNametag() == null || getNametag().isEmpty()){
            return this.entityType;
        }else{
            return getNametag() + "|" + this.entityType;
        }
    }
}
