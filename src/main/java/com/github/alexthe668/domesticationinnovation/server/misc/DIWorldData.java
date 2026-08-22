package com.github.alexthe668.domesticationinnovation.server.misc;

import com.github.alexthe668.domesticationinnovation.DomesticationMod;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import java.util.*;

public class DIWorldData extends SavedData {

    private static final String IDENTIFIER = "domesticationinnovation_world_data";
    private final List<RespawnRequest> respawnRequestList = new ArrayList<>();
    private final List<LanternRequest> lanternRequestList = new ArrayList<>();

    private DIWorldData() {
        super();
    }

    public static DIWorldData get(Level world) {
        if (world instanceof ServerLevel) {
            ServerLevel overworld = world.getServer().getLevel(Level.OVERWORLD);
            DimensionDataStorage storage = overworld.getDataStorage();
            return storage.computeIfAbsent(new SavedData.Factory<>(DIWorldData::new, DIWorldData::load), IDENTIFIER);
        }
        return null;
    }

    public static DIWorldData load(CompoundTag nbt, net.minecraft.core.HolderLookup.Provider registries) {
        DIWorldData data = new DIWorldData();
        // each entry is parsed independently so one corrupt entry (e.g. from a removed mod)
        // is logged and skipped instead of discarding every pending request in the world
        if (nbt.contains("RespawnList")) {
            ListTag listtag = nbt.getList("RespawnList", 10);
            for (int i = 0; i < listtag.size(); ++i) {
                try {
                    CompoundTag innerTag = listtag.getCompound(i);
                    data.respawnRequestList.add(new RespawnRequest(innerTag.getString("EntityType"), innerTag.getString("DimensionIn"), innerTag.getCompound("EntityData"),
                            new BlockPos(innerTag.getInt("X"), innerTag.getInt("Y"), innerTag.getInt("Z")), innerTag.getLong("Timestamp"), innerTag.getString("EntityNametag")));
                } catch (Exception e) {
                    DomesticationMod.LOGGER.error("Skipping malformed pet respawn entry {} in world data", i, e);
                }
            }
        }
        if (nbt.contains("LanternList")) {
            ListTag listtag = nbt.getList("LanternList", 10);
            for (int i = 0; i < listtag.size(); ++i) {
                try {
                    CompoundTag innerTag = listtag.getCompound(i);
                    CompoundTag snapshot = innerTag.contains("EntityData", 10) ? innerTag.getCompound("EntityData") : null;
                    data.lanternRequestList.add(new LanternRequest(innerTag.getUUID("PetUUID"), innerTag.getString("EntityType"), innerTag.getUUID("OwnerUUID"),
                            new BlockPos(innerTag.getInt("X"), innerTag.getInt("Y"), innerTag.getInt("Z")), innerTag.getLong("Timestamp"), innerTag.getString("EntityNametag"),
                            innerTag.getString("DimensionIn"), snapshot));
                } catch (Exception e) {
                    DomesticationMod.LOGGER.error("Skipping malformed wayward lantern entry {} in world data", i, e);
                }
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag compound, net.minecraft.core.HolderLookup.Provider registries) {
        if (!this.respawnRequestList.isEmpty()) {
            ListTag listTag = new ListTag();
            for(RespawnRequest request : respawnRequestList){
                CompoundTag tag = new CompoundTag();
                tag.putString("EntityType", request.getEntityTypeLoc());
                tag.putString("DimensionIn", request.getDimension());
                tag.put("EntityData", request.getEntityData());
                tag.putInt("X", request.getBedPosition().getX());
                tag.putInt("Y", request.getBedPosition().getY());
                tag.putInt("Z", request.getBedPosition().getZ());
                tag.putLong("Timestamp", request.getTimestamp());
                tag.putString("EntityNametag", request.getNametag());
                listTag.add(tag);
            }
            compound.put("RespawnList", listTag);
        }
        if (!this.lanternRequestList.isEmpty()) {
            ListTag listTag = new ListTag();
            for(LanternRequest request : lanternRequestList){
                CompoundTag tag = new CompoundTag();
                tag.putUUID("PetUUID", request.getPetUUID());
                tag.putString("EntityType", request.getEntityTypeLoc());
                tag.putUUID("OwnerUUID", request.getOwnerUUID());
                tag.putLong("Timestamp", request.getTimestamp());
                tag.putString("EntityNametag", request.getNametag());
                tag.putInt("X", request.getChunkPosition().getX());
                tag.putInt("Y", request.getChunkPosition().getY());
                tag.putInt("Z", request.getChunkPosition().getZ());
                if (!request.getDimension().isEmpty()) {
                    tag.putString("DimensionIn", request.getDimension());
                }
                if (request.getEntitySnapshot() != null) {
                    tag.put("EntityData", request.getEntitySnapshot());
                }
                listTag.add(tag);
            }
            compound.put("LanternList", listTag);
        }
        return compound;
    }

    public void addRespawnRequest(RespawnRequest request){
        this.respawnRequestList.add(request);
        this.setDirty();
    }

    public void removeRespawnRequest(RespawnRequest request){
        this.respawnRequestList.remove(request);
        this.setDirty();
    }
    public List<RespawnRequest> getRespawnRequestsFor(Level level, BlockPos pos){
        List<RespawnRequest> list = new ArrayList<>();
        String dimension = level.dimension().toString();
        for(RespawnRequest request : this.respawnRequestList){
            if(dimension.equals(request.getDimension()) && pos.equals(request.getBedPosition())){
                list.add(request);
            }
        }
        return list;
    }

    public void addLanternRequest(LanternRequest request){
        this.lanternRequestList.add(request);
        this.setDirty();
    }

    public void removeLanternRequest(LanternRequest request){
        this.lanternRequestList.remove(request);
        this.setDirty();
    }

    public void removeMatchingLanternRequests(UUID reloaded){
        if(this.lanternRequestList.removeIf(request -> request.getPetUUID().equals(reloaded))){
            this.setDirty();
        }
    }

    public List<LanternRequest> getLanternRequestsFor(UUID uuid){
        List<LanternRequest> list = new ArrayList<>();
        for(LanternRequest request : this.lanternRequestList){
            if(uuid.equals(request.getOwnerUUID())){
                list.add(request);
            }
        }
        return list;
    }
}
