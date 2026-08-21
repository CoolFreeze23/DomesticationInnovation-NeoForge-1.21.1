package com.github.alexthe668.domesticationinnovation.server.misc;

import com.github.alexthe668.domesticationinnovation.DomesticationMod;
import com.github.alexthe668.domesticationinnovation.server.entity.DIAttachments;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Network packet registration and sync helper.
 * Replaces Citadel's PropertiesMessage-based sync.
 */
@EventBusSubscriber(modid = DomesticationMod.MODID, bus = EventBusSubscriber.Bus.MOD)
public class DINetworkRegistry {

    @SubscribeEvent
    public static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(DomesticationMod.MODID).versioned("1.0");
        registrar.playToClient(
                DIPetDataSyncPacket.TYPE,
                DIPetDataSyncPacket.STREAM_CODEC,
                DIPetDataSyncPacket::handleClient
        );
    }

    /**
     * Sync pet data to all nearby players tracking this entity.
     * Call this whenever pet attachment data changes on the server.
     * No-ops when the tag is identical to what trackers already received,
     * so per-tick setters that rewrite an unchanged value cost no bandwidth.
     */
    public static void syncPetData(LivingEntity entity) {
        if (entity.level() instanceof ServerLevel) {
            CompoundTag data = entity.getExistingData(DIAttachments.PET_DATA).orElse(null);
            if (data == null) {
                // Never written - trackers already have the default empty tag
                return;
            }
            CompoundTag lastSynced = entity.getExistingData(DIAttachments.LAST_SYNCED_PET_DATA).orElse(null);
            if (data.equals(lastSynced)) {
                return;
            }
            // Snapshot once: packet payloads are encoded on the network thread,
            // so the packet must never share the live attachment tag, which the
            // game thread keeps mutating. The snapshot itself is never mutated,
            // so it can double as the last-synced record.
            CompoundTag snapshot = data.copy();
            entity.setData(DIAttachments.LAST_SYNCED_PET_DATA, snapshot);
            DIPetDataSyncPacket packet = new DIPetDataSyncPacket(entity.getId(), snapshot);
            PacketDistributor.sendToPlayersTrackingEntity(entity, packet);
            // Also send to the entity if it's a player (shouldn't be, but safety)
            if (entity instanceof ServerPlayer player) {
                PacketDistributor.sendToPlayer(player, packet);
            }
        }
    }

    /**
     * Store pet data on the server without broadcasting to tracking clients.
     * Intended for server-only counter fields that no client-side code reads;
     * the next synced write ships the full tag anyway, so the data is never
     * lost - the broadcast is just deferred.
     */
    public static void setPetDataNoSync(LivingEntity entity, CompoundTag tag) {
        entity.setData(DIAttachments.PET_DATA, tag);
    }
}
