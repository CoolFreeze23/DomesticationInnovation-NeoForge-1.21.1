package com.github.alexthe668.domesticationinnovation.server;
import com.github.alexthe668.domesticationinnovation.server.entity.ModifedToBeTameable;

import com.github.alexthe668.domesticationinnovation.DomesticationMod;
import com.github.alexthe668.domesticationinnovation.server.block.DIBlockRegistry;
import com.github.alexthe668.domesticationinnovation.server.block.PetBedBlock;
import com.github.alexthe668.domesticationinnovation.server.block.PetBedBlockEntity;
import com.github.alexthe668.domesticationinnovation.server.enchantment.DIEnchantmentKeys;
import com.github.alexthe668.domesticationinnovation.server.entity.*;
import com.github.alexthe668.domesticationinnovation.server.item.DIItemRegistry;
import com.github.alexthe668.domesticationinnovation.server.item.DeedOfOwnershipItem;
import com.github.alexthe668.domesticationinnovation.server.entity.ai.GenericPetAI;
import com.github.alexthe668.domesticationinnovation.server.misc.*;
import com.github.alexthe668.domesticationinnovation.server.misc.data.TamingDefinition;
import com.github.alexthe668.domesticationinnovation.server.misc.data.TransformationDefinition;
import com.github.alexthe668.domesticationinnovation.server.misc.trades.*;
import com.google.common.collect.ImmutableSet;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Fox;
import net.minecraft.world.entity.animal.Rabbit;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.AnvilUpdateEvent;
import net.neoforged.neoforge.event.entity.*;
import net.neoforged.neoforge.event.entity.item.ItemExpireEvent;
import net.neoforged.neoforge.event.entity.living.*;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.village.VillagerTradesEvent;

import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Predicate;

/**
 * Server-side event handler - ported to NeoForge 1.21.1.
 *
 * Changes from Forge 1.20.1:
 * - All event classes moved to net.neoforged.neoforge.event.*
 * - LivingEvent.LivingTickEvent → EntityTickEvent (filter for LivingEntity)
 * - LivingAttackEvent → LivingIncomingDamageEvent
 * - LivingDamageEvent → LivingDamageEvent.Pre
 * - TickEvent.LevelTickEvent → LevelTickEvent
 * - MobSpawnEvent.FinalizeSpawn → FinalizeSpawnEvent
 * - ForgeRegistries → BuiltInRegistries
 * - ForgeChunkManager → TicketController (see DIChunkLoadingRegistry)
 * - Enchantment references → ResourceKey<Enchantment> via DIEnchantmentKeys
 * - ResourceLocation constructor → .fromNamespaceAndPath()
 *
 * Bug fixes:
 * - Fixed NPE in blockCollarTick() when tracker is null
 * - Replaced new Random() with entity-bound RNG
 * - Decomposed 300+ line onLivingUpdate into focused helper methods
 */
public class CommonProxy {

    private static final ResourceLocation FROST_FANG_SLOW_ID =
            ResourceLocation.fromNamespaceAndPath(DomesticationMod.MODID, "frost_fang_slow");
    // Same ids the collar-update path uses, so a join-time refresh replaces
    // whatever an earlier session left on the entity
    private static final ResourceLocation HEALTH_BOOST_MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath(DomesticationMod.MODID, "health_boost");
    private static final ResourceLocation SPEED_BOOST_MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath(DomesticationMod.MODID, "speed_boost");
    private static final ResourceLocation AMPHIBIOUS_LAND_SPEED_MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath(DomesticationMod.MODID, "amphibious_land_speed");
    private static final ResourceLocation TOUGH_ARMOR_MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath(DomesticationMod.MODID, "tough_armor");
    private static final ResourceLocation TOUGH_KB_RESISTANCE_MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath(DomesticationMod.MODID, "tough_kb_resistance");
    private static final ResourceLocation BINDING_CURSE_ID = ResourceLocation.withDefaultNamespace("binding_curse");
    private static final ResourceLocation VANISHING_CURSE_ID = ResourceLocation.withDefaultNamespace("vanishing_curse");
    private static final TargetingConditions ZOMBIE_TARGET = TargetingConditions.forCombat().range(32.0D);

    // Re-entrancy guard for the Share enchant: shared hits reuse the original
    // DamageSource, so a shared victim's own hurt pipeline must not share again
    private static final ThreadLocal<Boolean> SHARING_DAMAGE = ThreadLocal.withInitial(() -> false);

    // Pets pending cross-dimension teleportation, cleared each tick
    public static final List<PendingPetTeleport> teleportingPets = new ArrayList<>();

    private static final Map<Level, CollarTickTracker> COLLAR_TICK_TRACKER_MAP = new HashMap<>();

    public record PendingPetTeleport(Entity entity, ServerLevel targetLevel, UUID ownerUUID) {}

    public void init() {}

    public void serverInit() {
    }

    public void clientInit() {}

    public void updateVisualDataForMob(Entity entity, int[] arr) {}

    public void updateEntityStatus(Entity entity, byte updateKind) {}

    // =========================================================================
    // Collar tick throttling - FIXED: null pointer crash in original
    // =========================================================================

    private boolean canTickCollar(Entity entity) {
        if (entity.level().isClientSide) {
            return true;
        }
        CollarTickTracker tracker = COLLAR_TICK_TRACKER_MAP.get(entity.level());
        return tracker == null || !tracker.isEntityBlocked(entity);
    }

    private void blockCollarTick(Entity entity) {
        if (!entity.level().isClientSide) {
            // BUG FIX: Original called tracker.addBlockedEntityTick() when tracker was null
            CollarTickTracker tracker = COLLAR_TICK_TRACKER_MAP.computeIfAbsent(entity.level(), k -> new CollarTickTracker());
            // ~2 game ticks; the tracker only decrements once per level tick
            tracker.addBlockedEntityTick(entity.getUUID(), 3);
        }
    }

    // =========================================================================
    // Server lifecycle
    // =========================================================================

    @SubscribeEvent
    public void onServerAboutToStart(ServerAboutToStartEvent event) {
        DIVillagePieceRegistry.registerHouses(event.getServer());
    }

    // =========================================================================
    // Entity join/leave world
    // =========================================================================

    @SubscribeEvent
    public void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof LivingEntity living && TameableUtils.couldBeTamed(living)) {
            if (!living.level().isClientSide && TameableUtils.hasAnyEnchants(living)) {
                refreshEnchantAttributeModifiers(living);
            }
            if (TameableUtils.hasEnchant(living, DIEnchantmentKeys.HEALTH_BOOST)) {
                living.setHealth((float) Math.max(living.getHealth(), TameableUtils.getSafePetHealth(living)));
            }
            if (living.isAlive() && TameableUtils.isTamed(living)) {
                DIWorldData data = DIWorldData.get(living.level());
                if (data != null) {
                    data.removeMatchingLanternRequests(living.getUUID());
                }
            }
            // Goal selectors are rebuilt on load, so data-tamed pets need
            // their generic AI re-installed every join (idempotent, server-only)
            if (!living.level().isClientSide && living instanceof Mob mob && TameableUtils.isDataTamed(mob)) {
                GenericPetAI.applyGenericPetAI(mob);
            }
            // Drop or adopt the stored bed link if the bed changed while unloaded
            PetBedBlockEntity.revalidateBedLink(living);
        }
    }

    /**
     * Recomputes the enchant-driven attribute modifiers from the stored enchant
     * tag every time the entity enters a level. The modifiers are added as
     * transient (unsaved) values and any previously serialized copy is removed
     * first, so config changes and power-multiplier tweaks take effect on load
     * and stale values never accumulate in entity NBT. The amounts must stay in
     * step with the ones applied when a collar is updated (TameableUtils).
     *
     * <p>Health policy: the boost modifiers are transient, so the saved "Health"
     * value was already clamped to the modifier-less base max by vanilla before
     * this event fired. Scaling health UP by the pre/post fraction here would
     * therefore full-heal or inflate the pet on every reload; instead, when max
     * grows we leave health untouched and let the SafePetHealth floor applied
     * by {@link #onEntityJoinLevel} restore the true pre-save health. Only when
     * max SHRINKS (enchant removed/disabled offline, power multiplier lowered,
     * or stale permanent modifiers dropped from a legacy save - the one case
     * where the pre-reapply max reflected the true boosted max) do we preserve
     * the bar's fill fraction.
     */
    private void refreshEnchantAttributeModifiers(LivingEntity living) {
        AttributeInstance health = living.getAttribute(Attributes.MAX_HEALTH);
        AttributeInstance speed = living.getAttribute(Attributes.MOVEMENT_SPEED);
        AttributeInstance armor = living.getAttribute(Attributes.ARMOR);
        AttributeInstance kbResistance = living.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        if (health == null && speed == null && armor == null && kbResistance == null) return;

        float oldMax = living.getMaxHealth();
        float healthFraction = oldMax > 0 ? living.getHealth() / oldMax : 1.0F;

        double powerMult = DomesticationMod.CONFIG.enchantPowerMultiplier.get();
        int healthLevel = TameableUtils.getEnchantLevel(living, DIEnchantmentKeys.HEALTH_BOOST);
        int speedLevel = TameableUtils.getEnchantLevel(living, DIEnchantmentKeys.SPEEDSTER);
        int toughLevel = TameableUtils.getEnchantLevel(living, DIEnchantmentKeys.TOUGH);
        boolean amphibiousOnLand = TameableUtils.hasEnchant(living, DIEnchantmentKeys.AMPHIBIOUS)
                && !living.isInWaterOrBubble() && isWaterCategory(living);

        if (health != null) {
            health.removeModifier(HEALTH_BOOST_MODIFIER_ID);
            if (healthLevel > 0) {
                health.addTransientModifier(new AttributeModifier(HEALTH_BOOST_MODIFIER_ID,
                        healthLevel * 10 * powerMult, AttributeModifier.Operation.ADD_VALUE));
            }
        }
        if (speed != null) {
            speed.removeModifier(SPEED_BOOST_MODIFIER_ID);
            if (speedLevel > 0) {
                speed.addTransientModifier(new AttributeModifier(SPEED_BOOST_MODIFIER_ID,
                        speedLevel * 0.075 * powerMult, AttributeModifier.Operation.ADD_VALUE));
            }
            speed.removeModifier(AMPHIBIOUS_LAND_SPEED_MODIFIER_ID);
            if (amphibiousOnLand) {
                speed.addTransientModifier(new AttributeModifier(AMPHIBIOUS_LAND_SPEED_MODIFIER_ID,
                        0.13, AttributeModifier.Operation.ADD_VALUE));
            }
        }
        // Tough: same ids and amounts as the collar-update path in TameableUtils
        if (armor != null) {
            armor.removeModifier(TOUGH_ARMOR_MODIFIER_ID);
            if (toughLevel > 0) {
                armor.addTransientModifier(new AttributeModifier(TOUGH_ARMOR_MODIFIER_ID,
                        toughLevel * 3, AttributeModifier.Operation.ADD_VALUE));
            }
        }
        if (kbResistance != null) {
            kbResistance.removeModifier(TOUGH_KB_RESISTANCE_MODIFIER_ID);
            if (toughLevel > 0) {
                kbResistance.addTransientModifier(new AttributeModifier(TOUGH_KB_RESISTANCE_MODIFIER_ID,
                        toughLevel * 3, AttributeModifier.Operation.ADD_VALUE));
            }
        }

        // Only the shrink case keeps the fill fraction (see javadoc above);
        // growth is restored to the exact saved health via the SafePetHealth
        // floor at the join site, never by scaling the clamped loaded value
        float newMax = living.getMaxHealth();
        if (health != null && living.isAlive() && newMax > 0 && newMax < oldMax) {
            living.setHealth(Mth.clamp(healthFraction * newMax, 1.0F, newMax));
        }
    }

    private static boolean isWaterCategory(LivingEntity living) {
        MobCategory category = living.getType().getCategory();
        return category == MobCategory.WATER_CREATURE
                || category == MobCategory.UNDERGROUND_WATER_CREATURE
                || category == MobCategory.WATER_AMBIENT;
    }

    @SubscribeEvent
    public void onEntityLeaveLevel(EntityLeaveLevelEvent event) {
        if (!(event.getEntity() instanceof LivingEntity living)) return;

        if (!living.level().isClientSide && living.isAlive() && TameableUtils.isTamed(living)
                && TameableUtils.shouldUnloadToLantern(living)) {
            UUID ownerUUID = TameableUtils.getOwnerUUIDOf(living);
            String saveName = living.hasCustomName() ? living.getCustomName().getString() : "";
            DIWorldData data = DIWorldData.get(living.level());
            if (data != null) {
                String entityTypeKey = BuiltInRegistries.ENTITY_TYPE.getKey(living.getType()).toString();
                // Snapshot the pet like the death path does, so the lantern can
                // rebuild it if the entity itself is unrecoverable later
                CompoundTag snapshot = new CompoundTag();
                living.addAdditionalSaveData(snapshot);
                DIAttachments.writePetDataTo(living, snapshot);
                LanternRequest request = new LanternRequest(living.getUUID(), entityTypeKey, ownerUUID,
                        living.blockPosition(), living.level().dayTime(), saveName,
                        living.level().dimension().toString(), snapshot);
                data.addLanternRequest(request);
            }
        }
        if (TameableUtils.couldBeTamed(living) && TameableUtils.hasEnchant(living, DIEnchantmentKeys.HEALTH_BOOST)) {
            TameableUtils.setSafePetHealth(living, living.getHealth());
        }
    }

    /**
     * Removes any queued wayward-lantern retrieval requests for the given pet
     * UUID. Must be called whenever a pet's data leaves the world through a
     * path that will never re-join under the same UUID - death, total-recall
     * capture, bucket pickup - because a stale request carries a full entity
     * snapshot that the lantern timeout would otherwise use to rebuild a second
     * copy of the pet along with its enchanted collar.
     */
    public static void purgeLanternRequests(Level level, UUID petUUID) {
        if (level == null || level.isClientSide) return;
        DIWorldData data = DIWorldData.get(level);
        if (data != null) {
            data.removeMatchingLanternRequests(petUUID);
        }
    }

    // =========================================================================
    // Level tick - cross-dimension pet teleportation
    // =========================================================================

    @SubscribeEvent
    public void onLevelTick(LevelTickEvent.Post event) {
        Level level = event.getLevel();
        if (!level.isClientSide) {
            COLLAR_TICK_TRACKER_MAP.computeIfAbsent(level, k -> new CollarTickTracker()).tick();
        }
        if (!level.isClientSide && level instanceof ServerLevel serverLevel) {
            processPendingTeleports(serverLevel);
            if (serverLevel.getGameTime() % 10 == 0) {
                tickXpTransferPets(serverLevel);
            }
        }
    }

    /**
     * XP transfer: every 10 ticks, idle enchanted pets near their owner vacuum
     * experience orbs within 10 blocks; orbs that reach the pet credit their
     * value directly to the OWNER. The pet walks toward the nearest orb.
     */
    private void tickXpTransferPets(ServerLevel level) {
        for (ServerPlayer owner : level.players()) {
            List<Mob> pets = level.getEntitiesOfClass(Mob.class, owner.getBoundingBox().inflate(10.0D),
                    pet -> TameableUtils.hasAnyEnchantsCheap(pet)
                            && pet.getTarget() == null
                            && !isOrderedToStay(pet)
                            && TameableUtils.isPetOf(owner, pet)
                            && TameableUtils.hasEnchant(pet, DIEnchantmentKeys.XP_TRANSFER));
            for (Mob pet : pets) {
                vacuumXpOrbsToOwner(pet, owner);
            }
        }
    }

    private static boolean isOrderedToStay(Mob pet) {
        if (pet instanceof ModifedToBeTameable modified) return modified.isStayingStill();
        if (pet instanceof TamableAnimal tame) return tame.isOrderedToSit();
        return TameableUtils.tryGetCommand(pet) == 1;
    }

    private void vacuumXpOrbsToOwner(Mob pet, Player owner) {
        List<ExperienceOrb> orbs = pet.level().getEntitiesOfClass(ExperienceOrb.class,
                pet.getBoundingBox().inflate(10.0D));
        if (orbs.isEmpty()) return;

        Vec3 mouth = new Vec3(pet.getX(), pet.getY() + pet.getEyeHeight() / 2.0D, pet.getZ());
        ExperienceOrb nearest = null;
        double nearestDistSqr = Double.MAX_VALUE;
        for (ExperienceOrb orb : orbs) {
            Vec3 pull = mouth.subtract(orb.position());
            double distSqr = pull.lengthSqr();
            if (distSqr < 2.0D) {
                owner.giveExperiencePoints(orb.getValue());
                orb.discard();
                continue;
            }
            if (distSqr < 64.0D) {
                // Same falloff curve the rejuvenation vacuum uses
                double strength = 1.0D - Math.sqrt(distSqr) / 8.0D;
                orb.setDeltaMovement(orb.getDeltaMovement().add(pull.normalize().scale(strength * strength * 0.5D)));
            }
            if (distSqr < nearestDistSqr) {
                nearestDistSqr = distSqr;
                nearest = orb;
            }
        }
        if (nearest != null) {
            pet.getLookControl().setLookAt(nearest, 30.0F, 30.0F);
            pet.getNavigation().moveTo(nearest, 1.2D);
        }
    }

    @SubscribeEvent
    public void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof Level level) {
            COLLAR_TICK_TRACKER_MAP.remove(level);
        }
    }

    private void processPendingTeleports(ServerLevel currentLevel) {
        if (teleportingPets.isEmpty()) return;
        // Only handle entries bound for this level; the rest wait for their
        // own level's tick instead of being consumed (and lost) here
        Iterator<PendingPetTeleport> iterator = teleportingPets.iterator();
        while (iterator.hasNext()) {
            PendingPetTeleport pending = iterator.next();
            ServerLevel targetLevel = pending.targetLevel();
            if (targetLevel != currentLevel) continue;
            iterator.remove();

            Entity entity = pending.entity();
            // A pet that died while queued must not be resurrected in the target level
            if (!entity.isAlive()) continue;
            UUID ownerUUID = pending.ownerUUID();
            entity.unRide();

            Entity player = targetLevel.getPlayerByUUID(ownerUUID);
            if (player != null) {
                Entity teleported = entity.getType().create(targetLevel);
                if (teleported != null) {
                    teleported.restoreFrom(entity);
                    Vec3 toPos = findSafePosition(entity, targetLevel, player.position());
                    teleported.moveTo(toPos.x, toPos.y, toPos.z, entity.getYRot(), entity.getXRot());
                    teleported.setYHeadRot(entity.getYHeadRot());
                    teleported.fallDistance = 0.0F;
                    teleported.setPortalCooldown();
                    targetLevel.addFreshEntity(teleported);
                }
                entity.remove(Entity.RemovalReason.DISCARDED);
            }
        }
    }

    private Vec3 findSafePosition(Entity entity, Level level, Vec3 startPos) {
        Vec3 toPos = startPos;
        EntityDimensions dimensions = entity.getDimensions(entity.getPose());
        AABB suffocationBox = new AABB(
                -dimensions.width() / 2.0F, 0, -dimensions.width() / 2.0F,
                dimensions.width() / 2.0F, dimensions.height(), dimensions.width() / 2.0F);
        while (!level.noCollision(entity, suffocationBox.move(toPos.x, toPos.y, toPos.z)) && toPos.y < 300) {
            toPos = toPos.add(0, 1, 0);
        }
        return toPos;
    }

    // =========================================================================
    // Projectile impact - deflection enchant
    // =========================================================================

    @SubscribeEvent
    public void onProjectileImpact(ProjectileImpactEvent event) {
        if (!(event.getRayTraceResult() instanceof EntityHitResult entityHit)) return;

        Entity hit = entityHit.getEntity();

        // Prevent owner's projectiles from hitting their pets
        if (event.getProjectile().getOwner() instanceof Player player && TameableUtils.isPetOf(player, hit)) {
            event.setCanceled(true);
            return;
        }

        if (!TameableUtils.isTamed(hit) || !(hit instanceof LivingEntity livingHit)) return;

        // Fix vanilla crash with piercing arrows hitting tamed mobs
        if (event.getEntity() instanceof AbstractArrow arrow && arrow.getPierceLevel() > 0) {
            arrow.remove(Entity.RemovalReason.DISCARDED);
            event.setCanceled(true);
            return;
        }

        // Deflection enchant
        if (TameableUtils.hasEnchant(livingHit, DIEnchantmentKeys.DEFLECTION)) {
            event.setCanceled(true);
            float xRot = event.getProjectile().getXRot();
            float yRot = event.getProjectile().yRotO;
            Vec3 vec3 = event.getProjectile().position().subtract(hit.position()).normalize()
                    .scale(hit.getBbWidth() + 0.5F);
            Vec3 particlePos = hit.position().add(vec3);
            hit.level().addParticle(DIParticleRegistry.DEFLECTION_SHIELD.get(),
                    particlePos.x, particlePos.y, particlePos.z, xRot, yRot, 0.0F);
            event.getProjectile().setDeltaMovement(event.getProjectile().getDeltaMovement().scale(-0.2D));
            event.getProjectile().setYRot(yRot + 180);
            event.getProjectile().setXRot(xRot + 180);
        }
    }

    // =========================================================================
    // Item despawn → rotten apple
    // =========================================================================

    @SubscribeEvent
    public void onItemDespawn(ItemExpireEvent event) {
        if (event.getEntity().getItem().getItem() != Items.APPLE || !DomesticationMod.CONFIG.rottenApple.get()) return;

        // FIX: Use entity RNG instead of new Random()
        if (event.getEntity().level().getRandom().nextFloat() < 0.1F * event.getEntity().getItem().getCount()) {
            event.getEntity().getItem().shrink(1);
            event.setExtraLife(10);
            ItemEntity rotten = new ItemEntity(event.getEntity().level(),
                    event.getEntity().getX(), event.getEntity().getY(), event.getEntity().getZ(),
                    new ItemStack(DIItemRegistry.ROTTEN_APPLE.get()));
            event.getEntity().level().addFreshEntity(rotten);
        }
    }

    // =========================================================================
    // Entity tick - collar enchantment logic (DECOMPOSED from original 300+ line method)
    // =========================================================================

    @SubscribeEvent
    public void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity living)) return;

        // Frost fang slow effect (applies to any entity, not just pets)
        tickFrostFangSlow(living);

        // Pet collar enchantment ticking
        if (!TameableUtils.couldBeTamed(living)) return;

        // Periodic bed-link revalidation, staggered by entity id so beds
        // broken or claimed elsewhere are noticed without a per-tick cost
        if (!living.level().isClientSide && (living.tickCount + living.getId()) % 200 == 0) {
            PetBedBlockEntity.revalidateBedLink(living);
        }

        // Data-tamed brain mobs get their bed-anchored roaming via
        // restrictTo instead of goal surgery
        if (!living.level().isClientSide && living instanceof Mob mob && TameableUtils.isDataTamed(mob)) {
            GenericPetAI.tickBrainRoamRestriction(mob);
        }

        if (!canTickCollar(living)) return;

        // One cheap attachment peek gates the enchant ticks for the common
        // case of wild/unenchanted animals
        if (TameableUtils.hasAnyEnchantsCheap(living)) {
            tickDefensiveEnchants(living);
            tickCombatEnchants(living);
            tickUtilityEnchants(living);
            tickCurseEnchants(living);
        }
        tickZombiePetAI(living);
    }

    private void tickFrostFangSlow(LivingEntity living) {
        int frozenTime = TameableUtils.getFrozenTime(living);
        if (frozenTime <= 0) return;

        TameableUtils.setFrozenTimeTag(living, frozenTime - 1);
        AttributeInstance instance = living.getAttribute(Attributes.MOVEMENT_SPEED);
        if (instance != null) {
            float slowAmount = -0.1F * living.getPercentFrozen();
            if (frozenTime > 1) {
                AttributeModifier fangModifier = new AttributeModifier(FROST_FANG_SLOW_ID, slowAmount,
                        AttributeModifier.Operation.ADD_VALUE);
                if (!instance.hasModifier(FROST_FANG_SLOW_ID)) {
                    instance.addTransientModifier(fangModifier);
                }
            } else {
                instance.removeModifier(FROST_FANG_SLOW_ID);
            }
        }
        for (int i = 0; i < 1 + living.getRandom().nextInt(2); i++) {
            living.level().addParticle(ParticleTypes.SNOWFLAKE,
                    living.getRandomX(0.7F), living.getRandomY(), living.getRandomZ(0.7F),
                    0.0F, 0.0F, 0.0F);
        }
    }

    private void tickDefensiveEnchants(LivingEntity living) {
        // Immunity frame cooldown
        if (TameableUtils.hasEnchant(living, DIEnchantmentKeys.IMMUNITY_FRAME) && !living.level().isClientSide) {
            int immuneTime = TameableUtils.getImmuneTime(living);
            if (immuneTime > 0) {
                TameableUtils.setImmuneTime(living, immuneTime - 1);
            }
        }

        // Poison resistance
        if (living.hasEffect(MobEffects.POISON) && TameableUtils.hasEnchant(living, DIEnchantmentKeys.POISON_RESISTANCE)) {
            living.removeEffect(MobEffects.POISON);
        }

        // Amphibious - infinite air
        if (TameableUtils.hasEnchant(living, DIEnchantmentKeys.AMPHIBIOUS)) {
            living.setAirSupply(living.getMaxAirSupply());
        }

        // Blazing protection bar regeneration
        if (TameableUtils.hasEnchant(living, DIEnchantmentKeys.BLAZING_PROTECTION) && !living.level().isClientSide) {
            int bars = TameableUtils.getBlazingProtectionBars(living);
            int maxBars = 2 * TameableUtils.getEnchantLevel(living, DIEnchantmentKeys.BLAZING_PROTECTION);
            if (bars < maxBars) {
                int cooldown = TameableUtils.getBlazingProtectionCooldown(living);
                if (cooldown > 0) {
                    TameableUtils.setBlazingProtectionCooldown(living, cooldown - 1);
                } else {
                    TameableUtils.setBlazingProtectionBars(living, bars + 1);
                    TameableUtils.setBlazingProtectionCooldown(living, 200);
                }
            }
        }

        // Healing aura
        if (TameableUtils.hasEnchant(living, DIEnchantmentKeys.HEALING_AURA) && !living.level().isClientSide) {
            tickHealingAura(living);
        }

        // Void cloud flight
        if (TameableUtils.hasEnchant(living, DIEnchantmentKeys.VOID_CLOUD)
                && !living.isInWaterOrBubble() && living.fallDistance > 3.0F && !living.onGround()) {
            tickVoidCloud(living);
        }
    }

    private void tickCombatEnchants(LivingEntity living) {
        // Magnetic pull
        if (TameableUtils.hasEnchant(living, DIEnchantmentKeys.MAGNETIC) && living instanceof Mob mob) {
            tickMagnetic(mob);
        }

        // Shadow hands
        int shadowHandsLevel = TameableUtils.getEnchantLevel(living, DIEnchantmentKeys.SHADOW_HANDS);
        if (shadowHandsLevel > 0 && living instanceof Mob mob) {
            DomesticationMod.PROXY.updateVisualDataForMob(living, TameableUtils.getShadowPunchTimes(mob));
            if (!mob.level().isClientSide) {
                tickShadowHands(mob, shadowHandsLevel);
            }
        }

        // Psychic wall
        int psychicWallLevel = TameableUtils.getEnchantLevel(living, DIEnchantmentKeys.PSYCHIC_WALL);
        if (psychicWallLevel > 0 && living instanceof Mob mob && !living.level().isClientSide) {
            tickPsychicWall(mob, psychicWallLevel);
        }

        // Sonic boom
        if (living instanceof Mob mob && !living.level().isClientSide
                && TameableUtils.hasEnchant(living, DIEnchantmentKeys.SONIC_BOOM)) {
            tickSonicBoom(mob);
        }
    }

    private void tickUtilityEnchants(LivingEntity living) {
        // Disc jockey - spawn following jukebox
        if (TameableUtils.hasEnchant(living, DIEnchantmentKeys.DISK_JOCKEY)
                && !living.level().isClientSide && living.tickCount % 10 == 0) {
            UUID uuid = TameableUtils.getPetJukeboxUUID(living);
            if (uuid == null || !(((ServerLevel) living.level()).getEntity(uuid) instanceof FollowingJukeboxEntity)) {
                FollowingJukeboxEntity follower = DIEntityRegistry.FOLLOWING_JUKEBOX.get().create(living.level());
                follower.setFollowingUUID(living.getUUID());
                follower.copyPosition(living);
                living.level().addFreshEntity(follower);
                TameableUtils.setPetJukeboxUUID(living, follower.getUUID());
            }
        }

        // Linked inventory - enable loot pickup
        if (TameableUtils.hasEnchant(living, DIEnchantmentKeys.LINKED_INVENTORY) && living instanceof Mob mob) {
            if (!mob.canPickUpLoot()) {
                mob.setCanPickUpLoot(true);
            }
        }

        // Shepherd/herding
        int shepherdLvl = TameableUtils.getEnchantLevel(living, DIEnchantmentKeys.SHEPHERD);
        if (shepherdLvl > 0) {
            TameableUtils.attractAnimals(living, shepherdLvl * 3);
        }

        // Intimidation
        if (TameableUtils.hasEnchant(living, DIEnchantmentKeys.INTIMIDATION)) {
            TameableUtils.scareRandomMonsters(living, TameableUtils.getEnchantLevel(living, DIEnchantmentKeys.INTIMIDATION));
        }

        // Rejuvenation - absorb XP; the tick event still fires during the death
        // animation, and a dying pet must not eat orbs or get its health raised
        if (living.isAlive() && TameableUtils.hasEnchant(living, DIEnchantmentKeys.REJUVENATION)) {
            TameableUtils.absorbExpOrbs(living);
        }

        // Ore scenting
        int oreLvl = TameableUtils.getEnchantLevel(living, DIEnchantmentKeys.ORE_SCENTING);
        if (oreLvl > 0 && !living.level().isClientSide) {
            int interval = 100 + Math.max(150, 550 - oreLvl * 100);
            TameableUtils.detectRandomOres(living, interval, 5 + oreLvl * 2, oreLvl * 50, oreLvl * 3);
        }

        // Insight - reveal enemies in the dark. The Glowing pulse lasts 20
        // ticks, so a 10-tick stagger keeps it seamless without paying for the
        // (potentially 45-block) enemy scan every tick
        int insightLvl = TameableUtils.getEnchantLevel(living, DIEnchantmentKeys.INSIGHT);
        if (insightLvl > 0 && !living.level().isClientSide
                && (living.tickCount + living.getId()) % 10 == 0
                && living.level().getMaxLocalRawBrightness(living.getOnPos().above()) < 9) {
            for (LivingEntity enemy : getNearbyEnemies(living, insightLvl * 15)) {
                enemy.addEffect(new MobEffectInstance(MobEffects.GLOWING, 20));
            }
        }

        // Night vision - grant the nearby owner long night vision, re-applied
        // once the old effect runs out
        if (living.tickCount % 40 == 0 && !living.level().isClientSide
                && TameableUtils.hasEnchant(living, DIEnchantmentKeys.NIGHT_VISION)
                && TameableUtils.getOwnerOf(living) instanceof Player owner
                && owner.distanceToSqr(living) < 10.0D
                && !owner.hasEffect(MobEffects.NIGHT_VISION)) {
            owner.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 6000));
        }
    }

    private void tickCurseEnchants(LivingEntity living) {
        if (TameableUtils.hasEnchant(living, DIEnchantmentKeys.INFAMY_CURSE)) {
            TameableUtils.aggroRandomMonsters(living);
        }
        if (TameableUtils.hasEnchant(living, DIEnchantmentKeys.BLIGHT_CURSE)) {
            TameableUtils.destroyRandomPlants(living);
        }
    }

    private void tickZombiePetAI(LivingEntity living) {
        if (!TameableUtils.isZombiePet(living) || living.level().isClientSide || !(living instanceof Mob mob)) return;

        if (mob.getTarget() instanceof Player player && player.isCreative()) {
            mob.setTarget(null);
        }
        if (mob.getTarget() == null || !mob.getTarget().isAlive()) {
            mob.setTarget(mob.level().getNearestPlayer(ZOMBIE_TARGET, mob));
        } else if (mob.distanceTo(mob.getTarget()) < mob.getBbWidth() + 0.5F) {
            mob.doHurtTarget(mob.getTarget());
        } else if (mob.getNavigation().isDone()) {
            mob.getNavigation().moveTo(mob.getTarget(), 1.0D);
        }
    }

    // --- Complex enchantment tick helpers ---

    private void tickMagnetic(Mob mob) {
        Entity sucking = TameableUtils.getPetAttackTarget(mob);
        if (!mob.level().isClientSide) {
            if (mob.getTarget() == null || !mob.getTarget().isAlive()
                    || mob.distanceTo(mob.getTarget()) < 0.5F + mob.getBbWidth()
                    || mob.getRootVehicle() instanceof GiantBubbleEntity) {
                if (TameableUtils.getPetAttackTargetID(mob) != -1) {
                    TameableUtils.setPetAttackTarget(mob, -1);
                }
            } else {
                TameableUtils.setPetAttackTarget(mob, mob.getTarget().getId());
            }
        } else if (sucking != null) {
            double dist = mob.distanceTo(sucking);
            Vec3 start = mob.position().add(0, mob.getBbHeight() * 0.5F, 0);
            Vec3 end = sucking.position().add(0, sucking.getBbHeight() * 0.5F, 0).subtract(start);
            for (float distStep = mob.getBbWidth() + 0.8F; distStep < (int) Math.ceil(dist); distStep++) {
                Vec3 vec3 = start.add(end.scale(distStep / dist));
                float f1 = 0.5F * (mob.getRandom().nextFloat() - 0.5F);
                float f2 = 0.5F * (mob.getRandom().nextFloat() - 0.5F);
                float f3 = 0.5F * (mob.getRandom().nextFloat() - 0.5F);
                mob.level().addParticle(DIParticleRegistry.MAGNET.get(),
                        vec3.x + f1, vec3.y + f2, vec3.z + f3, 0.0F, 0.0F, 0.0F);
            }
        }
        if (sucking != null) {
            if (mob.tickCount % 15 == 0) {
                mob.playSound(DISoundRegistry.MAGNET_LOOP.get(), 1F, 1F);
            }
            mob.setDeltaMovement(mob.getDeltaMovement().multiply(0.88D, 1.0D, 0.88D));
            Vec3 pullVec = new Vec3(mob.getX() - sucking.getX(),
                    mob.getY() - (double) sucking.getEyeHeight() / 2.0D - sucking.getY(),
                    mob.getZ() - sucking.getZ());
            sucking.setDeltaMovement(sucking.getDeltaMovement().add(
                    pullVec.normalize().scale(mob.onGround() ? 0.15D : 0.05D)));
        }
    }

    private void tickShadowHands(Mob mob, int level) {
        Entity punching = TameableUtils.getPetAttackTarget(mob);
        int[] punchProgress = TameableUtils.getShadowPunchTimes(mob);

        if (punching != null && punching.isAlive() && mob.hasLineOfSight(punching) && mob.distanceTo(punching) < 16) {
            int[] striking = TameableUtils.getShadowPunchStriking(mob);
            if (punchProgress == null || punchProgress.length < level) {
                TameableUtils.setShadowPunchTimes(mob, new int[level]);
                TameableUtils.setShadowPunchStriking(mob, new int[level]);
            } else {
                int cooldown = TameableUtils.getShadowPunchCooldown(mob);
                if (cooldown <= 0) {
                    int start = level == 1 ? 0 : mob.getRandom().nextInt(level - 1);
                    for (int i = start; i < level; i++) {
                        if (striking[i] == 0) {
                            striking[i] = 1;
                            TameableUtils.setShadowPunchCooldown(mob, 5);
                            break;
                        }
                    }
                } else {
                    TameableUtils.setShadowPunchCooldown(mob, cooldown - 1);
                }
                for (int i = 0; i < Math.min(level, Math.min(striking.length, punchProgress.length)); i++) {
                    if (striking[i] != 0) {
                        if (punchProgress[i] < 10) {
                            punchProgress[i]++;
                        } else {
                            punching.hurt(punching.damageSources().mobAttack(mob), Mth.clamp(level, 2, 4));
                            striking[i] = 0;
                        }
                    }
                    if (striking[i] == 0 && punchProgress[i] > 0) {
                        punchProgress[i]--;
                    }
                }
                TameableUtils.setShadowPunchStriking(mob, striking);
                TameableUtils.setShadowPunchTimes(mob, punchProgress);
            }
        } else {
            // Wind down and find new target
            if (punching != null && punchProgress != null) {
                boolean allDone = true;
                for (int i = 0; i < Math.min(level, punchProgress.length); i++) {
                    if (punchProgress[i] > 0) {
                        punchProgress[i]--;
                        allDone = false;
                    }
                }
                TameableUtils.setShadowPunchStriking(mob, new int[level]);
                TameableUtils.setShadowPunchTimes(mob, punchProgress);
                if (allDone) {
                    TameableUtils.setPetAttackTarget(mob, -1);
                }
            }
            // Acquire new target from owner context
            Entity newTarget = findShadowHandTarget(mob);
            if (newTarget != null && newTarget.isAlive()) {
                TameableUtils.setPetAttackTarget(mob, newTarget.getId());
            }
        }
    }

    private Entity findShadowHandTarget(Mob mob) {
        if (mob.getTarget() != null) return mob.getTarget();

        if (TameableUtils.getOwnerOf(mob) instanceof LivingEntity owner) {
            if (owner.getLastHurtByMob() != null && owner.getLastHurtByMob().isAlive()
                    && !TameableUtils.hasSameOwnerAs(mob, owner.getLastHurtByMob())) {
                return owner.getLastHurtByMob();
            }
            if (owner.getLastHurtMob() != null && owner.getLastHurtMob().isAlive()
                    && !TameableUtils.hasSameOwnerAs(mob, owner.getLastHurtMob())) {
                return owner.getLastHurtMob();
            }
        }
        return null;
    }

    private void tickPsychicWall(Mob mob, int level) {
        int cooldown = TameableUtils.getPsychicWallCooldown(mob);
        if (cooldown > 0) {
            TameableUtils.setPsychicWallCooldown(mob, cooldown - 1);
            return;
        }

        Entity blocking = null;
        Entity blockingFrom = null;

        if (mob.getTarget() != null) {
            blocking = mob.getTarget();
            blockingFrom = mob;
        } else if (TameableUtils.getOwnerOf(mob) instanceof LivingEntity owner) {
            if (owner.getLastHurtByMob() != null && owner.getLastHurtByMob().isAlive()
                    && !TameableUtils.hasSameOwnerAs(mob, owner.getLastHurtByMob())) {
                blocking = owner.getLastHurtByMob();
                blockingFrom = owner;
            }
            if (owner.getLastHurtMob() != null && owner.getLastHurtMob().isAlive()
                    && !TameableUtils.hasSameOwnerAs(mob, owner.getLastHurtMob())) {
                blocking = owner.getLastHurtMob();
                blockingFrom = owner;
            }
        }

        if (blocking == null) return;

        int width = level + 1;
        float yAdditional = blocking.getBbHeight() * 0.5F + width * 0.5F;
        Vec3 vec3 = blockingFrom.position().add(0, yAdditional, 0);
        Vec3 vec32 = blocking.position().add(0, yAdditional, 0);
        Vec3 avg = vec3.add(vec32).scale(0.5);
        avg = new Vec3(avg.x, Math.floor(avg.y), avg.z);
        Vec3 rotationFrom = avg.subtract(vec3);
        Direction dir = Direction.getNearest(rotationFrom.x, rotationFrom.y, rotationFrom.z);

        PsychicWallEntity wall = DIEntityRegistry.PSYCHIC_WALL.get().create(mob.level());
        wall.setPos(avg.x, avg.y, avg.z);
        wall.setBlockWidth(width);
        wall.setCreatorId(mob.getUUID());
        wall.setLifespan(level * 100);
        wall.setWallDirection(dir);
        mob.level().addFreshEntity(wall);
        TameableUtils.setPsychicWallCooldown(mob, level * 200 + 40);
    }

    /**
     * Sonic boom: every 200 ticks, a pet that has a combat target within 10
     * blocks (20 vertically) - or is swarmed by more than 3 enemies within 10
     * blocks - fires a Warden-style sonic boom at its target. When more than 3
     * enemies stand within 5 blocks, all of them are hit instead.
     */
    private void tickSonicBoom(Mob mob) {
        // Timer first: everything below allocates or queries, and the boom can
        // only ever fire on a 200-tick boundary anyway
        if (mob.tickCount % 200 != 0) return;
        LivingEntity target = mob.getTarget();
        if (target == null || !target.isAlive()) return;

        ServerLevel serverLevel = (ServerLevel) mob.level();
        // Deliberate deviation: the swarm gate counts hostiles only. The
        // original counted ANY nearby mob, so a pet standing in a livestock
        // pen would boom at its own farm.
        if (!mob.closerThan(target, 10.0D, 20.0D) && getNearbyEnemies(mob, 10.0D).size() <= 3) return;

        List<LivingEntity> pointBlank = getNearbyEnemies(mob, 5.0D);
        if (pointBlank.size() > 3) {
            for (LivingEntity enemy : pointBlank) {
                sonicBoomAttack(mob, enemy, serverLevel);
            }
        } else {
            sonicBoomAttack(mob, target, serverLevel);
        }
    }

    private static List<LivingEntity> getNearbyEnemies(LivingEntity center, double range) {
        return center.level().getEntitiesOfClass(LivingEntity.class,
                center.getBoundingBox().inflate(range),
                e -> e != center && e instanceof Enemy && e.isAlive());
    }

    private static void sonicBoomAttack(Mob mob, LivingEntity target, ServerLevel serverLevel) {
        // Vanilla's sonic_boom damage type is usable here: DamageSources#sonicBoom
        // accepts any entity as the source, so no DI damage type is needed
        Vec3 chest = mob.position().add(mob.getAttachments().get(EntityAttachment.WARDEN_CHEST, 0, mob.getYRot()));
        Vec3 toTarget = target.getEyePosition().subtract(chest);
        Vec3 ray = toTarget.normalize();
        int particleSteps = Mth.floor(toTarget.length()) + 7;
        for (int step = 1; step < particleSteps; step++) {
            Vec3 pos = chest.add(ray.scale(step));
            serverLevel.sendParticles(ParticleTypes.SONIC_BOOM, pos.x, pos.y, pos.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
        mob.playSound(SoundEvents.WARDEN_SONIC_BOOM, 3.0F, 1.0F);
        if (target.hurt(serverLevel.damageSources().sonicBoom(mob), 10.0F)) {
            double resist = 1.0D - target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
            target.push(ray.x * 2.5D * resist, ray.y * 0.5D * resist, ray.z * 2.5D * resist);
        }
    }

    private void tickHealingAura(LivingEntity living) {
        int time = TameableUtils.getHealingAuraTime(living);
        if (time > 0) {
            List<LivingEntity> hurtNearby = TameableUtils.getAuraHealables(living);
            int amplifier = TameableUtils.getEnchantLevel(living, DIEnchantmentKeys.HEALING_AURA) - 1;
            for (LivingEntity needsHealing : hurtNearby) {
                if (!needsHealing.hasEffect(MobEffects.REGENERATION)) {
                    needsHealing.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, amplifier));
                }
            }
            time--;
            if (time == 0) {
                time = -600 - living.getRandom().nextInt(600);
            }
        } else if (time < 0) {
            time++;
        } else if ((living.tickCount + living.getId()) % 200 == 0 || TameableUtils.getHealingAuraImpulse(living)) {
            if (!TameableUtils.getAuraHealables(living).isEmpty()) {
                time = 200;
            }
            TameableUtils.setHealingAuraImpulse(living, false);
        }
        TameableUtils.setHealingAuraTime(living, time);
    }

    private void tickVoidCloud(LivingEntity living) {
        Entity owner = TameableUtils.getOwnerOf(living);
        boolean shouldMoveToOwnerXZ = owner != null && Math.abs(owner.getY() - living.getY()) < 1;
        double targetX = shouldMoveToOwnerXZ ? owner.getX() : living.getX();
        double targetY = Math.max(living.level().getMinBuildHeight() + 0.5F,
                owner == null ? 64F : owner.getY() < living.getY()
                        ? owner.getY() + 0.6F : owner.getY(1.0F) + living.getBbHeight());
        if (owner != null && owner.getRootVehicle() == living) {
            targetY = Math.min(living.level().getMinBuildHeight() + 0.5F, living.getY() - 0.5F);
        }
        double targetZ = shouldMoveToOwnerXZ ? owner.getZ() : living.getZ();
        if (living.verticalCollision) {
            living.setOnGround(true);
            targetX += (living.getRandom().nextFloat() - 0.5F) * 4;
            targetZ += (living.getRandom().nextFloat() - 0.5F) * 4;
        }
        Vec3 move = new Vec3(targetX - living.getX(), targetY - living.getY(), targetZ - living.getZ());
        living.setDeltaMovement(living.getDeltaMovement().add(move.normalize().scale(0.15D)).multiply(0.5F, 0.5F, 0.5F));
        if (living.level() instanceof ServerLevel serverLevel) {
            TameableUtils.setFallDistance(living, living.fallDistance);
            serverLevel.sendParticles(ParticleTypes.REVERSE_PORTAL,
                    living.getRandomX(1.5F), living.getY() - living.getRandom().nextFloat(),
                    living.getRandomZ(1.5F), 0, 0, -0.2F, 0, 1.0D);
        }
    }

    // =========================================================================
    // Damage events
    // =========================================================================

    @SubscribeEvent
    public void onLivingIncomingDamage(LivingIncomingDamageEvent event) {
        // A hit the target's shield will fully block deals no damage, so it must
        // neither consume defensive resources nor set off on-hit enchant effects.
        // This event fires before vanilla resolves the block, hence the pre-check.
        if (event.getAmount() > 0.0F && event.getEntity().isDamageSourceBlocked(event.getSource())) {
            return;
        }

        // --- Defensive enchantments on the target ---
        if (TameableUtils.isTamed(event.getEntity()) && !event.getSource().is(DIDamageTypes.SIPHON)) {
            boolean blocked = false;

            // Immunity frame
            if (TameableUtils.hasEnchant(event.getEntity(), DIEnchantmentKeys.IMMUNITY_FRAME)) {
                int level = TameableUtils.getEnchantLevel(event.getEntity(), DIEnchantmentKeys.IMMUNITY_FRAME);
                if (TameableUtils.getImmuneTime(event.getEntity()) <= 0) {
                    TameableUtils.setImmuneTime(event.getEntity(), 20 + level * 20);
                } else {
                    event.setCanceled(true);
                    blocked = true;
                }
            }

            // Blazing protection
            if (!blocked && TameableUtils.hasEnchant(event.getEntity(), DIEnchantmentKeys.BLAZING_PROTECTION)) {
                int bars = TameableUtils.getBlazingProtectionBars(event.getEntity());
                if (bars > 0) {
                    Entity attacker = event.getSource().getEntity();
                    if (attacker instanceof LivingEntity livingAttacker
                            && !TameableUtils.hasSameOwnerAs(livingAttacker, event.getEntity())) {
                        livingAttacker.igniteForSeconds(5 + event.getEntity().getRandom().nextInt(3));
                        livingAttacker.knockback(0.4, event.getEntity().getX() - livingAttacker.getX(),
                                event.getEntity().getZ() - livingAttacker.getZ());
                    }
                    event.setCanceled(true);
                    blocked = true;
                    if (attacker != null) {
                        for (int i = 0; i < 3 + event.getEntity().getRandom().nextInt(3); i++) {
                            attacker.level().addParticle(ParticleTypes.FLAME,
                                    event.getEntity().getRandomX(0.8F), event.getEntity().getRandomY(),
                                    event.getEntity().getRandomZ(0.8F), 0.0F, 0.0F, 0.0F);
                        }
                    }
                    event.getEntity().playSound(DISoundRegistry.BLAZING_PROTECTION.get(), 1, event.getEntity().getVoicePitch());
                    TameableUtils.setBlazingProtectionBars(event.getEntity(), bars - 1);
                    TameableUtils.setBlazingProtectionCooldown(event.getEntity(), 600);
                }
            }

            // Amphibious - block drowning/dry out
            if (!blocked && (event.getSource().is(DamageTypes.DROWN) || event.getSource().is(DamageTypes.DRY_OUT))
                    && TameableUtils.hasEnchant(event.getEntity(), DIEnchantmentKeys.AMPHIBIOUS)) {
                event.setCanceled(true);
                blocked = true;
            }

            // Void cloud - block fall damage
            if (!blocked && (event.getSource().is(DamageTypes.FALL) || event.getSource().is(DamageTypes.FELL_OUT_OF_WORLD))
                    && TameableUtils.hasEnchant(event.getEntity(), DIEnchantmentKeys.VOID_CLOUD)) {
                event.setCanceled(true);
                blocked = true;
            }

            // Health siphon - redirect damage to owner
            if (!blocked && TameableUtils.hasEnchant(event.getEntity(), DIEnchantmentKeys.HEALTH_SIPHON)) {
                Entity owner = TameableUtils.getOwnerOf(event.getEntity());
                if (owner != null && owner.isAlive() && owner.distanceTo(event.getEntity()) < 100 && owner != event.getEntity()) {
                    owner.hurt(event.getSource(), event.getAmount());
                    event.setCanceled(true);
                    blocked = true;
                    event.getEntity().hurt(DIDamageTypes.causeSiphonDamage(owner.level().registryAccess()), 0.0F);
                }
            }

            // Total recall - save pet when near death
            if (!blocked && TameableUtils.hasEnchant(event.getEntity(), DIEnchantmentKeys.TOTAL_RECALL)
                    && event.getEntity().getHealth() - event.getAmount() <= 2.0D
                    && !TameableUtils.isZombiePet(event.getEntity())) {
                UUID ownerUUID = TameableUtils.getOwnerUUIDOf(event.getEntity());
                if (ownerUUID != null) {
                    if (event.getEntity() instanceof Mob mob) mob.playAmbientSound();
                    event.getEntity().playSound(SoundEvents.ENDER_CHEST_CLOSE, 1.0F, 1.5F);

                    RecallBallEntity recallBall = DIEntityRegistry.RECALL_BALL.get().create(event.getEntity().level());
                    recallBall.setOwnerUUID(ownerUUID);
                    CompoundTag tag = new CompoundTag();
                    event.getEntity().addAdditionalSaveData(tag);
                    DIAttachments.writePetDataTo(event.getEntity(), tag);
                    recallBall.setContainedData(tag);
                    recallBall.setContainedEntityType(BuiltInRegistries.ENTITY_TYPE.getKey(event.getEntity().getType()).toString());
                    recallBall.setPos(event.getEntity().getX(),
                            Math.max(event.getEntity().getY(), event.getEntity().level().getMinBuildHeight() + 1),
                            event.getEntity().getZ());
                    recallBall.setYRot(event.getEntity().getYRot());
                    recallBall.setInvulnerable(true);
                    event.getEntity().stopRiding();
                    if (event.getEntity().level().addFreshEntity(recallBall)) {
                        // Ball release rebuilds the pet under a NEW UUID, so any
                        // lantern request snapshotted for the old one is stale
                        purgeLanternRequests(event.getEntity().level(), event.getEntity().getUUID());
                        event.getEntity().discard();
                    }
                    event.setCanceled(true);
                }
            }
        }

        // --- Offensive enchantments on the attacker ---
        if (event.getSource().getEntity() instanceof LivingEntity attacker && TameableUtils.isTamed(attacker)) {
            tickAttackerEnchants(attacker, event);
        }

        // --- Healing aura impulse ---
        // Healers can only heal entities sharing their owner, so only players
        // and potential pets warrant the healer scan
        if (!event.isCanceled()
                && (event.getEntity() instanceof Player || TameableUtils.couldBeTamed(event.getEntity()))) {
            List<LivingEntity> nearbyHealers = TameableUtils.getNearbyHealers(event.getEntity());
            for (LivingEntity healer : nearbyHealers) {
                TameableUtils.setHealingAuraImpulse(healer, true);
            }
        }
    }

    private void tickAttackerEnchants(LivingEntity attacker, LivingIncomingDamageEvent event) {
        LivingEntity target = event.getEntity();

        // Chain lightning
        int lightningLevel = TameableUtils.getEnchantLevel(attacker, DIEnchantmentKeys.CHAIN_LIGHTNING);
        if (lightningLevel > 0) {
            ChainLightningEntity lightning = DIEntityRegistry.CHAIN_LIGHTNING.get().create(target.level());
            lightning.setCreatorEntityID(attacker.getId());
            lightning.setFromEntityID(attacker.getId());
            lightning.setToEntityID(target.getId());
            lightning.copyPosition(target);
            lightning.setChainsLeft(3 + lightningLevel * 3);
            target.level().addFreshEntity(lightning);
            target.playSound(DISoundRegistry.CHAIN_LIGHTNING.get(), 1F, 1F);
        }

        // Frost fang
        if (TameableUtils.hasEnchant(attacker, DIEnchantmentKeys.FROST_FANG)) {
            target.setTicksFrozen(target.getTicksRequiredToFreeze() + 200);
            Vec3 vec3 = target.getEyePosition().subtract(attacker.getEyePosition()).normalize()
                    .scale(attacker.getBbWidth() + 0.5F);
            Vec3 particlePos = attacker.getEyePosition().add(vec3);
            for (int i = 0; i < 3 + attacker.getRandom().nextInt(3); i++) {
                float f1 = 0.2F * (attacker.getRandom().nextFloat() - 1.0F);
                float f2 = 0.2F * (attacker.getRandom().nextFloat() - 1.0F);
                float f3 = 0.2F * (attacker.getRandom().nextFloat() - 1.0F);
                attacker.level().addParticle(ParticleTypes.SNOWFLAKE,
                        particlePos.x + f1, particlePos.y + f2, particlePos.z + f3, 0, 0, 0);
            }
            TameableUtils.setFrozenTimeTag(target, 60);
        }

        // Bubbling
        int bubblingLevel = TameableUtils.getEnchantLevel(attacker, DIEnchantmentKeys.BUBBLING);
        if (bubblingLevel > 0 && !(target.getRootVehicle() instanceof GiantBubbleEntity)
                && (target.onGround() || target.isInWaterOrBubble() || target.isInLava())) {
            GiantBubbleEntity bubble = DIEntityRegistry.GIANT_BUBBLE.get().create(target.level());
            bubble.copyPosition(target);
            target.startRiding(bubble, true);
            bubble.setpopsIn(bubblingLevel * 40 + 40);
            target.level().addFreshEntity(bubble);
            target.playSound(DISoundRegistry.GIANT_BUBBLE_INFLATE.get(), 1F, 1F);
        }

        // Vampire
        int vampireLevel = TameableUtils.getEnchantLevel(attacker, DIEnchantmentKeys.VAMPIRE);
        if (vampireLevel > 0 && attacker.getHealth() < attacker.getMaxHealth()) {
            float healAmount = Mth.clamp(event.getAmount() * vampireLevel * 0.5F, 1F, 10F);
            attacker.heal(healAmount);
            if (target.level() instanceof ServerLevel serverLevel) {
                for (int i = 0; i < 5 + target.getRandom().nextInt(3); i++) {
                    double f1 = target.getRandomX(0.7F);
                    double f2 = target.getY(0.4F + target.getRandom().nextFloat() * 0.2F);
                    double f3 = target.getRandomZ(0.7F);
                    Vec3 motion = attacker.getEyePosition().subtract(f1, f2, f3).normalize().scale(0.2F);
                    serverLevel.sendParticles(DIParticleRegistry.VAMPIRE.get(), f1, f2, f3, 1,
                            motion.x, motion.y, motion.z, 0.2F);
                }
            }
        }

        // Warping bite
        if (TameableUtils.hasEnchant(attacker, DIEnchantmentKeys.WARPING_BITE) && !target.level().isClientSide) {
            for (int i = 0; i < 16; ++i) {
                double d3 = target.getX() + (attacker.getRandom().nextDouble() - 0.5D) * 16.0D;
                double d4 = Mth.clamp(target.getY() + (double) (attacker.getRandom().nextInt(16) - 8),
                        target.level().getMinBuildHeight(),
                        target.level().getMinBuildHeight() + ((ServerLevel) target.level()).getLogicalHeight() - 1);
                double d5 = target.getZ() + (attacker.getRandom().nextDouble() - 0.5D) * 16.0D;
                if (target.randomTeleport(d3, d4, d5, true)) {
                    SoundEvent sound = target instanceof Fox ? SoundEvents.FOX_TELEPORT : SoundEvents.CHORUS_FRUIT_TELEPORT;
                    target.playSound(sound, 1.0F, 1.0F);
                    break;
                }
            }
        }
    }

    // =========================================================================
    // Living damage (post-attack, pre-final) - NeoForge: LivingDamageEvent.Pre
    // =========================================================================

    @SubscribeEvent
    public void onLivingDamage(LivingDamageEvent.Pre event) {
        // Prevent owner from damaging their own pet (unless sneaking)
        if (TameableUtils.isTamed(event.getEntity())
                && event.getSource().getDirectEntity() instanceof Player player
                && TameableUtils.isPetOf(player, event.getEntity())
                && !player.isShiftKeyDown()) {
            event.setNewDamage(0);
            return;
        }

        // --- Attacker-side: pet's landed hits ---
        if (event.getSource().getEntity() instanceof LivingEntity pet && TameableUtils.isTamed(pet)
                && TameableUtils.hasAnyEnchantsCheap(pet)) {
            // Violent - random on-hit mayhem
            if (TameableUtils.hasEnchant(pet, DIEnchantmentKeys.VIOLENT)) {
                rollViolentWheel(event);
            }
            // Immaturity curse - reduce pet damage output
            if (TameableUtils.hasEnchant(pet, DIEnchantmentKeys.IMMATURITY_CURSE)) {
                event.setNewDamage((float) Math.ceil(event.getNewDamage() * 0.7F));
            }
        }

        // --- Victim-side: retaliation enchants on a hurt pet ---
        LivingEntity victim = event.getEntity();
        if (TameableUtils.isTamed(victim) && TameableUtils.hasAnyEnchantsCheap(victim)
                && event.getSource().getEntity() instanceof LivingEntity attacker && attacker != victim) {
            // Chaos - the attacker staggers away drunk
            if (TameableUtils.hasEnchant(victim, DIEnchantmentKeys.CHAOS)) {
                attacker.addEffect(new MobEffectInstance(DIEffectRegistry.DRUNK, 120, 1));
            }
            // Paralysis - lock the attacker down, scaling duration with level
            int paralysisLevel = TameableUtils.getEnchantLevel(victim, DIEnchantmentKeys.PARALYSIS);
            if (paralysisLevel > 0) {
                applyParalysisDebuffs(attacker, paralysisLevel * 20);
            }
            // Share - spread a cut of the pain to every enemy in earshot
            if (TameableUtils.hasEnchant(victim, DIEnchantmentKeys.SHARE) && !SHARING_DAMAGE.get()) {
                shareDamageWithEnemies(victim, event);
            }
        }
    }

    /**
     * Violent: every hit the pet lands rolls once on a wheel of side effects,
     * from a rare outright kill down to a minor slow/weaken. Damage tweaks are
     * applied in-pipeline via setNewDamage so armor, absorption, totems and
     * other mods' damage hooks all still apply (the reference mod's "instant
     * kill" called die() without dealing damage, dropping loot from a mob that
     * then stayed alive - deal an actually lethal hit instead).
     */
    private void rollViolentWheel(LivingDamageEvent.Pre event) {
        LivingEntity target = event.getEntity();
        float roll = target.getRandom().nextFloat();
        if (roll < 0.01F) {
            event.setNewDamage(Float.MAX_VALUE);
        } else if (roll < 0.11F) {
            target.addEffect(new MobEffectInstance(MobEffects.POISON, 100));
        } else if (roll < 0.21F) {
            applyParalysisDebuffs(target, 20);
        } else if (roll < 0.41F) {
            event.setNewDamage(event.getOriginalDamage() + 3.0F);
        } else if (roll < 0.60F) {
            target.igniteForTicks(100);
        } else if (roll < 0.70F) {
            event.setNewDamage(target.getHealth() > 30.0F
                    ? target.getHealth() / 3.0F : event.getOriginalDamage() + 5.0F);
        } else if (roll < 0.80F) {
            target.addEffect(new MobEffectInstance(DIEffectRegistry.DRUNK, 100));
        } else {
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 50, false, false));
            target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 20, 50, false, false));
        }
    }

    private static void applyParalysisDebuffs(LivingEntity target, int duration) {
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, duration, 100, false, false));
        target.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, duration, 100, false, false));
        target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, duration, 100, false, false));
    }

    /**
     * Share: when the hurt pet has more than one enemy within 20 blocks, each
     * of them takes 30% of the original damage with the same damage source.
     * The re-entrancy guard stops a shared hit on another SHARE pet (or any
     * mod echoing the source) from cascading back through this handler.
     */
    private void shareDamageWithEnemies(LivingEntity victim, LivingDamageEvent.Pre event) {
        List<LivingEntity> enemies = getNearbyEnemies(victim, 20.0D);
        if (enemies.size() <= 1) return;
        float shared = event.getOriginalDamage() * 0.3F;
        SHARING_DAMAGE.set(true);
        try {
            for (LivingEntity enemy : enemies) {
                enemy.hurt(event.getSource(), shared);
            }
        } finally {
            SHARING_DAMAGE.set(false);
        }
    }

    // =========================================================================
    // Death - pet bed respawn, death messages, undead curse
    // =========================================================================

    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        if (!TameableUtils.isTamed(event.getEntity()) || TameableUtils.isZombiePet(event.getEntity())) return;

        // A dead pet never re-joins under its saved UUID (even a bed respawn
        // builds a fresh entity from the snapshot), so any lantern retrieval
        // request still queued for it is stale - drop it before its snapshot
        // can rebuild a duplicate pet carrying a duplicate collar
        purgeLanternRequests(event.getEntity().level(), event.getEntity().getUUID());

        // Pet bed respawn registration
        BlockPos bedPos = TameableUtils.getPetBedPos(event.getEntity());
        boolean queuedRespawn = false;
        if (bedPos != null && DomesticationMod.CONFIG.petBedRespawns.get()) {
            CompoundTag data = new CompoundTag();
            event.getEntity().addAdditionalSaveData(data);
            DIAttachments.writePetDataTo(event.getEntity(), data);
            String saveName = event.getEntity().hasCustomName() ? event.getEntity().getCustomName().getString() : "";
            String entityTypeKey = BuiltInRegistries.ENTITY_TYPE.getKey(event.getEntity().getType()).toString();
            RespawnRequest request = new RespawnRequest(entityTypeKey, TameableUtils.getPetBedDimension(event.getEntity()),
                    data, bedPos, event.getEntity().level().dayTime(), saveName);
            DIWorldData worldData = DIWorldData.get(event.getEntity().level());
            if (worldData != null) {
                worldData.addRespawnRequest(request);
                queuedRespawn = true;
            }
        }
        if (bedPos != null && !queuedRespawn && !event.getEntity().level().isClientSide) {
            // No respawn coming, so the pet's death frees its bed for another pet
            PetBedBlockEntity.releaseClaim(event.getEntity().level(), bedPos, event.getEntity().getUUID());
        }
        if (!queuedRespawn && DomesticationMod.CONFIG.collarDropsOnDeath.get()
                && !event.getEntity().level().isClientSide
                && TameableUtils.hasCollar(event.getEntity())
                && !willResurrectAsZombiePet(event.getEntity())) {
            // No respawn was queued, so the pet is gone for good whether or not
            // it had a bed; return the collar itself - unless a zombie copy
            // inherits the collar data or its curse vanishes it. Rebuild from
            // the RAW stored enchants so config-disabled ones survive on the
            // item instead of being silently destroyed.
            Map<ResourceLocation, Integer> enchants = TameableUtils.getEnchantsRaw(event.getEntity());
            if (enchants == null || !enchants.containsKey(VANISHING_CURSE_ID)) {
                ItemStack collar = rebuildCollarStack(event.getEntity(), enchants);
                if (event.getEntity().hasCustomName()) {
                    collar.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                            event.getEntity().getCustomName());
                }
                event.getEntity().spawnAtLocation(collar);
            }
        }

        // Death message for non-TamableAnimal tamed mobs
        if (!(event.getEntity() instanceof TamableAnimal)) {
            Entity owner = TameableUtils.getOwnerOf(event.getEntity());
            if (!event.getEntity().level().isClientSide
                    && event.getEntity().level().getGameRules().getBoolean(GameRules.RULE_SHOWDEATHMESSAGES)
                    && owner instanceof ServerPlayer serverPlayer) {
                serverPlayer.sendSystemMessage(event.getEntity().getCombatTracker().getDeathMessage());
            }
        }

        // Undead curse - zombie pet resurrection
        if (willResurrectAsZombiePet(event.getEntity())) {
            spawnZombiePet((Mob) event.getEntity());
        }
    }

    private static boolean willResurrectAsZombiePet(LivingEntity entity) {
        return entity instanceof Mob
                && entity.level().getDifficulty() != Difficulty.PEACEFUL
                && TameableUtils.hasEnchant(entity, DIEnchantmentKeys.UNDEAD_CURSE);
    }

    /**
     * Rebuilds a collar tag item carrying the given stored enchantments, for
     * returning a pet's collar to the world (swap and death paths).
     */
    private static ItemStack rebuildCollarStack(LivingEntity living, @Nullable Map<ResourceLocation, Integer> enchants) {
        ItemStack collar = new ItemStack(DIItemRegistry.COLLAR_TAG.get());
        if (enchants != null && !enchants.isEmpty()) {
            var registry = living.level().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
            for (Map.Entry<ResourceLocation, Integer> entry : enchants.entrySet()) {
                var holder = registry.get(ResourceKey.create(net.minecraft.core.registries.Registries.ENCHANTMENT, entry.getKey()));
                holder.ifPresent(h -> collar.enchant(h, entry.getValue()));
            }
        }
        return collar;
    }

    private void spawnZombiePet(Mob mob) {
        Mob zombieCopy = (Mob) mob.getType().create(mob.level());
        if (zombieCopy == null) return;

        int id = zombieCopy.getId();
        CompoundTag livingNbt = new CompoundTag();
        mob.addAdditionalSaveData(livingNbt);
        DIAttachments.writePetDataTo(mob, livingNbt);
        livingNbt.putString("DeathLootTable", BuiltInLootTables.EMPTY.location().toString());
        zombieCopy.readAdditionalSaveData(livingNbt);
        DIAttachments.readPetDataFrom(zombieCopy, livingNbt);
        zombieCopy.setId(id);

        // Clear tame status
        if (zombieCopy instanceof TamableAnimal tamed) {
            tamed.setTame(false, false);
            tamed.setOwnerUUID(null);
            tamed.setOrderedToSit(false);
        }
        if (zombieCopy instanceof ModifedToBeTameable tameable) {
            tameable.setTame(false);
            tameable.setTameOwnerUUID(null);
        }
        TameableUtils.trySetCommand(zombieCopy, 0);

        Entity owner = TameableUtils.getOwnerOf(mob);
        zombieCopy.copyPosition(mob);
        zombieCopy.setTarget(owner instanceof Player player && !player.isCreative()
                ? player : mob.level().getNearestPlayer(ZOMBIE_TARGET, mob));
        mob.level().addFreshEntity(zombieCopy);
        zombieCopy.setHealth(zombieCopy.getMaxHealth());
        TameableUtils.setZombiePet(zombieCopy, true);
    }

    // =========================================================================
    // Mounting - prevent dismounting from bubbles
    // =========================================================================

    @SubscribeEvent
    public void onEntityMount(EntityMountEvent event) {
        if (event.getEntityBeingMounted() instanceof GiantBubbleEntity && event.isDismounting()
                && event.getEntityBeingMounted().isAlive()) {
            event.setCanceled(true);
        }
    }

    // =========================================================================
    // Entity interaction - collar tags, deed of ownership, taming, feeding
    // =========================================================================

    @SubscribeEvent
    public void onInteractWithEntity(PlayerInteractEvent.EntityInteract event) {
        Player player = event.getEntity();
        Entity entity = event.getTarget();
        ItemStack stack = event.getItemStack();

        // Deed of ownership
        if (TameableUtils.isTamed(entity) && stack.is(DIItemRegistry.DEED_OF_OWNERSHIP.get())) {
            if (handleDeedOfOwnership(player, entity, stack, event)) return;
        }

        // Block interaction with zombie pets
        if (TameableUtils.couldBeTamed(entity) && TameableUtils.isZombiePet((LivingEntity) entity)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }

        // Sneaking steps aside from implicit pet interactions (command cycling,
        // any-food feeding) so vanilla and other mods' shift-interactions can be
        // reached. Deliberate item uses - collar tags, deeds - still go through.
        boolean sneakBypass = player.isShiftKeyDown()
                && DomesticationMod.CONFIG.sneakBypassesPetInteractions.get();

        // Datapack-driven taming and transformations run BEFORE every hardcoded
        // species path so packs can override or extend them. Taming is a
        // feeding-style implicit interaction and honors the sneak bypass;
        // transformations are deliberate item uses (like collar tags) and
        // still go through while sneaking.
        if (entity instanceof Mob mob && DomesticationMod.CONFIG.dataDrivenTaming.get()) {
            if (!sneakBypass && handleDataDrivenTaming(player, mob, stack, event)) return;
            if (handleDataDrivenTransformation(player, mob, stack, event)) return;
        }

        // Gluttonous - feed pet any food to heal
        if (!sneakBypass && entity instanceof LivingEntity living && TameableUtils.isTamed(entity)
                && TameableUtils.hasEnchant(living, DIEnchantmentKeys.GLUTTONOUS)) {
            if (handleGluttonousFeeding(player, living, stack, event)) return;
        }

        // Rabbit taming
        if (entity instanceof Rabbit rabbit && DomesticationMod.CONFIG.tameableRabbit.get()) {
            if (handleRabbitInteraction(player, rabbit, stack, event, sneakBypass)) return;
        }

        // Collar tag application
        if (entity instanceof LivingEntity living && TameableUtils.isPetOf(player, entity)
                && !living.getType().is(DITagRegistry.REFUSES_COLLAR_TAGS)) {
            if (stack.is(DIItemRegistry.COLLAR_TAG.get()) && DomesticationMod.CONFIG.collarTag.get()) {
                handleCollarTagApplication(player, living, stack, event);
            }
        }

        // Trinary command cycling for generic (datapack-tamed) pets. Species
        // pets cycle inside their own mixin/handler paths; this only serves
        // mobs with neither taming API, so those paths are never doubled.
        if (!sneakBypass && DomesticationMod.CONFIG.trinaryCommandSystem.get()
                && stack.isEmpty() && event.getHand() == InteractionHand.MAIN_HAND
                && entity instanceof Mob mob
                && !(mob instanceof ModifedToBeTameable) && !(mob instanceof TamableAnimal)
                && TameableUtils.isDataTamed(mob) && TameableUtils.isPetOf(player, mob)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            if (!mob.level().isClientSide) {
                int next = (Math.max(TameableUtils.tryGetCommand(mob), 0) + 1) % 3;
                if (TameableUtils.trySetCommand(mob, next)) {
                    mob.setTarget(null);
                    mob.getNavigation().stop();
                    player.displayClientMessage(
                            Component.translatable("message.domesticationinnovation.command_" + next, mob.getName()), true);
                }
            }
        }
    }

    // =========================================================================
    // Datapack-driven taming and transformation (DIDataRegistries)
    // =========================================================================

    /**
     * Generic taming from the domesticationinnovation:taming datapack registry.
     * Returns true when a definition matched this click (whether or not the
     * chance roll succeeded); the event is then cancelled and no other
     * interaction path runs. All side effects are server-only.
     */
    private boolean handleDataDrivenTaming(Player player, Mob mob, ItemStack stack,
                                           PlayerInteractEvent.EntityInteract event) {
        if (!mob.isAlive() || TameableUtils.isTamed(mob)) return false;
        // The blacklist tag is a hard veto no datapack entry can override
        if (mob.getType().is(DIDataRegistries.TAMING_BLACKLIST)) return false;
        Registry<TamingDefinition> registry =
                mob.level().registryAccess().registry(DIDataRegistries.TAMING).orElse(null);
        if (registry == null) return false;

        TamingDefinition match = null;
        for (TamingDefinition def : registry) {
            // required_data serializes the mob, so it is checked last and only
            // for entries that already match type + item
            if (def.appliesTo(mob) && def.items().test(stack) && def.matchesRequiredData(mob)) {
                match = def;
                break;
            }
        }
        if (match == null) return false;

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (mob.level().isClientSide) return true;

        // Consume the item before the roll (matches our species taming), and
        // hand back any container generically - modded fish buckets included
        if (!player.isCreative()) {
            ItemStack remainder = stack.getCraftingRemainingItem();
            stack.shrink(1);
            if (!remainder.isEmpty()) {
                if (stack.isEmpty()) {
                    player.setItemInHand(event.getHand(), remainder);
                } else if (!player.getInventory().add(remainder)) {
                    player.drop(remainder, false);
                }
            }
        }
        mob.playSound(SoundEvents.GENERIC_EAT, 1.0F, mob.getVoicePitch());

        ServerLevel serverLevel = (ServerLevel) mob.level();
        if (mob.getRandom().nextFloat() < match.chance()) {
            tameDataDrivenPet(player, mob);
            sendTamingParticles(serverLevel, mob, ParticleTypes.HEART);
            if (player instanceof ServerPlayer serverPlayer && mob instanceof Animal animal) {
                CriteriaTriggers.TAME_ANIMAL.trigger(serverPlayer, animal);
            }
        } else {
            sendTamingParticles(serverLevel, mob, ParticleTypes.SMOKE);
        }
        return true;
    }

    /**
     * Grants ownership through the deepest API the mob actually has - vanilla
     * TamableAnimal first, then our species interface, then the generic
     * PET_DATA record - calms it down, and installs the generic pet AI.
     */
    private void tameDataDrivenPet(Player player, Mob mob) {
        if (mob instanceof TamableAnimal tamable) {
            tamable.setOwnerUUID(player.getUUID());
            tamable.setTame(true, true);
        } else if (mob instanceof ModifedToBeTameable modified) {
            modified.setTame(true);
            modified.setTameOwnerUUID(player.getUUID());
            modified.setCommand(1);
        } else {
            TameableUtils.setDataTameOwner(mob, player.getUUID());
        }
        try {
            mob.setTarget(null);
            mob.setLastHurtByMob(null);
            if (mob.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)) {
                mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
            }
        } catch (Exception ignored) {
            // A modded brain that refuses memory access must not fail the tame
        }
        mob.getNavigation().stop();
        mob.setPersistenceRequired();
        GenericPetAI.applyGenericPetAI(mob);
    }

    /**
     * Generic mob-to-mob conversion from the domesticationinnovation:transformation
     * registry. Mirrors the Rotten Apple conversion body: equipment dropped
     * before the snapshot, save-data plus PET_DATA carried to the result, name
     * and health fraction preserved, original discarded.
     */
    private boolean handleDataDrivenTransformation(Player player, Mob mob, ItemStack stack,
                                                   PlayerInteractEvent.EntityInteract event) {
        if (!mob.isAlive()) return false;
        Registry<TransformationDefinition> registry =
                mob.level().registryAccess().registry(DIDataRegistries.TRANSFORMATION).orElse(null);
        if (registry == null) return false;

        TransformationDefinition match = null;
        for (TransformationDefinition def : registry) {
            if (def.appliesTo(mob) && def.triggerItem().test(stack) && def.matchesRequiredData(mob)) {
                match = def;
                break;
            }
        }
        if (match == null) return false;

        @SuppressWarnings("unchecked")
        EntityType<? extends LivingEntity> resultType = (EntityType<? extends LivingEntity>) match.resultEntity();
        if (!net.neoforged.neoforge.event.EventHooks.canLivingConvert(mob, resultType, timer -> {})) {
            return false;
        }

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.CONSUME);
        if (mob.level().isClientSide) return true;

        Entity created = match.resultEntity().create(mob.level());
        if (!(created instanceof Mob result)) {
            if (created != null) created.discard();
            return true;
        }

        player.swing(event.getHand());
        SoundEvent sound = match.sound().map(net.minecraft.core.Holder::value).orElse(SoundEvents.ZOMBIE_INFECT);
        mob.playSound(sound, 0.8F, mob.getVoicePitch());

        // Body gear (horse armor etc) is not always readable by the result
        // type, so drop it instead of silently destroying it
        if (!mob.getItemBySlot(EquipmentSlot.BODY).isEmpty()) {
            mob.spawnAtLocation(mob.getItemBySlot(EquipmentSlot.BODY).copy());
            mob.setItemSlot(EquipmentSlot.BODY, ItemStack.EMPTY);
        }

        float healthFraction = mob.getMaxHealth() > 0 ? mob.getHealth() / mob.getMaxHealth() : 1.0F;
        CompoundTag extras = new CompoundTag();
        mob.addAdditionalSaveData(extras);
        DIAttachments.writePetDataTo(mob, extras);

        if (mob.isLeashed()) {
            result.setLeashedTo(mob.getLeashHolder(), true);
        }
        result.moveTo(mob.getX(), mob.getY(), mob.getZ(), mob.getYRot(), mob.getXRot());
        result.setNoAi(mob.isNoAi());
        result.setBaby(mob.isBaby());
        if (mob.hasCustomName()) {
            result.setCustomName(mob.getCustomName());
            result.setCustomNameVisible(mob.isCustomNameVisible());
        }
        try {
            result.readAdditionalSaveData(extras);
        } catch (Exception e) {
            // Cross-type snapshots can trip picky readers; the conversion
            // still stands, the result just starts from type defaults
            DomesticationMod.LOGGER.warn("Transformation result {} rejected copied data: {}",
                    match.resultEntity(), e.toString());
        }
        DIAttachments.readPetDataFrom(result, extras);
        result.setHealth(Math.max(1.0F, healthFraction * result.getMaxHealth()));
        result.setPersistenceRequired();

        for (int i = 0; i < 6 + mob.getRandom().nextInt(5); i++) {
            ((ServerLevel) mob.level()).sendParticles(ParticleTypes.SNEEZE,
                    mob.getRandomX(1.0F), mob.getRandomY(), mob.getRandomZ(1.0F), 1, 0, 0, 0, 0);
        }
        net.neoforged.neoforge.event.EventHooks.onLivingConvert(mob, result);
        mob.level().addFreshEntity(result);
        mob.discard();
        if (!player.isCreative()) {
            stack.shrink(1);
        }
        return true;
    }

    private static void sendTamingParticles(ServerLevel level, Mob mob, ParticleOptions particle) {
        for (int i = 0; i < 7; ++i) {
            level.sendParticles(particle,
                    mob.getRandomX(1.0D), mob.getRandomY() + 0.5D, mob.getRandomZ(1.0D),
                    1, mob.getRandom().nextGaussian() * 0.02D,
                    mob.getRandom().nextGaussian() * 0.02D,
                    mob.getRandom().nextGaussian() * 0.02D, 0.02D);
        }
    }

    private boolean handleDeedOfOwnership(Player player, Entity entity, ItemStack stack,
                                           PlayerInteractEvent.EntityInteract event) {
        boolean unbound = !DeedOfOwnershipItem.isBound(stack);
        Entity currentOwner = TameableUtils.getOwnerOf(entity);

        // Bind deed to pet
        if (currentOwner != null && currentOwner.equals(player) && unbound) {
            DeedOfOwnershipItem.bindToEntity(stack, entity.getUUID(), entity.getName().getString());
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return true;
        }

        // Transfer ownership
        if (DeedOfOwnershipItem.isBound(stack)) {
            UUID fromItem = DeedOfOwnershipItem.getBoundEntity(stack);
            if (entity.getUUID().equals(fromItem)) {
                player.getCooldowns().addCooldown(stack.getItem(), 5);
                TameableUtils.setOwnerUUIDOf(entity, player.getUUID());
                player.displayClientMessage(
                        Component.translatable("message.domesticationinnovation.set_owner", player.getName(), entity.getName()), true);
                if (currentOwner instanceof Player otherPlayer && !otherPlayer.equals(player)) {
                    otherPlayer.displayClientMessage(
                            Component.translatable("message.domesticationinnovation.set_owner", player.getName(), entity.getName()), true);
                }
                DeedOfOwnershipItem.clearBinding(stack);
                if (!player.isCreative()) stack.shrink(1);
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.SUCCESS);
                return true;
            }
        }
        return false;
    }

    private boolean handleGluttonousFeeding(Player player, LivingEntity living, ItemStack stack,
                                             PlayerInteractEvent.EntityInteract event) {
        var foodProps = stack.get(net.minecraft.core.component.DataComponents.FOOD);
        if (foodProps != null && living.getHealth() < living.getMaxHealth()) {
            living.heal((float) Math.floor(foodProps.nutrition() * 1.5F));
            if (!player.isCreative()) stack.shrink(1);
            living.playSound(living.getRandom().nextBoolean() ? SoundEvents.PLAYER_BURP : SoundEvents.GENERIC_EAT,
                    1F, living.getVoicePitch());
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return true;
        }
        return false;
    }

    private boolean handleRabbitInteraction(Player player, Rabbit rabbit, ItemStack stack,
                                            PlayerInteractEvent.EntityInteract event, boolean sneakBypass) {
        if (stack.getItem() == Items.HAY_BLOCK) {
            if (TameableUtils.isTamed(rabbit) && rabbit.getHealth() < rabbit.getMaxHealth()) {
                rabbit.heal(3);
                if (!player.isCreative()) stack.shrink(1);
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.SUCCESS);
                return true;
            }
            if (!TameableUtils.isTamed(rabbit) && !rabbit.level().isClientSide) {
                if (!player.isCreative()) stack.shrink(1);
                rabbit.playSound(SoundEvents.FOX_EAT);
                ServerLevel serverLevel = (ServerLevel) rabbit.level();
                if (rabbit.getRandom().nextBoolean()) {
                    for (int i = 0; i < 3; ++i) {
                        serverLevel.sendParticles(ParticleTypes.HEART,
                                rabbit.getRandomX(1.0D), rabbit.getRandomY() + 0.5D, rabbit.getRandomZ(1.0D),
                                3, rabbit.getRandom().nextGaussian() * 0.02D,
                                rabbit.getRandom().nextGaussian() * 0.02D,
                                rabbit.getRandom().nextGaussian() * 0.02D, 0.02F);
                    }
                    ((ModifedToBeTameable) rabbit).setTame(true);
                    ((ModifedToBeTameable) rabbit).setTameOwnerUUID(player.getUUID());
                    ((ModifedToBeTameable) rabbit).setCommand(1);
                } else {
                    for (int i = 0; i < 3; ++i) {
                        serverLevel.sendParticles(ParticleTypes.SMOKE,
                                rabbit.getRandomX(1.0D), rabbit.getRandomY() + 0.5D, rabbit.getRandomZ(1.0D),
                                3, rabbit.getRandom().nextGaussian() * 0.02D,
                                rabbit.getRandom().nextGaussian() * 0.02D,
                                rabbit.getRandom().nextGaussian() * 0.02D, 0.02F);
                    }
                }
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.SUCCESS);
                return true;
            }
        }
        // Same trinary gate as every mixin species path (WolfMixin, CatMixin,
        // etc.): without the command system, clicking must not cycle the
        // hidden command value or spam command overlay messages
        if (!sneakBypass && DomesticationMod.CONFIG.trinaryCommandSystem.get()
                && TameableUtils.isTamed(rabbit) && TameableUtils.isPetOf(player, rabbit)) {
            ((ModifedToBeTameable) rabbit).playerSetCommand(player, rabbit);
        }
        return false;
    }

    private void handleCollarTagApplication(Player player, LivingEntity living, ItemStack stack,
                                            PlayerInteractEvent.EntityInteract event) {
        if (!player.level().isClientSide && living.isAlive()) {
            // Read enchantments from the collar tag item. The pet side reads
            // the RAW stored list: curse presence must be honored and rebuilt
            // collars must keep their entries even while config-disabled.
            var itemEnchants = stack.getEnchantments();
            Map<ResourceLocation, Integer> existingEnchants = TameableUtils.getEnchantsRaw(living);

            // A collar bound by its curse cannot be swapped off the pet; refuse
            // without consuming the new tag
            if (TameableUtils.hasCollar(living) && existingEnchants != null
                    && existingEnchants.containsKey(BINDING_CURSE_ID)) {
                living.playSound(SoundEvents.VILLAGER_NO, 1.0F, living.getVoicePitch());
                if (living.level() instanceof ServerLevel serverLevel) {
                    serverLevel.sendParticles(ParticleTypes.SMOKE,
                            living.getX(), living.getY(0.5D), living.getZ(), 5, 0.3D, 0.3D, 0.3D, 0.02D);
                }
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.FAIL);
                return;
            }

            // Applying a collar identical to the pet's current one (same name,
            // same enchantments) is a no-op - don't consume the item
            if (stack.has(net.minecraft.core.component.DataComponents.CUSTOM_NAME)
                    && living.hasCustomName() && stack.getHoverName().equals(living.getCustomName())) {
                // "Identical" requires the same SET of enchants, not just that
                // every item enchant matches: a same-named collar carrying a
                // strict subset (or none at all) is a legitimate downgrade swap
                int existingCount = existingEnchants == null ? 0 : existingEnchants.size();
                boolean hasSameEnchants = existingCount == itemEnchants.size();
                if (hasSameEnchants && existingEnchants != null) {
                    for (var entry : itemEnchants.entrySet()) {
                        ResourceLocation enchantId = entry.getKey().unwrapKey()
                                .map(net.minecraft.resources.ResourceKey::location)
                                .orElse(null);
                        Integer existingLevel = enchantId == null ? null : existingEnchants.get(enchantId);
                        if (existingLevel == null || existingLevel != entry.getIntValue()) {
                            hasSameEnchants = false;
                        }
                    }
                }
                if (hasSameEnchants) {
                    event.setCanceled(true);
                    event.setCancellationResult(InteractionResult.FAIL);
                    return;
                }
            }

            // Drop the old collar if entity already has one; a vanishing curse
            // destroys it instead of returning it
            blockCollarTick(living);
            if (TameableUtils.hasCollar(living)
                    && (existingEnchants == null || !existingEnchants.containsKey(VANISHING_CURSE_ID))) {
                living.spawnAtLocation(rebuildCollarStack(living, existingEnchants));
            }

            // Apply name from collar tag
            if (stack.has(net.minecraft.core.component.DataComponents.CUSTOM_NAME)) {
                living.setCustomName(stack.getHoverName());
            }

            // Transfer enchantments from item to entity
            if (itemEnchants.isEmpty()) {
                TameableUtils.clearEnchants(living);
            } else {
                TameableUtils.clearEnchants(living);
                var registry = living.level().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
                itemEnchants.entrySet().forEach(entry -> {
                    ResourceLocation enchantId = entry.getKey().unwrapKey()
                            .map(net.minecraft.resources.ResourceKey::location)
                            .orElse(null);
                    if (enchantId != null) {
                        TameableUtils.addEnchant(living, enchantId, entry.getIntValue());
                    }
                });
            }

            if (!player.isCreative()) stack.shrink(1);
            living.playSound(DISoundRegistry.COLLAR_TAG.get(), 1, 1);
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    // =========================================================================
    // Block break - pet bed cleanup
    // =========================================================================

    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getState().getBlock() instanceof PetBedBlock
                && event.getLevel().getBlockEntity(event.getPos()) instanceof PetBedBlockEntity bedEntity) {
            bedEntity.removeAllRequestsFor(event.getPlayer());
            bedEntity.resetBedsForNearbyPets();
        }
    }

    // =========================================================================
    // Villager trades
    // =========================================================================

    @SubscribeEvent
    public void onVillagerTrades(VillagerTradesEvent event) {
        if (event.getType() != DIVillagerRegistry.ANIMAL_TAMER.get()) return;

        List<VillagerTrades.ItemListing> level1 = new ArrayList<>();
        List<VillagerTrades.ItemListing> level2 = new ArrayList<>();
        List<VillagerTrades.ItemListing> level3 = new ArrayList<>();
        List<VillagerTrades.ItemListing> level4 = new ArrayList<>();
        List<VillagerTrades.ItemListing> level5 = new ArrayList<>();

        // Level 1
        level1.add(new BuyingItemTrade(Items.TROPICAL_FISH, 10, 2, 10, 2));
        level1.add(new SellingItemTrade(Items.BONE, 3, 10, 6, 4));
        level1.add(new BuyingItemTrade(Items.HAY_BLOCK, 7, 1, 9, 1));
        level1.add(new SellingItemTrade(Items.COD, 2, 7, 6, 3));
        level1.add(new SellingItemTrade(Items.EGG, 4, 2, 9, 3));
        level1.add(new SellingItemTrade(DIItemRegistry.FEATHER_ON_A_STICK.get(), 3, 1, 2, 3));

        // Level 2
        level2.add(new SellingItemTrade(Items.TROPICAL_FISH_BUCKET, 2, 1, 6, 7));
        level2.add(new BuyingItemTrade(DIItemRegistry.COLLAR_TAG.get(), 5, 1, 12, 7));
        level2.add(new SellingItemTrade(Items.APPLE, 4, 12, 3, 7));
        level2.add(new SellingOneOfTheseItemsTrade(ImmutableSet.of(
                DIBlockRegistry.WHITE_PET_BED.get(), DIBlockRegistry.ORANGE_PET_BED.get(),
                DIBlockRegistry.MAGENTA_PET_BED.get(), DIBlockRegistry.LIGHT_BLUE_PET_BED.get(),
                DIBlockRegistry.YELLOW_PET_BED.get(), DIBlockRegistry.LIME_PET_BED.get(),
                DIBlockRegistry.PINK_PET_BED.get(), DIBlockRegistry.GRAY_PET_BED.get(),
                DIBlockRegistry.LIGHT_GRAY_PET_BED.get(), DIBlockRegistry.CYAN_PET_BED.get(),
                DIBlockRegistry.PURPLE_PET_BED.get(), DIBlockRegistry.BLUE_PET_BED.get(),
                DIBlockRegistry.BROWN_PET_BED.get(), DIBlockRegistry.GREEN_PET_BED.get(),
                DIBlockRegistry.RED_PET_BED.get(), DIBlockRegistry.BLACK_PET_BED.get()
        ), 2, 1, 6, 7));
        level2.add(new SellingItemTrade(DIItemRegistry.DEED_OF_OWNERSHIP.get(), 3, 1, 2, 7));

        // Level 3
        level3.add(new SellingItemTrade(DIItemRegistry.ROTTEN_APPLE.get(), 4, 1, 1, 10));
        level3.add(new SellingItemTrade(Items.CARROT_ON_A_STICK, 3, 1, 2, 10));
        level3.add(new SellingItemTrade(Items.LEAD, 3, 2, 5, 10));
        level3.add(new SellingItemTrade(Items.LEATHER_HORSE_ARMOR, 4, 1, 3, 11));
        level3.add(new SellingItemTrade(DIBlockRegistry.DRUM.get(), 2, 3, 7, 11));
        level3.add(new SellingItemTrade(Items.TADPOLE_BUCKET, 6, 1, 4, 13));
        level3.add(new EnchantItemTrade(DIItemRegistry.COLLAR_TAG.get(), 20, 2, 8, 3, 10));

        // Level 4
        level4.add(new SellingItemTrade(Items.IRON_HORSE_ARMOR, 8, 1, 2, 15));
        level4.add(new SellingItemTrade(Items.AXOLOTL_BUCKET, 11, 1, 2, 15));
        level4.add(new SellingItemTrade(Items.TURTLE_EGG, 26, 1, 2, 15));
        level4.add(new EnchantItemTrade(DIItemRegistry.COLLAR_TAG.get(), 40, 3, 18, 3, 15));

        // Level 5
        level5.add(new SellingItemTrade(Items.GOLDEN_HORSE_ARMOR, 13, 1, 1, 18));
        level5.add(new SellingItemTrade(Items.TURTLE_SCUTE, 21, 1, 3, 18));
        level5.add(new EnchantItemTrade(DIItemRegistry.COLLAR_TAG.get(), 50, 4, 38, 3, 20));
        level5.add(new SellingEnchantedBook(DIEnchantmentKeys.CHARISMA, 3, 12, 1, 18, 0.02F));

        // The tamer is the dedicated pet-enchant book vendor (librarians no longer roll pet books)
        if (DomesticationMod.CONFIG.tamerSellsBooks.get()) {
            level1.add(new SellingRandomPetBook(1));
            level2.add(new SellingRandomPetBook(5));
            level3.add(new SellingRandomPetBook(10));
            level4.add(new SellingRandomPetBook(15));
            level5.add(new SellingRandomPetBook(15));
        }

        event.getTrades().put(1, level1);
        event.getTrades().put(2, level2);
        event.getTrades().put(3, level3);
        event.getTrades().put(4, level4);
        event.getTrades().put(5, level5);
    }

    // =========================================================================
    // Living drops - suppress drops for pet bed respawn
    // =========================================================================

    @SubscribeEvent
    public void onLivingDrops(LivingDropsEvent event) {
        if (TameableUtils.isTamed(event.getEntity()) && TameableUtils.getPetBedPos(event.getEntity()) != null) {
            event.setCanceled(true);
        }
    }

    // =========================================================================
    // Explosion defusal
    // =========================================================================

    @SubscribeEvent
    public void onExplosion(ExplosionEvent.Start event) {
        float dist = 30;
        Vec3 center = event.getExplosion().center();
        AABB searchBox = new AABB(center.add(-dist, -dist, -dist), center.add(dist, dist, dist));
        Predicate<Entity> hasDefusal = animal -> TameableUtils.isTamed(animal)
                && TameableUtils.hasEnchant((LivingEntity) animal, DIEnchantmentKeys.DEFUSAL);

        boolean shouldDefuse = false;
        for (LivingEntity defuser : event.getLevel().getEntitiesOfClass(LivingEntity.class, searchBox,
                EntitySelector.NO_SPECTATORS.and(hasDefusal))) {
            float range = 10 * TameableUtils.getEnchantLevel(defuser, DIEnchantmentKeys.DEFUSAL);
            if (defuser.distanceToSqr(center) <= range * range) {
                shouldDefuse = true;
                break;
            }
        }

        if (shouldDefuse) {
            event.setCanceled(true);
            // FIX: Use level RNG instead of new Random()
            float pitch = 1.5F + event.getLevel().getRandom().nextFloat();
            event.getLevel().playSound(null, center.x, center.y, center.z,
                    SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 1, pitch);
            if (event.getLevel() instanceof ServerLevel serverLevel) {
                for (int i = 0; i < 5; i++) {
                    serverLevel.sendParticles(ParticleTypes.CLOUD, center.x, center.y + 1.0F, center.z, 5, 0, 0F, 0, 0.2F);
                }
            }
        }
    }

    // =========================================================================
    // Pet teleportation - follow owner through teleports and dimensions
    // =========================================================================

    private void teleportNearbyPets(Player owner, Vec3 fromPos, Vec3 toPos, Level fromLevel, Level toLevel) {
        double dist = 20;
        boolean crossDimension = fromLevel.dimension() != toLevel.dimension();
        Predicate<Entity> enchantedPet = animal -> animal instanceof Mob
                && TameableUtils.isPetOf(owner, animal) && TameableUtils.isValidTeleporter(owner, (Mob) animal);

        AABB searchBox = new AABB(fromPos.x - dist, fromPos.y - dist, fromPos.z - dist,
                fromPos.x + dist, fromPos.y + dist, fromPos.z + dist);

        for (Mob entity : fromLevel.getEntitiesOfClass(Mob.class, searchBox,
                EntitySelector.NO_SPECTATORS.and(enchantedPet))) {
            if (crossDimension) {
                teleportingPets.add(new PendingPetTeleport(entity, (ServerLevel) toLevel, owner.getUUID()));
            } else {
                Vec3 safePos = findSafePosition(entity, toLevel, toPos);
                entity.fallDistance = 0.0F;
                if (toLevel instanceof ServerLevel serverLevel) {
                    // Ticket and load the destination chunk so the pet arrives even
                    // when the owner teleported into unloaded terrain; POST_TELEPORT
                    // tickets expire on their own a few ticks later
                    ChunkPos chunkPos = new ChunkPos(BlockPos.containing(safePos));
                    serverLevel.getChunkSource().addRegionTicket(TicketType.POST_TELEPORT, chunkPos, 0, entity.getId());
                    serverLevel.getChunk(chunkPos.x, chunkPos.z);
                }
                entity.teleportTo(safePos.x, safePos.y, safePos.z);
                entity.setPortalCooldown();
            }
        }
    }

    @SubscribeEvent
    public void onEntityTeleport(EntityTeleportEvent event) {
        if (event.getEntity() instanceof Player player) {
            teleportNearbyPets(player, event.getPrev(), event.getTarget(), player.level(), player.level());
        }
    }

    @SubscribeEvent
    public void onEntityTravelToDimension(EntityTravelToDimensionEvent event) {
        if (!event.isCanceled() && event.getEntity().level() instanceof ServerLevel serverLevel
                && event.getEntity() instanceof Player player) {
            MinecraftServer server = serverLevel.getServer();
            Level toLevel = server.getLevel(event.getDimension());
            if (toLevel != null) {
                teleportNearbyPets(player, player.position(), player.position(), player.level(), toLevel);
            }
        }
    }

    // =========================================================================
    // Target change - prevent pets from targeting their owner
    // =========================================================================

    @SubscribeEvent
    public void onSetAttackTarget(LivingChangeTargetEvent event) {
        if (TameableUtils.isTamed(event.getEntity())
                && event.getNewAboutToBeSetTarget() instanceof Player player
                && TameableUtils.isPetOf(player, event.getEntity())) {
            event.setCanceled(true);
        }
    }

    // =========================================================================
    // Anvil - collar tag combining
    // =========================================================================

    @SubscribeEvent
    public void onAnvilUpdate(AnvilUpdateEvent event) {
        if (!event.getLeft().is(DIItemRegistry.COLLAR_TAG.get()) || !event.getRight().is(DIItemRegistry.COLLAR_TAG.get())) return;
        if (event.getLeft().getEnchantments().isEmpty() || event.getRight().getEnchantments().isEmpty()) return;

        // Combine enchantments from both collar tags; the first incompatible
        // pair (per exclusive-set tags) aborts all remaining merges, and each
        // conflicting pair adds a flat +1 to the cost. Iterate in registry-id
        // order so the merge/abort sequence is identical on client and server
        // (the backing enchantment map has no stable iteration order).
        ItemStack result = event.getLeft().copy();
        var rightEnchants = event.getRight().getEnchantments();
        boolean canCombine = true;
        int cost = 0;

        var sortedEntries = rightEnchants.entrySet().stream()
                .sorted(Comparator.comparing(en -> en.getKey().unwrapKey().map(k -> k.location().toString()).orElse("")))
                .toList();
        for (var entry : sortedEntries) {
            var enchantHolder = entry.getKey();
            int rightLevel = entry.getIntValue();
            int leftLevel = result.getEnchantments().getLevel(enchantHolder);
            int newLevel = leftLevel == rightLevel ? rightLevel + 1 : Math.max(rightLevel, leftLevel);

            for (var existingEntry : result.getEnchantments().entrySet()) {
                if (!existingEntry.getKey().equals(enchantHolder) && !Enchantment.areCompatible(existingEntry.getKey(), enchantHolder)) {
                    canCombine = false;
                    cost++;
                }
            }

            if (canCombine) {
                newLevel = Math.min(newLevel, enchantHolder.value().getMaxLevel());
                result.enchant(enchantHolder, newLevel);
                int rarityCost = switch (enchantHolder.value().definition().weight()) {
                    case 1 -> 8;
                    case 2 -> 4;
                    case 5 -> 2;
                    default -> 1;
                };
                cost += rarityCost * newLevel;
            }
        }

        event.setCost(cost);
        event.setOutput(result);
    }
}
