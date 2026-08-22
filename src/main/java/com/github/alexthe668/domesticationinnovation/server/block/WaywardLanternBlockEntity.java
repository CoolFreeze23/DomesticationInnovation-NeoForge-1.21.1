package com.github.alexthe668.domesticationinnovation.server.block;

import com.github.alexthe668.domesticationinnovation.DomesticationMod;
import com.github.alexthe668.domesticationinnovation.server.entity.DIAttachments;
import com.github.alexthe668.domesticationinnovation.server.entity.TameableUtils;
import com.github.alexthe668.domesticationinnovation.server.misc.DIWorldData;
import com.github.alexthe668.domesticationinnovation.server.misc.LanternRequest;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

public class WaywardLanternBlockEntity extends BlockEntity {

    // a pet already this close to the lantern is left where it stands
    private static final double NEARBY_DIST_SQR = 24 * 24;

    private int checkAgainIn = 100;
    private List<LanternRequest> workingRequests = new ArrayList<>();
    public WaywardLanternBlockEntity(BlockPos pos, BlockState state) {
        super(DITileEntityRegistry.WAYWARD_LANTERN.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, WaywardLanternBlockEntity te) {
        if(te.workingRequests.isEmpty()){
            if(te.checkAgainIn > 0){
                te.checkAgainIn--;
            }else{
                te.checkAgainIn = 200 + level.random.nextInt(400);
                DIWorldData data = DIWorldData.get(level);
                if(data != null){
                    for (Player player : getPlayers(level, pos)) {
                        for (LanternRequest request : data.getLanternRequestsFor(player.getUUID())) {
                            if (request.matchesDimension(level)) {
                                te.workingRequests.add(request);
                            }
                        }
                    }
                }
            }
        }else{
            if(level instanceof ServerLevel serverLevel) {
                DIWorldData data = DIWorldData.get(level);
                int timeout = DomesticationMod.CONFIG.lanternRequestTimeoutTicks.get();
                Iterator<LanternRequest> iterator = te.workingRequests.iterator();
                while (iterator.hasNext()) {
                    LanternRequest request = iterator.next();
                    // the 3x3 of chunks is forced exactly once per request, not re-issued every tick
                    if (!request.areChunksForced()) {
                        loadChunksAround(serverLevel, request.getPetUUID(), request.getChunkPosition(), true);
                        request.setChunksForced(true);
                    }
                    Entity entityFromChunk = serverLevel.getEntity(request.getPetUUID());
                    //takes a while to load in entities from the forced chunk, be patient...
                    if (entityFromChunk == null && request.tickWaitTime() <= timeout) {
                        continue;
                    }
                    if (entityFromChunk != null) {
                        // a pet already close to the lantern is left where it stands
                        if (entityFromChunk.position().distanceToSqr(Vec3.atCenterOf(pos)) > NEARBY_DIST_SQR) {
                            entityFromChunk.ejectPassengers();
                            entityFromChunk.stopRiding();
                            BlockPos putAt = getPlaceFor(entityFromChunk, pos, level.random);
                            entityFromChunk.teleportTo(putAt.getX() + 0.5F, putAt.getY(), putAt.getZ() + 0.5F);
                            notifyOwner(entityFromChunk);
                        }
                    } else if (DomesticationMod.CONFIG.lanternCrashSafeRespawn.get() && request.getEntitySnapshot() != null) {
                        // the chunks loaded but the pet is gone (crash mid-save, corrupted region...)
                        // - rebuild it from the snapshot taken when it unloaded
                        Entity rebuilt = rebuildFromSnapshot(serverLevel, pos, request);
                        if (rebuilt != null) {
                            notifyOwner(rebuilt);
                        }
                    }
                    loadChunksAround(serverLevel, request.getPetUUID(), request.getChunkPosition(), false);
                    request.setChunksForced(false);
                    iterator.remove();
                    if (data != null) {
                        data.removeMatchingLanternRequests(request.getPetUUID());
                    }
                }
            }
        }
    }

    private static void notifyOwner(Entity pet) {
        Entity owner = TameableUtils.getOwnerOf(pet);
        if (owner instanceof Player) {
            ((Player) owner).displayClientMessage(Component.translatable("message.domesticationinnovation.wayward_lantern_return", pet.getName()), false);
        }
    }

    private static Entity rebuildFromSnapshot(ServerLevel serverLevel, BlockPos lanternPos, LanternRequest request) {
        EntityType type = request.getEntityType();
        if (type == null) {
            return null;
        }
        Entity entity = type.create(serverLevel);
        if (!(entity instanceof LivingEntity living)) {
            if (entity != null) {
                entity.discard();
            }
            return null;
        }
        living.readAdditionalSaveData(request.getEntitySnapshot());
        DIAttachments.readPetDataFrom(living, request.getEntitySnapshot());
        // the original UUID is kept so bed claims and any future requests still point at this pet
        living.setUUID(request.getPetUUID());
        if (!request.getNametag().isEmpty()) {
            living.setCustomName(Component.translatable(request.getNametag()));
        }
        BlockPos putAt = getPlaceFor(living, lanternPos, serverLevel.random);
        living.moveTo(putAt.getX() + 0.5D, putAt.getY(), putAt.getZ() + 0.5D, living.getYRot(), living.getXRot());
        if (!serverLevel.addFreshEntity(living)) {
            living.discard();
            return null;
        }
        return living;
    }

    private static void loadChunksAround(ServerLevel serverLevel, UUID ticket, BlockPos center, boolean load){
        ChunkPos chunkPos = new ChunkPos(center);
        for(int i = -1; i <= 1; i++){
            for(int j = -1; j <= 1; j++){
                DIChunkLoadingRegistry.PET_TICKET_CONTROLLER.forceChunk(serverLevel, ticket, chunkPos.x + i, chunkPos.z + j, load, true);
            }
        }
    }

    private static List<Player> getPlayers(Level level, BlockPos pos) {
        double dist = 64 * 64;
        List<Player> withinDist = new ArrayList<>();
        for (Player player : level.players()) {
            if (player.distanceToSqr(Vec3.atCenterOf(pos)) < dist) {
                withinDist.add(player);
            }
        }
        return withinDist;
    }

    protected void loadAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("CheckAgainIn")) {
            this.checkAgainIn = tag.getInt("CheckAgainIn");
        }

    }

    protected void saveAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("CheckAgainIn", this.checkAgainIn);
    }

    private static BlockPos getPlaceFor(Entity entity, BlockPos lanternPos, RandomSource random){
        int maxDist = (int)Math.max(entity.getBbWidth() + 1, 10);
        for(int i = 0; i < 10; i++){
            BlockPos at = lanternPos.offset(random.nextInt(maxDist) - maxDist/2, 1, random.nextInt(maxDist) - maxDist/2);
            while(entity.level().getBlockState(at).isAir() && at.getY() > entity.level().getMinBuildHeight() && entity.level().noCollision(entity.getType().getDimensions().makeBoundingBox(at.getX() + 0.5, at.getY() - 1, at.getZ() + 0.5))){
                at = at.below();
            }
            if(entity.level().noCollision(entity.getType().getDimensions().makeBoundingBox(at.getX() + 0.5, at.getY(), at.getZ() + 0.5))){
                return at;
            }
            if(entity.isInWall()){
                return lanternPos.above();
            }
        }
        return lanternPos.above();
    }
}
