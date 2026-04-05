package com.github.alexthe668.domesticationinnovation.client;

import com.github.alexthe668.domesticationinnovation.DomesticationMod;
import com.github.alexthe668.domesticationinnovation.client.particle.*;
import com.github.alexthe668.domesticationinnovation.client.render.*;
import com.github.alexthe668.domesticationinnovation.server.CommonProxy;
import com.github.alexthe668.domesticationinnovation.server.entity.*;
import com.github.alexthe668.domesticationinnovation.server.item.DIItemRegistry;
import com.github.alexthe668.domesticationinnovation.server.item.DeedOfOwnershipItem;
import com.github.alexthe668.domesticationinnovation.server.item.FeatherOnAStickItem;
import com.github.alexthe668.domesticationinnovation.server.misc.DIParticleRegistry;
import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.*;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@OnlyIn(Dist.CLIENT)
public class ClientProxy extends CommonProxy {

    public static final Map<Integer, DiscJockeySound> DISC_JOCKEY_SOUND_MAP = new HashMap<>();
    public static Map<Entity, int[]> shadowPunchRenderData = new HashMap<>();

    /**
     * Register client-side mod event listeners.
     * Called from DomesticationMod constructor - pass the mod event bus.
     */
    public static void registerModEvents(IEventBus modEventBus) {
        modEventBus.addListener(ClientProxy::onAddLayers);
        modEventBus.addListener(ClientProxy::setupParticles);
    }

    @OnlyIn(Dist.CLIENT)
    public static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        List<EntityType<? extends LivingEntity>> entityTypes = ImmutableList.copyOf(
                BuiltInRegistries.ENTITY_TYPE.stream()
                        .filter(LayerManager::canApply)
                        .filter(DefaultAttributes::hasSupplier)
                        .map(entityType -> (EntityType<? extends LivingEntity>) entityType)
                        .collect(Collectors.toList()));
        entityTypes.forEach(entityType -> LayerManager.addLayerIfApplicable(entityType, event));
    }

    public static float getNametagOffset() {
        return ModList.get().isLoaded("neat") ? 0.5F : 0;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void init() {
        // Mod event listeners are registered via registerModEvents() called from the mod constructor
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void clientInit() {
        // Entity renderers
        EntityRenderers.register(DIEntityRegistry.CHAIN_LIGHTNING.get(), ChainLightningRender::new);
        EntityRenderers.register(DIEntityRegistry.RECALL_BALL.get(), RecallBallRender::new);
        EntityRenderers.register(DIEntityRegistry.FEATHER.get(), RenderFeather::new);
        EntityRenderers.register(DIEntityRegistry.GIANT_BUBBLE.get(), RenderGiantBubble::new);
        EntityRenderers.register(DIEntityRegistry.FOLLOWING_JUKEBOX.get(), RenderJukeboxFollower::new);
        EntityRenderers.register(DIEntityRegistry.HIGHLIGHTED_BLOCK.get(), RenderHighlightedBlock::new);
        EntityRenderers.register(DIEntityRegistry.PSYCHIC_WALL.get(), RenderPsychicWall::new);

        // Item model predicates - ResourceLocation updated
        ItemProperties.register(DIItemRegistry.FEATHER_ON_A_STICK.get(),
                ResourceLocation.fromNamespaceAndPath(DomesticationMod.MODID, "cast"),
                (stack, lvl, holder, i) -> {
                    if (holder == null) return 0.0F;
                    boolean mainHand = holder.getMainHandItem() == stack;
                    boolean offHand = holder.getOffhandItem() == stack;
                    if (holder.getMainHandItem().getItem() instanceof FeatherOnAStickItem) offHand = false;
                    return (mainHand || offHand) && holder instanceof Player player
                            && player.fishing instanceof FeatherEntity ? 1.0F : 0.0F;
                });

        ItemProperties.register(DIItemRegistry.DEED_OF_OWNERSHIP.get(),
                ResourceLocation.fromNamespaceAndPath(DomesticationMod.MODID, "bound"),
                (stack, lvl, holder, i) -> DeedOfOwnershipItem.isBound(stack) ? 1 : 0);
    }

    public static void setupParticles(RegisterParticleProvidersEvent event) {
        DomesticationMod.LOGGER.debug("Registered particle factories");
        event.registerSpecial(DIParticleRegistry.DEFLECTION_SHIELD.get(), new ParticleDeflectionShield.Factory());
        event.registerSpriteSet(DIParticleRegistry.MAGNET.get(), ParticleMagnet.Factory::new);
        event.registerSpriteSet(DIParticleRegistry.ZZZ.get(), ParticleZZZ.Factory::new);
        event.registerSpriteSet(DIParticleRegistry.GIANT_POP.get(), ParticleGiantPop.Factory::new);
        event.registerSpriteSet(DIParticleRegistry.SIMPLE_BUBBLE.get(), ParticleSimpleBubble.Factory::new);
        event.registerSpriteSet(DIParticleRegistry.VAMPIRE.get(), ParticleVampire.Factory::new);
        event.registerSpriteSet(DIParticleRegistry.SNIFF.get(), ParticleSniff.Factory::new);
        event.registerSpriteSet(DIParticleRegistry.PSYCHIC_WALL.get(), ParticlePsychicWall.Factory::new);
        event.registerSpecial(DIParticleRegistry.INTIMIDATION.get(), new ParticleIntimidation.Factory());
        event.registerSpriteSet(DIParticleRegistry.BLIGHT.get(), ParticleBlight.Factory::new);
        event.registerSpriteSet(DIParticleRegistry.LANTERN_BUGS.get(), ParticleLanternBugs.Factory::new);
    }

    // =========================================================================
    // Client events - replaces Citadel's EventGetOutlineColor and Forge events
    // =========================================================================

    /**
     * Replaces Citadel's EventGetOutlineColor.
     * In NeoForge, use RenderHighlightEvent or custom rendering to handle outline colors.
     * The HighlightedBlockEntity outline is handled in its renderer instead.
     */
    // Outline color for HighlightedBlockEntity is handled in RenderHighlightedBlock via custom glowing effect

    @SubscribeEvent
    public void renderNametagEvent(RenderNameTagEvent event) {
        if (TameableUtils.isTamed(event.getEntity())
                && TameableUtils.isPetOf(Minecraft.getInstance().player, event.getEntity())
                && TameableUtils.hasAnyEnchants((LivingEntity) event.getEntity())
                && Minecraft.getInstance().player.isShiftKeyDown()) {
            event.setContent(net.minecraft.network.chat.Component.empty());
            renderNametagEnchantments(event.getEntity(), event.getContent(), event.getPoseStack(),
                    event.getMultiBufferSource(), event.getPackedLight());
        }
    }

    @SubscribeEvent
    public void onAttackEntityFromClient(InputEvent.InteractionKeyMappingTriggered event) {
        Player player = Minecraft.getInstance().player;
        if (player == null) return;

        if (event.isAttack() && DomesticationMod.CONFIG.swingThroughPets.get()
                && !player.isShiftKeyDown()
                && Minecraft.getInstance().hitResult instanceof EntityHitResult entityHit
                && TameableUtils.isPetOf(player, entityHit.getEntity())) {

            event.setCanceled(true);
            event.setSwingHand(true);

            Vec3 eyePos = player.getEyePosition(1.0F);
            Vec3 viewVec = player.getViewVector(1.0F);
            double reach = player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ENTITY_INTERACTION_RANGE) + 1.5D;
            double maxDistSq = Minecraft.getInstance().hitResult.getLocation().distanceToSqr(eyePos) + 8.0D;
            Vec3 farPoint = eyePos.add(viewVec.x * reach, viewVec.y * reach, viewVec.z * reach);
            AABB searchBox = player.getBoundingBox().expandTowards(viewVec.scale(reach)).inflate(1.0D, 1.0D, 1.0D);

            EntityHitResult result = ProjectileUtil.getEntityHitResult(player, eyePos, farPoint, searchBox,
                    entity -> !entity.isSpectator() && entity.isPickable() && !TameableUtils.isPetOf(player, entity),
                    maxDistSq);
            if (result != null) {
                Minecraft.getInstance().gameMode.attack(player, result.getEntity());
            }
        }
    }

    // =========================================================================
    // Nametag enchantment rendering
    // =========================================================================

    private void renderNametagEnchantments(Entity entity, Component nameTag, PoseStack pose,
                                           MultiBufferSource buffer, int lightIn) {
        if (!Minecraft.getInstance().player.isShiftKeyDown()
                || !TameableUtils.isTamed(entity) || !TameableUtils.hasAnyEnchants((LivingEntity) entity)) return;

        LivingEntity living = (LivingEntity) entity;
        List<Component> enchantList = TameableUtils.getEnchantDescriptions(living);
        double distSq = Minecraft.getInstance().getEntityRenderDispatcher().distanceToSqr(entity);

        // Note: ForgeHooksClient.isNameplateInRenderDistance replaced with distance check
        if (distSq > 4096.0D) return; // ~64 blocks

        if (nameTag instanceof MutableComponent mutable) {
            int health = Math.round(living.getHealth());
            int maxHealth = Math.round(living.getMaxHealth());
            nameTag = mutable.append(" (" + health + "/" + maxHealth + ")");
        }

        Font font = Minecraft.getInstance().font;
        boolean visible = !entity.isDiscrete();
        float yOffset = entity.getBbHeight() + 0.5F;
        int baseY = -10 * enchantList.size();

        pose.pushPose();
        pose.translate(0.0D, yOffset + getNametagOffset(), 0.0D);
        pose.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
        pose.scale(-0.025F, -0.025F, 0.025F);

        // Collar tag icon
        float iconX = !enchantList.isEmpty() ? (float) (-font.width(enchantList.get(0)) / 2) : (float) (-font.width(nameTag) / 2);
        pose.pushPose();
        pose.translate(iconX + 12, (-10 * enchantList.size()) + 16, 0);
        pose.mulPose(Axis.XP.rotationDegrees(180.0F));
        pose.scale(22F, 22F, 22F);
        Minecraft.getInstance().getItemRenderer().renderStatic(
                new ItemStack(DIItemRegistry.COLLAR_TAG.get()), ItemDisplayContext.GROUND,
                lightIn, OverlayTexture.NO_OVERLAY, pose, buffer, entity.level(), entity.getId());
        pose.popPose();

        // Name text
        Matrix4f matrix = pose.last().pose();
        float bgOpacity = Minecraft.getInstance().options.getBackgroundOpacity(0.25F);
        int bgColor = (int) (bgOpacity * 255.0F) << 24;
        float nameX = (float) (-font.width(nameTag) / 2);
        font.drawInBatch(nameTag, nameX, (float) baseY - 0.25F, 553648127, false, matrix, buffer,
                Font.DisplayMode.NORMAL, bgColor, lightIn);
        if (visible) {
            font.drawInBatch(nameTag, nameX, (float) baseY - 0.25F, -1, false, matrix, buffer,
                    Font.DisplayMode.NORMAL, 0, lightIn);
        }

        // Enchantment list
        pose.pushPose();
        pose.scale(0.8F, 0.8F, 0.8F);
        matrix = pose.last().pose();
        for (int k = 0; k < enchantList.size(); k++) {
            float enchX = (float) (-font.width(enchantList.get(k)) / 2);
            float enchY = baseY * 1.25F + k * 10 + 12;
            font.drawInBatch(enchantList.get(k), enchX, enchY, 553648127, false, matrix, buffer,
                    Font.DisplayMode.NORMAL, bgColor, lightIn);
            if (visible) {
                font.drawInBatch(enchantList.get(k), enchX, enchY, -1, false, matrix, buffer,
                        Font.DisplayMode.NORMAL, 0, lightIn);
            }
        }
        pose.popPose();
        pose.popPose();
    }

    // =========================================================================
    // Visual data sync
    // =========================================================================

    @Override
    public void updateVisualDataForMob(Entity entity, int[] arr) {
        shadowPunchRenderData.put(entity, arr);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void updateEntityStatus(Entity entity, byte updateKind) {
        if (!(entity instanceof FollowingJukeboxEntity jukebox)) return;

        SoundEvent record = jukebox.getRecordSound();
        if (entity.isAlive() && updateKind == 66) {
            DiscJockeySound sound;
            if (record != null && (DISC_JOCKEY_SOUND_MAP.get(entity.getId()) == null
                    || DISC_JOCKEY_SOUND_MAP.get(entity.getId()).getRecordSound() != record)) {
                sound = new DiscJockeySound(record, jukebox);
                DISC_JOCKEY_SOUND_MAP.put(entity.getId(), sound);
            } else {
                sound = DISC_JOCKEY_SOUND_MAP.get(entity.getId());
            }
            if (sound != null && !Minecraft.getInstance().getSoundManager().isActive(sound)
                    && sound.canPlaySound() && sound.isNearest()) {
                Minecraft.getInstance().getSoundManager().play(sound);
            }
        }
        if (updateKind == 67 || record == null) {
            DiscJockeySound sound = DISC_JOCKEY_SOUND_MAP.remove(entity.getId());
            if (sound != null) {
                Minecraft.getInstance().getSoundManager().stop(sound);
            }
        }
    }
}
