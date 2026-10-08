package com.github.alexthe668.domesticationinnovation.server.misc;

import com.github.alexthe668.domesticationinnovation.DomesticationMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;
import java.util.UUID;

/**
 * Server to client: the player's pets for the pet compass screen, plus which of its buttons the
 * server allows. Opens the screen, or refreshes it if it's already open; a {@code refreshOnly} list
 * (sent when a recall finishes) only updates a screen that is still open.
 */
public record PetCompassListPacket(List<Entry> entries, boolean allowGoTo, boolean allowRecall,
                                   boolean allowCrossDimension, boolean refreshOnly) implements CustomPacketPayload {

    public static final byte ACTIVE = 0;
    public static final byte LAST_SEEN = 1;
    public static final byte RESTING = 2;

    /** {@code health} is -1 when the pet isn't loaded. */
    public record Entry(UUID pet, Component name, ResourceLocation dimension, BlockPos pos, byte status,
                        float health, float maxHealth) {

        public static final StreamCodec<RegistryFriendlyByteBuf, Entry> STREAM_CODEC = StreamCodec.ofMember(Entry::write, Entry::read);

        private void write(RegistryFriendlyByteBuf buf) {
            UUIDUtil.STREAM_CODEC.encode(buf, pet);
            ComponentSerialization.STREAM_CODEC.encode(buf, name);
            buf.writeResourceLocation(dimension);
            buf.writeBlockPos(pos);
            buf.writeByte(status);
            buf.writeFloat(health);
            buf.writeFloat(maxHealth);
        }

        private static Entry read(RegistryFriendlyByteBuf buf) {
            return new Entry(UUIDUtil.STREAM_CODEC.decode(buf), ComponentSerialization.STREAM_CODEC.decode(buf),
                    buf.readResourceLocation(), buf.readBlockPos(), buf.readByte(), buf.readFloat(), buf.readFloat());
        }
    }

    public static final CustomPacketPayload.Type<PetCompassListPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(DomesticationMod.MODID, "pet_compass_list"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PetCompassListPacket> STREAM_CODEC = StreamCodec.composite(
            Entry.STREAM_CODEC.apply(ByteBufCodecs.list(256)), PetCompassListPacket::entries,
            ByteBufCodecs.BOOL, PetCompassListPacket::allowGoTo,
            ByteBufCodecs.BOOL, PetCompassListPacket::allowRecall,
            ByteBufCodecs.BOOL, PetCompassListPacket::allowCrossDimension,
            ByteBufCodecs.BOOL, PetCompassListPacket::refreshOnly,
            PetCompassListPacket::new
    );

    public static void handleClient(PetCompassListPacket packet, IPayloadContext context) {
        // the proxy keeps this class free of client-only references, so it loads on dedicated servers
        DomesticationMod.PROXY.openPetCompass(packet);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
