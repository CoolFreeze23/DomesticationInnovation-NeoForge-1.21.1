package com.github.alexthe668.domesticationinnovation.server.misc;

import com.github.alexthe668.domesticationinnovation.DomesticationMod;
import com.github.alexthe668.domesticationinnovation.server.entity.DIAttachments;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Syncs pet attachment data from server → client.
 * Replaces Citadel's PropertiesMessage.
 */
public record DIPetDataSyncPacket(int entityId, CompoundTag data) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<DIPetDataSyncPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(DomesticationMod.MODID, "pet_data_sync"));

    public static final StreamCodec<FriendlyByteBuf, DIPetDataSyncPacket> STREAM_CODEC =
            StreamCodec.of(DIPetDataSyncPacket::write, DIPetDataSyncPacket::read);

    public static DIPetDataSyncPacket read(FriendlyByteBuf buf) {
        return new DIPetDataSyncPacket(buf.readVarInt(), buf.readNbt());
    }

    public static void write(FriendlyByteBuf buf, DIPetDataSyncPacket packet) {
        buf.writeVarInt(packet.entityId);
        buf.writeNbt(packet.data);
    }

    public static void handleClient(DIPetDataSyncPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (Minecraft.getInstance().level != null) {
                Entity entity = Minecraft.getInstance().level.getEntity(packet.entityId);
                if (entity != null) {
                    entity.setData(DIAttachments.PET_DATA, packet.data);
                }
            }
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
