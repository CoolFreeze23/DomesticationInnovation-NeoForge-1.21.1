package com.github.alexthe668.domesticationinnovation.server.misc;

import com.github.alexthe668.domesticationinnovation.DomesticationMod;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/** Client to server: a pet compass button press. The server checks ownership and its own config. */
public record PetCompassActionPacket(UUID pet, byte action) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<PetCompassActionPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(DomesticationMod.MODID, "pet_compass_action"));

    public static final StreamCodec<FriendlyByteBuf, PetCompassActionPacket> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, PetCompassActionPacket::pet,
            ByteBufCodecs.BYTE, PetCompassActionPacket::action,
            PetCompassActionPacket::new
    );

    public static void handleServer(PetCompassActionPacket packet, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            PetCompassService.handleAction(player, packet.pet(), packet.action());
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
