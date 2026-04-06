package com.github.alexthe668.domesticationinnovation.server.misc;

import com.github.alexthe668.domesticationinnovation.DomesticationMod;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.pools.LegacySinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElementType;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.*;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.registries.BuiltInRegistries;
import com.github.alexthe668.domesticationinnovation.server.entity.DIVillagerRegistry;

import java.util.List;

public class PetshopStructurePoolElement extends LegacySinglePoolElement {

    public static final ResourceLocation CHEST = ResourceLocation.fromNamespaceAndPath(DomesticationMod.MODID, "chests/petshop_chest");
    public static final ResourceLocation FISHTANK_MOBS = ResourceLocation.fromNamespaceAndPath(DomesticationMod.MODID, "petstore_fishtank");
    public static final ResourceLocation CAGE_0_MOBS = ResourceLocation.fromNamespaceAndPath(DomesticationMod.MODID, "petstore_cage_0");
    public static final ResourceLocation CAGE_1_MOBS = ResourceLocation.fromNamespaceAndPath(DomesticationMod.MODID, "petstore_cage_1");
    public static final ResourceLocation CAGE_2_MOBS = ResourceLocation.fromNamespaceAndPath(DomesticationMod.MODID, "petstore_cage_2");
    public static final ResourceLocation CAGE_3_MOBS = ResourceLocation.fromNamespaceAndPath(DomesticationMod.MODID, "petstore_cage_3");
    private static boolean initializedMobLists = false;
    private static EntityType[] fishtankMobs = null;
    private static EntityType[] cage0Mobs = null;
    private static EntityType[] cage1Mobs = null;
    private static EntityType[] cage2Mobs = null;
    private static EntityType[] cage3Mobs = null;

    public static final MapCodec<PetshopStructurePoolElement> CODEC = RecordCodecBuilder.mapCodec((p_210357_) -> {
        return p_210357_.group(templateCodec(), processorsCodec(), projectionCodec()).apply(p_210357_, (a, b, c) -> new PetshopStructurePoolElement(a, b, c, java.util.Optional.empty()));
    });

    protected PetshopStructurePoolElement(Either<ResourceLocation, StructureTemplate> either, Holder<StructureProcessorList> p_210349_, StructureTemplatePool.Projection p_210350_, java.util.Optional<LiquidSettings> liquidSettings) {
        super(either, p_210349_, p_210350_, liquidSettings);
    }

    public PetshopStructurePoolElement(ResourceLocation resourceLocation, Holder<StructureProcessorList> processors) {
        super(Either.left(resourceLocation), processors, StructureTemplatePool.Projection.RIGID, java.util.Optional.empty());
    }

    @Override
    public boolean place(StructureTemplateManager structureTemplateManager, WorldGenLevel worldGenLevel, StructureManager structureManager, ChunkGenerator chunkGenerator, BlockPos offset, BlockPos pos2, Rotation rotation, BoundingBox boundingBox, RandomSource random, LiquidSettings liquidSettings, boolean keepJigsaws) {
        boolean result = super.place(structureTemplateManager, worldGenLevel, structureManager, chunkGenerator, offset, pos2, rotation, boundingBox, random, liquidSettings, keepJigsaws);
        if (result) {
            List<StructureTemplate.StructureBlockInfo> dataMarkers = this.getDataMarkers(structureTemplateManager, offset, rotation, true);
            for (StructureTemplate.StructureBlockInfo info : dataMarkers) {
                if (boundingBox.isInside(info.pos())) {
                    this.handleDataMarker(worldGenLevel, info, offset, rotation, random, boundingBox);
                }
            }
        }
        return result;
    }

    @Override
    public void handleDataMarker(LevelAccessor levelAccessor, StructureTemplate.StructureBlockInfo structureBlockInfo, BlockPos pos, Rotation rotation, RandomSource random, BoundingBox box) {
        if (structureBlockInfo.nbt() == null) {
            DomesticationMod.LOGGER.warn("PetshopStructurePoolElement: data marker at {} has null NBT", structureBlockInfo.pos());
            return;
        }
        String contents = structureBlockInfo.nbt().getString("metadata");
        DomesticationMod.LOGGER.info("PetshopStructurePoolElement: processing data marker '{}' at {}", contents, structureBlockInfo.pos());
        if(!initializedMobLists){
            fishtankMobs = getAllMatchingEntities(DITagRegistry.PETSTORE_FISHTANK).toArray(new EntityType[0]);
            cage0Mobs = getAllMatchingEntities(DITagRegistry.PETSTORE_CAGE_0).toArray(new EntityType[0]);
            cage1Mobs = getAllMatchingEntities(DITagRegistry.PETSTORE_CAGE_1).toArray(new EntityType[0]);
            cage2Mobs = getAllMatchingEntities(DITagRegistry.PETSTORE_CAGE_2).toArray(new EntityType[0]);
            cage3Mobs = getAllMatchingEntities(DITagRegistry.PETSTORE_CAGE_3).toArray(new EntityType[0]);
            initializedMobLists = true;
        }
        switch (contents) {
            case "petshop_water":
                BlockState state = Blocks.WATER.defaultBlockState();
                float f = random.nextFloat();
                if (f < 0.5F) {
                    state = Blocks.SEAGRASS.defaultBlockState();
                } else if (f < 0.75F) {
                    Block coralBlock;
                    switch (random.nextInt(5)) {
                        case 1:
                            coralBlock = Blocks.TUBE_CORAL;
                            break;
                        case 2:
                            coralBlock = Blocks.BRAIN_CORAL;
                            break;
                        case 3:
                            coralBlock = Blocks.BUBBLE_CORAL;
                            break;
                        case 4:
                            coralBlock = Blocks.FIRE_CORAL;
                            break;
                        default:
                            coralBlock = Blocks.HORN_CORAL;
                            break;
                    }
                    state = coralBlock.defaultBlockState().setValue(BaseCoralPlantTypeBlock.WATERLOGGED, true);
                }
                spawnAnimalsAt(levelAccessor, structureBlockInfo.pos(), 2,  random, fishtankMobs);
                levelAccessor.setBlock(structureBlockInfo.pos(), state, 2);
                break;
            case "petshop_chest":
                levelAccessor.setBlock(structureBlockInfo.pos(), Blocks.AIR.defaultBlockState(), 2);
                BlockEntity chestBe = levelAccessor.getBlockEntity(structureBlockInfo.pos().below());
                if (chestBe instanceof RandomizableContainerBlockEntity container) {
                    container.setLootTable(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.LOOT_TABLE, CHEST));
                }
                break;
            case "petshop_cage_0"://wolf, rabbit or cat
                spawnAnimalsAt(levelAccessor, structureBlockInfo.pos(), 1 + random.nextInt(2), random, cage0Mobs);
                levelAccessor.setBlock(structureBlockInfo.pos(), Blocks.AIR.defaultBlockState(), 4);
                spawnAnimalTamer(levelAccessor, structureBlockInfo.pos().above(), random);
                break;
            case "petshop_cage_1"://desert terrarium
                spawnAnimalsAt(levelAccessor, structureBlockInfo.pos(), 2 + random.nextInt(2), random, cage1Mobs);
                levelAccessor.setBlock(structureBlockInfo.pos(), Blocks.AIR.defaultBlockState(), 2);
                break;
            case "petshop_cage_2"://ice terrarium
                spawnAnimalsAt(levelAccessor, structureBlockInfo.pos(), 1 + random.nextInt(2), random, cage2Mobs);
                levelAccessor.setBlock(structureBlockInfo.pos(), Blocks.AIR.defaultBlockState(), 2);
                break;
            case "petshop_cage_3"://parrot
                spawnAnimalsAt(levelAccessor, structureBlockInfo.pos(), 1, random, cage3Mobs);
                levelAccessor.setBlock(structureBlockInfo.pos(), Blocks.AIR.defaultBlockState(), 2);
                break;
        }
    }

    private List<EntityType<?>> getAllMatchingEntities(TagKey<EntityType<?>> tag) {
       return BuiltInRegistries.ENTITY_TYPE.stream().filter((type -> type.is(tag))).toList();
    }

    public void spawnAnimalsAt(LevelAccessor accessor, BlockPos at, int count, RandomSource random, EntityType... types) {
        if (types.length > 0 && count > 0 && accessor instanceof ServerLevelAccessor serverLevel) {
            for (int i = 0; i < count; i++) {
                int index = types.length == 1 ? 0 : random.nextInt(types.length);
                Entity entity = types[index].create(serverLevel.getLevel());
                entity.setPos(Vec3.atBottomCenterOf(at));
                entity.setYRot(random.nextInt(360) - 180);
                entity.setXRot(random.nextInt(360) - 180);
                if (entity instanceof Mob mob) {
                    mob.setPersistenceRequired();
                    mob.finalizeSpawn(serverLevel, serverLevel.getCurrentDifficultyAt(mob.blockPosition()), MobSpawnType.STRUCTURE, null);
                }
                serverLevel.addFreshEntityWithPassengers(entity);
            }
        }
    }

    private void spawnAnimalTamer(LevelAccessor accessor, BlockPos at, RandomSource random) {
        if (accessor instanceof ServerLevelAccessor serverLevel) {
            Villager villager = EntityType.VILLAGER.create(serverLevel.getLevel());
            if (villager != null) {
                villager.setPos(Vec3.atBottomCenterOf(at));
                villager.setVillagerData(villager.getVillagerData()
                        .setProfession(DIVillagerRegistry.ANIMAL_TAMER.get())
                        .setLevel(2));
                villager.setPersistenceRequired();
                villager.finalizeSpawn(serverLevel, serverLevel.getCurrentDifficultyAt(villager.blockPosition()), MobSpawnType.STRUCTURE, null);
                serverLevel.addFreshEntityWithPassengers(villager);
            }
        }
    }

    @Override
    protected StructurePlaceSettings getSettings(Rotation rotation, BoundingBox boundingBox, LiquidSettings liquidSettings, boolean keepJigsaws) {
        StructurePlaceSettings settings = super.getSettings(rotation, boundingBox, liquidSettings, keepJigsaws);
        settings.setIgnoreEntities(false);
        settings.setFinalizeEntities(true);
        return settings;
    }

    public StructurePoolElementType<?> getType() {
        return DIVillagePieceRegistry.PETSHOP.get();
    }

    public String toString() {
        return "PetShop[" + this.template + "]";
    }
}
