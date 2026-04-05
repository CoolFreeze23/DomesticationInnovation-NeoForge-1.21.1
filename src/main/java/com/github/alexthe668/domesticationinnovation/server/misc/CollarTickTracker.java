package com.github.alexthe668.domesticationinnovation.server.misc;

import net.minecraft.world.entity.Entity;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class CollarTickTracker {

    private final Map<UUID, Integer> blockedCollarTagUpdates = new HashMap<>();
    public void addBlockedEntityTick(UUID uuid, int duration){
        this.blockedCollarTagUpdates.put(uuid, duration);
    }

    public boolean isEntityBlocked(Entity entity){
        return this.blockedCollarTagUpdates.getOrDefault(entity.getUUID(), 0) > 0;
    }

    public void tick(){
        if(!blockedCollarTagUpdates.isEmpty()){
            // Fixed: original code threw ConcurrentModificationException
            blockedCollarTagUpdates.entrySet().removeIf(entry -> {
                int remaining = entry.getValue() - 1;
                if(remaining < 0) return true;
                entry.setValue(remaining);
                return false;
            });
        }
    }
}
