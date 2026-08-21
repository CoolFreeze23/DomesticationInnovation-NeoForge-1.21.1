package com.github.alexthe668.domesticationinnovation.server.misc;

import com.github.alexthe668.domesticationinnovation.server.entity.DIAttachments;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Pushes existing pet attachment data to each player that starts tracking an
 * entity. Attachments are not synced by the engine, so without this a client
 * that logs in near a pet, returns from another dimension, or walks into
 * tracking range would only receive the pet's data on its next server-side
 * mutation - leaving collar overlays, enchant nametags and client-side enchant
 * checks empty for pets whose enchants never tick a counter.
 */
public class DIPetDataTrackingHandler {

    @SubscribeEvent
    public void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getTarget() instanceof LivingEntity living) {
            CompoundTag tag = living.getExistingData(DIAttachments.PET_DATA).orElse(null);
            if (tag != null && !tag.isEmpty()) {
                // Copy: encoding happens on the network thread, so the packet
                // must not share the live attachment tag
                PacketDistributor.sendToPlayer(player, new DIPetDataSyncPacket(living.getId(), tag.copy()));
            }
        }
    }
}
