package com.github.alexthe668.domesticationinnovation.server.block;
import com.github.alexthe668.domesticationinnovation.server.entity.DIAttachments;
import com.github.alexthe668.domesticationinnovation.server.entity.ModifedToBeTameable;
import com.github.alexthe668.domesticationinnovation.DomesticationMod;
import com.github.alexthe668.domesticationinnovation.server.entity.TameableUtils;
import com.github.alexthe668.domesticationinnovation.server.misc.DIWorldData;
import com.github.alexthe668.domesticationinnovation.server.misc.RespawnRequest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

public class PetBedBlockEntity extends BlockEntity {

    // the one pet this bed belongs to; null while the bed is unclaimed
    @Nullable
    private UUID claimedPetUUID;

    public PetBedBlockEntity(BlockPos pos, BlockState state) {
        super(DITileEntityRegistry.PET_BED.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, PetBedBlockEntity blockEntity) {
        long time = level.dayTime() % 24000L;
        if(time == 1){
            DIWorldData data = DIWorldData.get(level);
            if(data != null){
               List<RespawnRequest> requestList = data.getRespawnRequestsFor(level, pos);
               for(RespawnRequest request : requestList){
                    if(addAndRemoveEntity(level, pos, state.getValue(PetBedBlock.FACING), request)){
                        data.removeRespawnRequest(request);
                    }
               }
            }
        }
        if (blockEntity.claimedPetUUID != null && level.getGameTime() % 40L == 0L && level instanceof ServerLevel serverLevel) {
            // a claimant that is loaded but no longer points back here has moved on - free the bed.
            // an unresolvable claimant may simply be in an unloaded chunk, so its claim is kept
            Entity claimant = serverLevel.getEntity(blockEntity.claimedPetUUID);
            if (claimant instanceof LivingEntity living) {
                BlockPos linkedPos = TameableUtils.isTamed(living) ? TameableUtils.getPetBedPos(living) : null;
                if (linkedPos == null || !linkedPos.equals(pos)) {
                    blockEntity.setClaimedPet(null);
                }
            }
        }
    }

    @Nullable
    public UUID getClaimedPet() {
        return this.claimedPetUUID;
    }

    public void setClaimedPet(@Nullable UUID petUUID) {
        this.claimedPetUUID = petUUID;
        this.setChanged();
    }

    /**
     * Attempts to bind this bed to the given pet. Succeeds when the bed is unclaimed,
     * already belongs to this pet, or its previous claim has gone stale; a bed actively
     * claimed by another living pet is refused. On success the pet's previous bed (if any
     * and reachable) is released so a pet only ever holds one bed at a time.
     */
    public static boolean tryClaim(Level level, BlockPos pos, LivingEntity pet) {
        if (!(level.getBlockEntity(pos) instanceof PetBedBlockEntity bed)) {
            return false;
        }
        UUID petId = pet.getUUID();
        if (DomesticationMod.CONFIG.exclusivePetBeds.get() && bed.claimedPetUUID != null && !bed.claimedPetUUID.equals(petId)) {
            if (!(level instanceof ServerLevel serverLevel)) {
                return false;
            }
            Entity claimant = serverLevel.getEntity(bed.claimedPetUUID);
            if (claimant == null) {
                // possibly just unloaded; the periodic validation decides its fate
                return false;
            }
            if (claimant instanceof LivingEntity other && TameableUtils.isTamed(other)
                    && pos.equals(TameableUtils.getPetBedPos(other))) {
                return false;
            }
            // the recorded claimant no longer wants this bed - fall through and take it over
        }
        releasePreviousBedOf(level, pos, pet);
        bed.setClaimedPet(petId);
        return true;
    }

    private static void releasePreviousBedOf(Level level, BlockPos newBedPos, LivingEntity pet) {
        BlockPos previous = TameableUtils.getPetBedPos(pet);
        if (previous == null || previous.equals(newBedPos)) {
            return;
        }
        String bedDimension = TameableUtils.getPetBedDimension(pet);
        if (bedDimension != null && !bedDimension.isEmpty() && !bedDimension.equals(level.dimension().toString())) {
            // old bed sits in another dimension; its own validation tick will free it
            return;
        }
        if (level.hasChunkAt(previous) && level.getBlockEntity(previous) instanceof PetBedBlockEntity previousBed
                && pet.getUUID().equals(previousBed.claimedPetUUID)) {
            previousBed.setClaimedPet(null);
        }
    }

    /**
     * Frees this bed's claim if it is held by the given pet. Meant for callers that know
     * the pet is permanently gone (died with respawns disabled, untamed, etc).
     */
    public static void releaseClaim(Level level, BlockPos pos, UUID petUUID) {
        if (level.hasChunkAt(pos) && level.getBlockEntity(pos) instanceof PetBedBlockEntity bed
                && petUUID.equals(bed.claimedPetUUID)) {
            bed.setClaimedPet(null);
        }
    }

    /**
     * Checks a pet's stored bed link against the world: if the bed block is gone or now
     * belongs to a different pet, the link is dropped; an unclaimed bed at the stored
     * position is adopted (covers worlds saved before beds tracked their pet).
     * Only the pet's own dimension is inspected; anything unverifiable is left alone.
     */
    public static void revalidateBedLink(LivingEntity pet) {
        if (pet.level().isClientSide || !TameableUtils.isTamed(pet)) {
            return;
        }
        BlockPos bedPos = TameableUtils.getPetBedPos(pet);
        if (bedPos == null) {
            return;
        }
        String bedDimension = TameableUtils.getPetBedDimension(pet);
        if (bedDimension != null && !bedDimension.isEmpty() && !bedDimension.equals(pet.level().dimension().toString())) {
            return;
        }
        if (!pet.level().hasChunkAt(bedPos)) {
            return;
        }
        if (!(pet.level().getBlockEntity(bedPos) instanceof PetBedBlockEntity bed)) {
            TameableUtils.removePetBedPos(pet);
            return;
        }
        if (bed.claimedPetUUID == null) {
            bed.setClaimedPet(pet.getUUID());
        } else if (DomesticationMod.CONFIG.exclusivePetBeds.get() && !bed.claimedPetUUID.equals(pet.getUUID())) {
            TameableUtils.removePetBedPos(pet);
        }
    }

    public void removeAllRequestsFor(@Nullable Player message){
        DIWorldData data = DIWorldData.get(level);
        if(data != null){
            List<RespawnRequest> requestList = data.getRespawnRequestsFor(level, this.getBlockPos());
            for(RespawnRequest request : requestList){
                data.removeRespawnRequest(request);
                if(message != null){
                    message.displayClientMessage(Component.translatable("message.domesticationinnovation.goodbye", request.getNametag()), false);
                }
            }
        }
    }

    private static boolean addAndRemoveEntity(Level level, BlockPos pos, Direction dir, RespawnRequest request) {
        EntityType type = request.getEntityType();
        if(type != null && DomesticationMod.CONFIG.petBedRespawns.get()){
            Entity entity = type.create(level);
            if(entity instanceof LivingEntity living){
                living.readAdditionalSaveData(request.getEntityData());
                DIAttachments.readPetDataFrom(living, request.getEntityData());
                living.setPos(Vec3.upFromBottomCenterOf(pos, 0.8F));
                living.setHealth(living.getMaxHealth());
                if(!request.getNametag().isEmpty()){
                    living.setCustomName(Component.translatable(request.getNametag()));
                }
                switch (dir){
                    case NORTH:
                        living.setYRot(180);
                        break;
                    case EAST:
                        living.setYRot(-90);
                        break;
                    case SOUTH:
                        living.setYRot(0);
                        break;
                    case WEST:
                        living.setYRot(90);
                        break;
                }
                TameableUtils.trySetCommand(living, 1);
                if (living instanceof TamableAnimal tame) {
                    tame.setOrderedToSit(true);
                }
                level.addFreshEntity(living);
                // the respawned pet is a fresh entity - point the bed's claim at its new UUID
                if (level.getBlockEntity(pos) instanceof PetBedBlockEntity bed) {
                    bed.setClaimedPet(living.getUUID());
                }
                Entity owner = TameableUtils.getOwnerOf(entity);
                if(owner instanceof Player){
                    ((Player)owner).displayClientMessage(Component.translatable("message.domesticationinnovation.respawn", entity.getName()), false);
                }
                return true;
            }
        }
        return false;
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.hasUUID("ClaimedPetUUID")) {
            this.claimedPetUUID = tag.getUUID("ClaimedPetUUID");
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (this.claimedPetUUID != null) {
            tag.putUUID("ClaimedPetUUID", this.claimedPetUUID);
        }
    }

    public void resetBedsForNearbyPets() {
        this.setClaimedPet(null);
        Predicate<Entity> pet = (animal) -> TameableUtils.isTamed(animal) && TameableUtils.getPetBedPos((LivingEntity)animal) != null && TameableUtils.getPetBedPos((LivingEntity)animal).equals(this.getBlockPos());
        List<LivingEntity> list = level.getEntitiesOfClass(LivingEntity.class, new AABB(this.getBlockPos().getX() - 10, this.getBlockPos().getY() - 5, this.getBlockPos().getZ() - 10, this.getBlockPos().getX() + 10, this.getBlockPos().getY() + 5, this.getBlockPos().getZ() + 10), EntitySelector.NO_SPECTATORS.and(pet));
        for (LivingEntity entity : list){
            Entity owner = TameableUtils.getOwnerOf(entity);
            if(owner instanceof Player){
                ((Player)owner).displayClientMessage(Component.translatable("message.domesticationinnovation.remove_respawn", entity.getName()), false);
                TameableUtils.removePetBedPos(entity);
            }
        }
    }
}
