package com.github.alexthe668.domesticationinnovation.server.misc;

import com.github.alexthe668.domesticationinnovation.DomesticationMod;
import com.github.alexthe668.domesticationinnovation.server.entity.DIAttachments;
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
     */
    public static void syncPetData(LivingEntity entity) {
        if (entity.level() instanceof ServerLevel serverLevel) {
            DIPetDataSyncPacket packet = new DIPetDataSyncPacket(
                    entity.getId(),
                    entity.getData(DIAttachments.PET_DATA)
            );
            PacketDistributor.sendToPlayersTrackingEntity(entity, packet);
            // Also send to the entity if it's a player (shouldn't be, but safety)
            if (entity instanceof ServerPlayer player) {
                PacketDistributor.sendToPlayer(player, packet);
            }
        }
    }
}
