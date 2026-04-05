package com.github.alexthe668.domesticationinnovation.server.block;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.UUID;

public class DrumBlockEntity extends BlockEntity {

    private UUID placerUUID;

    public DrumBlockEntity(BlockPos pos, BlockState state) {
        super(DITileEntityRegistry.DRUM.get(), pos, state);
    }

    public UUID getPlacerUUID() {
        return placerUUID;
    }

    public void setPlacerUUID(UUID placerUUID) {
        this.placerUUID = placerUUID;
    }

    @Override
    protected void loadAdditional(CompoundTag compound, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(compound, registries);
        if (compound.contains("PlacerUUID")) {
            this.placerUUID = compound.getUUID("PlacerUUID");
        }
    }

    @Override
    protected void saveAdditional(CompoundTag compound, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(compound, registries);
        if (this.placerUUID != null) {
            compound.putUUID("PlacerUUID", placerUUID);
        }
    }
}
