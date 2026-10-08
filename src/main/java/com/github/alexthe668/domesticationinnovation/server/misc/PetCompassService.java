package com.github.alexthe668.domesticationinnovation.server.misc;

import com.github.alexthe668.domesticationinnovation.DomesticationMod;
import com.github.alexthe668.domesticationinnovation.server.CommonProxy;
import com.github.alexthe668.domesticationinnovation.server.block.DIChunkLoadingRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The server side of the pet compass: the list a player sees, and the two buttons. "Go to" puts the
 * player where the pet is (or was last seen); "recall" brings the pet to the player, loading its
 * chunk for a moment when it isn't loaded. Everything is checked against the pet records' owner
 * and the server's config, never against what the client sent.
 */
public final class PetCompassService {

    public static final byte GO_TO = 0;
    public static final byte RECALL = 1;

    /** How long a recall waits for an unloaded pet's chunk and entities to load. */
    private static final int SEARCH_TICKS = 200;
    private static final int MAX_ENTRIES = 256;

    private record PendingRecall(UUID pet, UUID player, ResourceKey<Level> level, ChunkPos chunk, long startedAt) {
    }

    private static final List<PendingRecall> PENDING = new ArrayList<>();
    private static final Map<UUID, Long> LAST_USE = new HashMap<>();

    private PetCompassService() {
    }

    public static void openFor(ServerPlayer player) {
        if (!DomesticationMod.CONFIG.petCompass.get()) {
            player.displayClientMessage(Component.translatable("message.domesticationinnovation.pet_compass.disabled"), true);
            return;
        }
        sendList(player, false);
    }

    /** Updates the player's compass screen if it's still open, without opening it. */
    public static void refreshFor(ServerPlayer player) {
        if (DomesticationMod.CONFIG.petCompass.get()) {
            sendList(player, true);
        }
    }

    private static void sendList(ServerPlayer player, boolean refreshOnly) {
        // a connection that never negotiated this mod's packets (a fake player, say) can't take the list
        if (player.connection == null || !player.connection.hasChannel(PetCompassListPacket.TYPE)) {
            return;
        }
        PacketDistributor.sendToPlayer(player, new PetCompassListPacket(entriesFor(player),
                DomesticationMod.CONFIG.compassTeleportToPet.get(), DomesticationMod.CONFIG.compassRecallPet.get(),
                DomesticationMod.CONFIG.compassCrossDimension.get(), refreshOnly));
    }

    public static List<PetCompassListPacket.Entry> entriesFor(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        PetRecords records = PetRecords.get(server);
        List<PetCompassListPacket.Entry> entries = new ArrayList<>();
        for (PetRecords.Record record : records.forOwner(player.getUUID())) {
            ResourceLocation dimension = ResourceLocation.tryParse(record.dimension());
            if (dimension == null) {
                continue;
            }
            ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
            Entity live = !record.resting() && level != null ? level.getEntity(record.pet()) : null;
            if (live instanceof LivingEntity living && living.isAlive()) {
                // a loaded pet is listed as it is right now, and its record catches up
                records.update(living);
                entries.add(new PetCompassListPacket.Entry(record.pet(), living.getDisplayName(), dimension,
                        living.blockPosition(), PetCompassListPacket.ACTIVE, living.getHealth(), living.getMaxHealth()));
            } else {
                entries.add(new PetCompassListPacket.Entry(record.pet(), nameOf(record), dimension, record.pos(),
                        record.resting() ? PetCompassListPacket.RESTING : PetCompassListPacket.LAST_SEEN, -1.0F, -1.0F));
            }
        }
        entries.sort(Comparator.comparingInt((PetCompassListPacket.Entry e) -> e.status())
                .thenComparing(e -> e.name().getString(), String.CASE_INSENSITIVE_ORDER));
        return entries.size() > MAX_ENTRIES ? new ArrayList<>(entries.subList(0, MAX_ENTRIES)) : entries;
    }

    public static void handleAction(ServerPlayer player, UUID petId, byte action) {
        if (!DomesticationMod.CONFIG.petCompass.get()) {
            player.displayClientMessage(Component.translatable("message.domesticationinnovation.pet_compass.disabled"), true);
            return;
        }
        if (action == GO_TO && !DomesticationMod.CONFIG.compassTeleportToPet.get()) {
            player.displayClientMessage(Component.translatable("message.domesticationinnovation.pet_compass.go_disabled"), true);
            return;
        }
        if (action == RECALL && !DomesticationMod.CONFIG.compassRecallPet.get()) {
            player.displayClientMessage(Component.translatable("message.domesticationinnovation.pet_compass.recall_disabled"), true);
            return;
        }
        MinecraftServer server = player.getServer();
        PetRecords.Record record = PetRecords.get(server).get(petId);
        if (record == null || !record.owner().equals(player.getUUID())) {
            player.displayClientMessage(Component.translatable("message.domesticationinnovation.pet_compass.not_yours"), true);
            refreshFor(player);
            return;
        }
        Component name = nameOf(record);
        if (record.resting()) {
            player.displayClientMessage(Component.translatable("message.domesticationinnovation.pet_compass.resting", name), true);
            return;
        }
        ResourceLocation dimension = ResourceLocation.tryParse(record.dimension());
        ServerLevel petLevel = dimension == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
        if (petLevel == null) {
            player.displayClientMessage(Component.translatable("message.domesticationinnovation.pet_compass.not_found", name), true);
            return;
        }
        if (petLevel != player.serverLevel() && !DomesticationMod.CONFIG.compassCrossDimension.get()) {
            player.displayClientMessage(Component.translatable("message.domesticationinnovation.pet_compass.other_dimension", name), true);
            return;
        }
        int cooldown = DomesticationMod.CONFIG.compassCooldownSeconds.get() * 20;
        long now = server.overworld().getGameTime();
        Long last = LAST_USE.get(player.getUUID());
        if (cooldown > 0 && last != null && now - last < cooldown) {
            long seconds = (cooldown - (now - last) + 19) / 20;
            player.displayClientMessage(Component.translatable("message.domesticationinnovation.pet_compass.cooldown", seconds), true);
            return;
        }
        LAST_USE.put(player.getUUID(), now);

        Entity live = petLevel.getEntity(petId);
        if (live instanceof LivingEntity living && living.isAlive()) {
            name = living.getDisplayName();
        } else {
            live = null;
        }
        if (action == GO_TO) {
            Vec3 destination = live != null ? live.position() : Vec3.atBottomCenterOf(record.pos());
            if (petLevel.hasChunkAt(BlockPos.containing(destination))) {
                destination = safeSpot(petLevel, player, destination);
            }
            player.teleportTo(petLevel, destination.x, destination.y, destination.z, player.getYRot(), player.getXRot());
            player.displayClientMessage(Component.translatable("message.domesticationinnovation.pet_compass.went_to", name), false);
        } else if (live != null) {
            recall((LivingEntity) live, player);
        } else {
            startSearch(petLevel, record, player);
            player.displayClientMessage(Component.translatable("message.domesticationinnovation.pet_compass.searching", name), false);
        }
    }

    /** Brings a loaded pet to the player: in place within a dimension, through the pet portal queue across one. */
    public static void recall(LivingEntity pet, ServerPlayer player) {
        if (pet.hasIndirectPassenger(player)) {
            return;
        }
        pet.stopRiding();
        pet.ejectPassengers();
        if (pet.level() == player.level()) {
            Vec3 spot = safeSpot(player.serverLevel(), pet, player.position());
            pet.teleportTo(spot.x, spot.y, spot.z);
            pet.fallDistance = 0.0F;
            if (pet instanceof Mob mob) {
                mob.getNavigation().stop();
            }
            PetRecords.get(player.getServer()).update(pet);
        } else {
            CommonProxy.teleportingPets.add(new CommonProxy.PendingPetTeleport(pet, player.serverLevel(), player.getUUID()));
        }
        player.displayClientMessage(Component.translatable("message.domesticationinnovation.pet_compass.recalled", pet.getDisplayName()), false);
        refreshFor(player);
    }

    /** Runs every server level tick: finishes recalls whose pet has loaded, gives up on the rest after a while. */
    public static void tick(ServerLevel level) {
        if (PENDING.isEmpty()) {
            return;
        }
        Iterator<PendingRecall> iterator = PENDING.iterator();
        while (iterator.hasNext()) {
            PendingRecall pending = iterator.next();
            if (pending.level() != level.dimension()) {
                continue;
            }
            Entity pet = level.getEntity(pending.pet());
            boolean found = pet instanceof LivingEntity living && living.isAlive();
            if (!found && level.getGameTime() - pending.startedAt() < SEARCH_TICKS) {
                continue;
            }
            iterator.remove();
            DIChunkLoadingRegistry.PET_TICKET_CONTROLLER.forceChunk(level, ticketKey(pending.pet()),
                    pending.chunk().x, pending.chunk().z, false, true);
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(pending.player());
            if (player == null) {
                continue;
            }
            if (found) {
                recall((LivingEntity) pet, player);
            } else {
                PetRecords.Record record = PetRecords.get(level.getServer()).get(pending.pet());
                Component name = record != null ? nameOf(record) : Component.translatable("gui.domesticationinnovation.pet_compass.unnamed");
                player.displayClientMessage(Component.translatable("message.domesticationinnovation.pet_compass.not_found", name), false);
                refreshFor(player);
            }
        }
    }

    private static void startSearch(ServerLevel level, PetRecords.Record record, ServerPlayer player) {
        ChunkPos chunk = new ChunkPos(record.pos());
        PENDING.removeIf(p -> p.pet().equals(record.pet()));
        PENDING.add(new PendingRecall(record.pet(), player.getUUID(), level.dimension(), chunk, level.getGameTime()));
        DIChunkLoadingRegistry.PET_TICKET_CONTROLLER.forceChunk(level, ticketKey(record.pet()), chunk.x, chunk.z, true, true);
    }

    /** Its own ticket owner per pet, so ending a search never drops a wayward lantern's ticket for the same pet. */
    private static UUID ticketKey(UUID pet) {
        return UUID.nameUUIDFromBytes(("pet_compass:" + pet).getBytes(StandardCharsets.UTF_8));
    }

    private static Vec3 safeSpot(Level level, Entity entity, Vec3 start) {
        EntityDimensions size = entity.getDimensions(entity.getPose());
        AABB box = new AABB(-size.width() / 2.0F, 0, -size.width() / 2.0F, size.width() / 2.0F, size.height(), size.width() / 2.0F);
        Vec3 spot = start;
        for (int i = 0; i < 16 && !level.noCollision(entity, box.move(spot.x, spot.y, spot.z)); i++) {
            spot = spot.add(0, 1, 0);
        }
        return spot;
    }

    static Component nameOf(PetRecords.Record record) {
        if (!record.name().isEmpty()) {
            return Component.literal(record.name());
        }
        return EntityType.byString(record.type()).<Component>map(EntityType::getDescription)
                .orElseGet(() -> Component.literal(record.type()));
    }
}
