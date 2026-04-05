package com.github.alexthe668.domesticationinnovation.server.entity;

import com.github.alexthe668.domesticationinnovation.DomesticationMod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class DIEntityRegistry {

    public static final DeferredRegister<EntityType<?>> DEF_REG = DeferredRegister.create(BuiltInRegistries.ENTITY_TYPE, DomesticationMod.MODID);

    // Note: setCustomClientFactory removed in NeoForge - client entity creation handled differently
    public static final Supplier<EntityType<ChainLightningEntity>> CHAIN_LIGHTNING = DEF_REG.register("chain_lightning",
            () -> EntityType.Builder.of(ChainLightningEntity::new, MobCategory.MISC).sized(0.5F, 0.5F).fireImmune().build(key("chain_lightning")));
    public static final Supplier<EntityType<RecallBallEntity>> RECALL_BALL = DEF_REG.register("recall_ball",
            () -> EntityType.Builder.of(RecallBallEntity::new, MobCategory.MISC).sized(0.8F, 0.8F).fireImmune().build(key("recall_ball")));
    public static final Supplier<EntityType<FeatherEntity>> FEATHER = DEF_REG.register("feather",
            () -> EntityType.Builder.<FeatherEntity>of(FeatherEntity::new, MobCategory.MISC).sized(0.2F, 0.2F).fireImmune().build(key("feather")));
    public static final Supplier<EntityType<GiantBubbleEntity>> GIANT_BUBBLE = DEF_REG.register("giant_bubble",
            () -> EntityType.Builder.of(GiantBubbleEntity::new, MobCategory.MISC).sized(1.2F, 1.8F).fireImmune().build(key("giant_bubble")));
    public static final Supplier<EntityType<FollowingJukeboxEntity>> FOLLOWING_JUKEBOX = DEF_REG.register("following_jukebox",
            () -> EntityType.Builder.of(FollowingJukeboxEntity::new, MobCategory.MISC).sized(0.65F, 0.65F).fireImmune().build(key("following_jukebox")));
    public static final Supplier<EntityType<HighlightedBlockEntity>> HIGHLIGHTED_BLOCK = DEF_REG.register("highlighted_block",
            () -> EntityType.Builder.of(HighlightedBlockEntity::new, MobCategory.MISC).sized(1.0F, 1.0F).fireImmune().build(key("highlighted_block")));
    public static final Supplier<EntityType<PsychicWallEntity>> PSYCHIC_WALL = DEF_REG.register("psychic_wall",
            () -> EntityType.Builder.of(PsychicWallEntity::new, MobCategory.MISC).sized(1F, 1F).fireImmune().build(key("psychic_wall")));

    private static String key(String name) {
        return ResourceLocation.fromNamespaceAndPath(DomesticationMod.MODID, name).toString();
    }
}
