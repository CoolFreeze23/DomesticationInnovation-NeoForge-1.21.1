package com.github.alexthe668.domesticationinnovation.server.entity;
import com.github.alexthe668.domesticationinnovation.server.entity.DIAttachments;
import com.github.alexthe668.domesticationinnovation.server.entity.ModifedToBeTameable;

import com.github.alexthe668.domesticationinnovation.DomesticationMod;
import com.github.alexthe668.domesticationinnovation.server.enchantment.DIEnchantmentKeys;
import com.github.alexthe668.domesticationinnovation.server.misc.DINetworkRegistry;
import com.github.alexthe668.domesticationinnovation.server.misc.DIParticleRegistry;
import com.github.alexthe668.domesticationinnovation.server.misc.DITagRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Fox;
import net.minecraft.world.entity.animal.Rabbit;
import net.minecraft.world.entity.animal.axolotl.Axolotl;
import net.minecraft.world.entity.animal.frog.Frog;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Predicate;

/**
 * Core utility class for all pet/taming operations.
 *
 * Ported from Forge 1.20.1 to NeoForge 1.21.1:
 * - CitadelEntityData → NeoForge IAttachmentType (DIAttachments.PET_DATA)
 * - Citadel sync messages → NeoForge attachment auto-sync
 * - Enchantment direct refs → ResourceKey<Enchantment>
 * - ForgeRegistries → BuiltInRegistries
 * - UUID AttributeModifiers → ResourceLocation-based
 * - Tags.Blocks.ORES → convention c:ores tag (ATM10 compat)
 * - ModifedToBeTameable → ModifedToBeTameable
 */
public class TameableUtils {

    // NBT keys for pet data stored in the attachment
    private static final String ENCHANTMENT_TAG = "StoredPetEnchantments";
    private static final String COLLAR_TAG = "HasPetCollar";
    private static final String IMMUNITY_TIME_TAG = "PetImmunityTimer";
    private static final String FROZEN_TIME_TAG = "PetFrozenTime";
    private static final String ATTACK_TARGET_ENTITY = "PetAttackTarget";
    private static final String SHADOW_PUNCH_TIMES = "PetShadowPunchTimes";
    private static final String SHADOW_PUNCH_COOLDOWN = "PetShadowPunchCooldown";
    private static final String PSYCHIC_WALL_COOLDOWN = "PetPsychicWallCooldown";
    private static final String INTIMIDATION_COOLDOWN = "PetIntimidationCooldown";
    private static final String SHADOW_PUNCH_STRIKING = "PetShadowPunchStriking";
    private static final String JUKEBOX_FOLLOWER_UUID = "PetJukeboxFollowerUUID";
    private static final String JUKEBOX_FOLLOWER_DISC = "PetJukeboxFollowerDisc";
    private static final String BLAZING_PROTECTION_BARS = "PetBlazingProtectionBars";
    private static final String BLAZING_PROTECTION_COOLDOWN = "PetBlazingProtectionCooldown";
    private static final String HEALING_AURA_TIME = "PetHealingAuraTime";
    private static final String HEALING_AURA_IMPULSE = "PetHealingAuraImpulse";
    private static final String HAS_PET_BED = "HasPetBed";
    private static final String PET_BED_X = "PetBedX";
    private static final String PET_BED_Y = "PetBedY";
    private static final String PET_BED_Z = "PetBedZ";
    private static final String PET_BED_DIMENSION = "PetBedDimension";
    private static final String FALL_DISTANCE_SYNC = "SyncedFallDistance";
    private static final String ZOMBIE_PET = "ZombiePet";
    private static final String SAFE_PET_HEALTH = "SafePetHealth";
    private static final String COLLAR_SWAP_COOLDOWN = "CollarSwapCooldown";

    // Attribute modifier IDs - NeoForge 1.21.1 uses ResourceLocation instead of UUID
    private static final ResourceLocation HEALTH_BOOST_ID = ResourceLocation.fromNamespaceAndPath(DomesticationMod.MODID, "health_boost");
    private static final ResourceLocation SPEED_BOOST_ID = ResourceLocation.fromNamespaceAndPath(DomesticationMod.MODID, "speed_boost");
    private static final ResourceLocation SPEED_BOOST_AQUATIC_LAND_ID = ResourceLocation.fromNamespaceAndPath(DomesticationMod.MODID, "amphibious_land_speed");

    // =========================================================================
    // Entity data access - replaces CitadelEntityData
    // =========================================================================

    private static CompoundTag getPetTag(LivingEntity entity) {
        return entity.getData(DIAttachments.PET_DATA);
    }

    private static void setPetTag(LivingEntity entity, CompoundTag tag) {
        entity.setData(DIAttachments.PET_DATA, tag);
        // Sync to all tracking clients so rendering/effects work
        if (!entity.level().isClientSide) {
            DINetworkRegistry.syncPetData(entity);
        }
    }

    // =========================================================================
    // Ownership helpers
    // =========================================================================

    public static boolean hasSameOwnerAs(LivingEntity tameable, Entity target) {
        return hasSameOwnerAsOneWay(tameable, target) || hasSameOwnerAsOneWay(target, tameable);
    }

    private static boolean hasSameOwnerAsOneWay(Entity tameable, Entity target) {
        if (tameable instanceof TamableAnimal tamed && tamed.getOwner() != null) {
            if (target instanceof ModifedToBeTameable other && other.getTameOwner() != null) {
                return tamed.getOwner().equals(other.getTameOwner());
            }
            if (target instanceof TamableAnimal otherPet && otherPet.getOwner() != null) {
                return tamed.getOwner().equals(otherPet.getOwner());
            }
            return tamed.getOwner().equals(target);
        } else if (tameable instanceof ModifedToBeTameable custom && custom.getTameOwner() != null) {
            if (target instanceof ModifedToBeTameable otherCustom && otherCustom.getTameOwner() != null) {
                return custom.getTameOwner().equals(otherCustom.getTameOwner());
            }
            if (target instanceof TamableAnimal tamed && tamed.getOwner() != null) {
                return custom.getTameOwner().equals(tamed.getOwner());
            }
            return custom.getTameOwner().equals(target);
        }
        return false;
    }

    public static boolean shouldUnloadToLantern(LivingEntity tameable) {
        if (DomesticationMod.CONFIG.trinaryCommandSystem.get() && tameable instanceof ModifedToBeTameable commandable) {
            return commandable.getCommand() == 2;
        }
        int command = tryGetCommand(tameable);
        if (command != -1) {
            return command == 2;
        }
        if (tameable instanceof TamableAnimal animal) {
            return !animal.isOrderedToSit();
        }
        return false;
    }

    public static boolean isPetOf(Player player, Entity entity) {
        return entity != null && (entity.isAlliedTo(player) || hasSameOwnerAsOneWay(entity, player));
    }

    public static boolean isTamed(Entity entity) {
        if (entity instanceof Axolotl && entity instanceof ModifedToBeTameable m) {
            return m.isTame() && DomesticationMod.CONFIG.tameableAxolotl.get();
        }
        if (entity instanceof Fox && entity instanceof ModifedToBeTameable m) {
            return m.isTame() && DomesticationMod.CONFIG.tameableFox.get();
        }
        if (entity instanceof Rabbit && entity instanceof ModifedToBeTameable m) {
            return m.isTame() && DomesticationMod.CONFIG.tameableRabbit.get();
        }
        if (entity instanceof Frog && entity instanceof ModifedToBeTameable m) {
            return m.isTame() && DomesticationMod.CONFIG.tameableFrog.get();
        }
        return (entity instanceof ModifedToBeTameable m && m.isTame())
                || (entity instanceof TamableAnimal t && t.isTame());
    }

    public static boolean couldBeTamed(Entity entity) {
        return entity instanceof ModifedToBeTameable || entity instanceof TamableAnimal;
    }

    @Nullable
    public static Entity getOwnerOf(Entity entity) {
        if (entity instanceof ModifedToBeTameable m) return m.getTameOwner();
        if (entity instanceof TamableAnimal t) return t.getOwner();
        return null;
    }

    @Nullable
    public static UUID getOwnerUUIDOf(Entity entity) {
        if (entity instanceof ModifedToBeTameable m) return m.getTameOwnerUUID();
        if (entity instanceof TamableAnimal t) return t.getOwnerUUID();
        return null;
    }

    public static void setOwnerUUIDOf(Entity entity, UUID uuid) {
        if (entity instanceof ModifedToBeTameable m) m.setTameOwnerUUID(uuid);
        if (entity instanceof TamableAnimal t) t.setOwnerUUID(uuid);
    }

    // =========================================================================
    // Modded mob command compat (Ice and Fire, etc.)
    // Uses reflection to detect setCommand(int)/getCommand() on any TamableAnimal
    // so the Command Drum, Wayward Lantern, etc. work without a hard dependency.
    // =========================================================================

    private static final Object SENTINEL = new Object();
    private static final Map<Class<?>, Object> SET_COMMAND_CACHE = new HashMap<>();
    private static final Map<Class<?>, Object> GET_COMMAND_CACHE = new HashMap<>();

    /**
     * Try to call setCommand(int) on a modded tameable entity via reflection.
     * Handles ModifedToBeTameable first, then checks for a reflected method.
     * Returns true if the command was set successfully.
     */
    public static boolean trySetCommand(LivingEntity entity, int command) {
        if (entity instanceof ModifedToBeTameable m) {
            m.setCommand(command);
            return true;
        }
        Method method = lookupMethod(entity.getClass(), SET_COMMAND_CACHE, "setCommand", int.class);
        if (method != null) {
            try {
                method.invoke(entity, command);
                return true;
            } catch (Exception ignored) {}
        }
        return false;
    }

    /**
     * Try to call getCommand() on a modded tameable entity via reflection.
     * Returns the command value, or -1 if the entity has no such method.
     */
    public static int tryGetCommand(LivingEntity entity) {
        if (entity instanceof ModifedToBeTameable m) {
            return m.getCommand();
        }
        Method method = lookupMethod(entity.getClass(), GET_COMMAND_CACHE, "getCommand");
        if (method != null) {
            try {
                return (int) method.invoke(entity);
            } catch (Exception ignored) {}
        }
        return -1;
    }

    private static Method lookupMethod(Class<?> clazz, Map<Class<?>, Object> cache, String name, Class<?>... paramTypes) {
        Object cached = cache.get(clazz);
        if (cached == SENTINEL) return null;
        if (cached instanceof Method m) return m;
        try {
            Method m = clazz.getMethod(name, paramTypes);
            cache.put(clazz, m);
            return m;
        } catch (NoSuchMethodException e) {
            cache.put(clazz, SENTINEL);
            return null;
        }
    }

    // =========================================================================
    // Enchantment system - now uses ResourceKey<Enchantment> for data-driven enchants
    // =========================================================================

    /**
     * Get the enchantment level for a data-driven enchantment on this entity.
     * Reads from the StoredPetEnchantments ListTag in the pet data attachment.
     */
    public static int getEnchantLevel(LivingEntity entity, ResourceKey<Enchantment> enchantmentKey) {
        ListTag listTag = getEnchantmentList(entity);
        String enchantId = enchantmentKey.location().toString();
        if (listTag != null && DomesticationMod.CONFIG.isEnchantEnabled(enchantmentKey.location().getPath())) {
            for (int i = 0; i < listTag.size(); i++) {
                CompoundTag entry = listTag.getCompound(i);
                String id = entry.getString("id");
                if (id.equals(enchantId)) {
                    return entry.getInt("lvl");
                }
            }
        }
        return 0;
    }

    public static boolean hasEnchant(LivingEntity entity, ResourceKey<Enchantment> enchantmentKey) {
        return getEnchantLevel(entity, enchantmentKey) > 0;
    }

    @Nullable
    public static Map<ResourceLocation, Integer> getEnchants(LivingEntity entity) {
        ListTag listTag = getEnchantmentList(entity);
        if (listTag == null) return null;

        Map<ResourceLocation, Integer> enchants = new HashMap<>();
        for (int i = 0; i < listTag.size(); i++) {
            CompoundTag entry = listTag.getCompound(i);
            String id = entry.getString("id");
            ResourceLocation loc = ResourceLocation.tryParse(id);
            if (loc != null && DomesticationMod.CONFIG.isEnchantEnabled(loc.getPath())) {
                enchants.put(loc, entry.getInt("lvl"));
            }
        }
        return enchants;
    }

    public static boolean hasAnyEnchants(LivingEntity entity) {
        ListTag listTag = getEnchantmentList(entity);
        return listTag != null && !listTag.isEmpty();
    }

    @Nullable
    private static ListTag getEnchantmentList(LivingEntity entity) {
        CompoundTag tag = getPetTag(entity);
        return tag.contains(ENCHANTMENT_TAG) ? tag.getList(ENCHANTMENT_TAG, 10) : null;
    }

    /**
     * Store an enchantment on the entity's collar.
     * Uses simple {id:"mod:name", lvl:N} format matching 1.21.1 conventions.
     */
    public static void addEnchant(LivingEntity entity, ResourceLocation enchantId, int level) {
        ListTag listTag = getEnchantmentList(entity);
        if (listTag == null) listTag = new ListTag();

        String idStr = enchantId.toString();
        boolean found = false;
        for (int i = 0; i < listTag.size(); i++) {
            CompoundTag entry = listTag.getCompound(i);
            if (entry.getString("id").equals(idStr)) {
                if (entry.getInt("lvl") < level) {
                    entry.putInt("lvl", level);
                }
                found = true;
                break;
            }
        }
        if (!found) {
            CompoundTag entry = new CompoundTag();
            entry.putString("id", idStr);
            entry.putInt("lvl", level);
            listTag.add(entry);
        }
        setEnchantmentTag(entity, listTag);
    }

    public static void clearEnchants(LivingEntity entity) {
        setEnchantmentTag(entity, new ListTag());
    }

    private static void setEnchantmentTag(LivingEntity entity, ListTag enchants) {
        Map<ResourceLocation, Integer> prevEnchants = getEnchants(entity);
        CompoundTag tag = getPetTag(entity);
        tag.put(ENCHANTMENT_TAG, enchants);
        tag.putInt(COLLAR_SWAP_COOLDOWN, 20);
        tag.putBoolean(COLLAR_TAG, true);
        setPetTag(entity, tag);
        onUpdateEnchants(prevEnchants, entity);
    }

    private static void onUpdateEnchants(@Nullable Map<ResourceLocation, Integer> prevEnchants, LivingEntity entity) {
        int healthExtra = getEnchantLevel(entity, DIEnchantmentKeys.HEALTH_BOOST);
        int speedExtra = getEnchantLevel(entity, DIEnchantmentKeys.SPEEDSTER);
        boolean amphib = hasEnchant(entity, DIEnchantmentKeys.AMPHIBIOUS) && !entity.isInWaterOrBubble() && isWaterCreature(entity);

        // ATM10: Apply power multiplier from config
        double powerMult = DomesticationMod.CONFIG.enchantPowerMultiplier.get();

        AttributeInstance health = entity.getAttribute(Attributes.MAX_HEALTH);
        AttributeInstance speed = entity.getAttribute(Attributes.MOVEMENT_SPEED);

        // Immaturity curse pose change
        ResourceLocation immaturityLoc = DIEnchantmentKeys.IMMATURITY_CURSE.location();
        if (hasEnchant(entity, DIEnchantmentKeys.IMMATURITY_CURSE)
                || (prevEnchants != null && prevEnchants.containsKey(immaturityLoc))) {
            entity.setPose(Pose.FALL_FLYING);
            entity.refreshDimensions();
        }

        if (health != null) {
            if (healthExtra > 0) {
                double amount = healthExtra * 10 * powerMult;
                AttributeModifier mod = new AttributeModifier(HEALTH_BOOST_ID, amount, AttributeModifier.Operation.ADD_VALUE);
                health.removeModifier(HEALTH_BOOST_ID);
                health.addPermanentModifier(mod);
            } else {
                health.removeModifier(HEALTH_BOOST_ID);
            }
        }

        if (speed != null) {
            if (speedExtra > 0) {
                double amount = speedExtra * 0.075 * powerMult;
                AttributeModifier mod = new AttributeModifier(SPEED_BOOST_ID, amount, AttributeModifier.Operation.ADD_VALUE);
                speed.removeModifier(SPEED_BOOST_ID);
                speed.addPermanentModifier(mod);
            } else {
                speed.removeModifier(SPEED_BOOST_ID);
            }
            if (amphib) {
                AttributeModifier mod = new AttributeModifier(SPEED_BOOST_AQUATIC_LAND_ID, 0.13, AttributeModifier.Operation.ADD_VALUE);
                speed.removeModifier(SPEED_BOOST_AQUATIC_LAND_ID);
                speed.addPermanentModifier(mod);
            } else {
                speed.removeModifier(SPEED_BOOST_AQUATIC_LAND_ID);
            }
        }
    }

    private static boolean isWaterCreature(LivingEntity entity) {
        MobCategory cat = entity.getType().getCategory();
        return cat == MobCategory.WATER_CREATURE || cat == MobCategory.UNDERGROUND_WATER_CREATURE || cat == MobCategory.WATER_AMBIENT;
    }

    public static List<Component> getEnchantDescriptions(LivingEntity entity) {
        List<Component> list = new ArrayList<>();
        list.add(Component.literal("   ").append(
                Component.translatable("message.domesticationinnovation.enchantments").withStyle(ChatFormatting.GOLD)));
        Map<ResourceLocation, Integer> map = getEnchants(entity);
        if (map != null) {
            for (Map.Entry<ResourceLocation, Integer> entry : map.entrySet()) {
                if (DomesticationMod.CONFIG.isEnchantEnabled(entry.getKey().getPath())) {
                    boolean isCurse = entry.getKey().getPath().contains("curse");
                    list.add(Component.translatable("enchantment." + entry.getKey().getNamespace() + "." + entry.getKey().getPath())
                            .append(Component.literal(" "))
                            .append(Component.translatable("enchantment.level." + entry.getValue()))
                            .withStyle(isCurse ? ChatFormatting.RED : ChatFormatting.AQUA));
                }
            }
        }
        return list;
    }

    // =========================================================================
    // Collar state
    // =========================================================================

    public static void setHasCollar(LivingEntity entity, boolean collar) {
        CompoundTag tag = getPetTag(entity);
        tag.putBoolean(COLLAR_TAG, collar);
        setPetTag(entity, tag);
    }

    public static boolean hasCollar(LivingEntity entity) {
        CompoundTag tag = getPetTag(entity);
        return tag.contains(COLLAR_TAG) && tag.getBoolean(COLLAR_TAG);
    }

    // =========================================================================
    // Timer/state getters and setters
    // =========================================================================

    public static int getImmuneTime(LivingEntity e) {
        return hasEnchant(e, DIEnchantmentKeys.IMMUNITY_FRAME) ? getPetTag(e).getInt(IMMUNITY_TIME_TAG) : 0;
    }

    public static void setImmuneTime(LivingEntity e, int time) {
        if (hasEnchant(e, DIEnchantmentKeys.IMMUNITY_FRAME)) {
            CompoundTag tag = getPetTag(e);
            tag.putInt(IMMUNITY_TIME_TAG, time);
            setPetTag(e, tag);
        }
    }

    public static int getFrozenTime(LivingEntity e) { return getPetTag(e).getInt(FROZEN_TIME_TAG); }
    public static void setFrozenTimeTag(LivingEntity e, int time) {
        CompoundTag tag = getPetTag(e); tag.putInt(FROZEN_TIME_TAG, time); setPetTag(e, tag);
    }

    public static int getPetAttackTargetID(LivingEntity e) {
        CompoundTag tag = getPetTag(e);
        return tag.contains(ATTACK_TARGET_ENTITY) ? tag.getInt(ATTACK_TARGET_ENTITY) : -1;
    }
    @Nullable public static Entity getPetAttackTarget(LivingEntity e) {
        int id = getPetAttackTargetID(e); return id == -1 ? null : e.level().getEntity(id);
    }
    public static void setPetAttackTarget(LivingEntity e, int id) {
        CompoundTag tag = getPetTag(e); tag.putInt(ATTACK_TARGET_ENTITY, id); setPetTag(e, tag);
    }

    public static int getShadowPunchCooldown(LivingEntity e) { return getPetTag(e).getInt(SHADOW_PUNCH_COOLDOWN); }
    public static void setShadowPunchCooldown(LivingEntity e, int t) {
        CompoundTag tag = getPetTag(e); tag.putInt(SHADOW_PUNCH_COOLDOWN, t); setPetTag(e, tag);
    }
    public static int[] getShadowPunchTimes(LivingEntity e) { return getPetTag(e).getIntArray(SHADOW_PUNCH_TIMES); }
    public static void setShadowPunchTimes(LivingEntity e, int[] t) {
        CompoundTag tag = getPetTag(e); tag.putIntArray(SHADOW_PUNCH_TIMES, t); setPetTag(e, tag);
    }
    public static int[] getShadowPunchStriking(LivingEntity e) { return getPetTag(e).getIntArray(SHADOW_PUNCH_STRIKING); }
    public static void setShadowPunchStriking(LivingEntity e, int[] t) {
        CompoundTag tag = getPetTag(e); tag.putIntArray(SHADOW_PUNCH_STRIKING, t); setPetTag(e, tag);
    }

    public static void setPetJukeboxUUID(LivingEntity e, UUID id) {
        CompoundTag tag = getPetTag(e); tag.putUUID(JUKEBOX_FOLLOWER_UUID, id); setPetTag(e, tag);
    }
    @Nullable public static UUID getPetJukeboxUUID(LivingEntity e) {
        CompoundTag tag = getPetTag(e); return tag.contains(JUKEBOX_FOLLOWER_UUID) ? tag.getUUID(JUKEBOX_FOLLOWER_UUID) : null;
    }
    public static void setPetJukeboxDisc(LivingEntity e, ItemStack stack) {
        CompoundTag tag = getPetTag(e); tag.put(JUKEBOX_FOLLOWER_DISC, stack.save(e.registryAccess())); setPetTag(e, tag);
    }
    public static ItemStack getPetJukeboxDisc(LivingEntity e) {
        CompoundTag tag = getPetTag(e);
        return tag.contains(JUKEBOX_FOLLOWER_DISC)
                ? ItemStack.parseOptional(e.registryAccess(), tag.getCompound(JUKEBOX_FOLLOWER_DISC))
                : ItemStack.EMPTY;
    }

    public static int getPsychicWallCooldown(LivingEntity e) { return getPetTag(e).getInt(PSYCHIC_WALL_COOLDOWN); }
    public static void setPsychicWallCooldown(LivingEntity e, int t) {
        CompoundTag tag = getPetTag(e); tag.putInt(PSYCHIC_WALL_COOLDOWN, t); setPetTag(e, tag);
    }
    public static int getIntimidationCooldown(LivingEntity e) { return getPetTag(e).getInt(INTIMIDATION_COOLDOWN); }
    public static void setIntimidationCooldown(LivingEntity e, int t) {
        CompoundTag tag = getPetTag(e); tag.putInt(INTIMIDATION_COOLDOWN, t); setPetTag(e, tag);
    }
    public static int getBlazingProtectionCooldown(LivingEntity e) { return getPetTag(e).getInt(BLAZING_PROTECTION_COOLDOWN); }
    public static void setBlazingProtectionCooldown(LivingEntity e, int t) {
        CompoundTag tag = getPetTag(e); tag.putInt(BLAZING_PROTECTION_COOLDOWN, t); setPetTag(e, tag);
    }
    public static int getBlazingProtectionBars(LivingEntity e) { return getPetTag(e).getInt(BLAZING_PROTECTION_BARS); }
    public static void setBlazingProtectionBars(LivingEntity e, int t) {
        CompoundTag tag = getPetTag(e); tag.putInt(BLAZING_PROTECTION_BARS, t); setPetTag(e, tag);
    }
    public static int getHealingAuraTime(LivingEntity e) { return getPetTag(e).getInt(HEALING_AURA_TIME); }
    public static void setHealingAuraTime(LivingEntity e, int t) {
        CompoundTag tag = getPetTag(e); tag.putInt(HEALING_AURA_TIME, t); setPetTag(e, tag);
    }
    public static boolean getHealingAuraImpulse(LivingEntity e) { return getPetTag(e).getBoolean(HEALING_AURA_IMPULSE); }
    public static void setHealingAuraImpulse(LivingEntity e, boolean b) {
        CompoundTag tag = getPetTag(e); tag.putBoolean(HEALING_AURA_IMPULSE, b); setPetTag(e, tag);
    }

    // =========================================================================
    // Pet bed
    // =========================================================================

    @Nullable
    public static BlockPos getPetBedPos(LivingEntity e) {
        CompoundTag tag = getPetTag(e);
        if (tag.getBoolean(HAS_PET_BED) && tag.contains(PET_BED_X) && tag.contains(PET_BED_Y) && tag.contains(PET_BED_Z)) {
            return new BlockPos(tag.getInt(PET_BED_X), tag.getInt(PET_BED_Y), tag.getInt(PET_BED_Z));
        }
        return null;
    }

    public static void setPetBedPos(LivingEntity e, BlockPos pos) {
        CompoundTag tag = getPetTag(e);
        tag.putBoolean(HAS_PET_BED, true);
        tag.putInt(PET_BED_X, pos.getX());
        tag.putInt(PET_BED_Y, pos.getY());
        tag.putInt(PET_BED_Z, pos.getZ());
        setPetTag(e, tag);
    }

    public static void removePetBedPos(LivingEntity e) {
        CompoundTag tag = getPetTag(e); tag.putBoolean(HAS_PET_BED, false); setPetTag(e, tag);
    }

    public static String getPetBedDimension(LivingEntity e) {
        CompoundTag tag = getPetTag(e);
        return tag.contains(PET_BED_DIMENSION) ? tag.getString(PET_BED_DIMENSION) : "minecraft:overworld";
    }

    public static void setPetBedDimension(LivingEntity e, String dim) {
        CompoundTag tag = getPetTag(e); tag.putString(PET_BED_DIMENSION, dim); setPetTag(e, tag);
    }

    // =========================================================================
    // Misc state
    // =========================================================================

    public static double getSafePetHealth(LivingEntity e) { return getPetTag(e).getDouble(SAFE_PET_HEALTH); }
    public static void setSafePetHealth(LivingEntity e, double h) {
        CompoundTag tag = getPetTag(e); tag.putDouble(SAFE_PET_HEALTH, h); setPetTag(e, tag);
    }
    public static float getFallDistance(LivingEntity e) { return getPetTag(e).getFloat(FALL_DISTANCE_SYNC); }
    public static void setFallDistance(LivingEntity e, float d) {
        CompoundTag tag = getPetTag(e); tag.putFloat(FALL_DISTANCE_SYNC, d); setPetTag(e, tag);
    }
    public static boolean isZombiePet(LivingEntity e) { return getPetTag(e).getBoolean(ZOMBIE_PET); }
    public static void setZombiePet(LivingEntity e, boolean z) {
        CompoundTag tag = getPetTag(e); tag.putBoolean(ZOMBIE_PET, z); setPetTag(e, tag);
    }

    // =========================================================================
    // Charisma & teleport validation
    // =========================================================================

    public static int getCharismaBonusForOwner(Player player) {
        Predicate<Entity> pet = animal -> isTamed(animal) && isPetOf(player, animal);
        List<LivingEntity> list = player.level().getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(25, 8, 25), EntitySelector.NO_SPECTATORS.and(pet));
        int total = 0;
        for (LivingEntity e : list) {
            total += 10 * getEnchantLevel(e, DIEnchantmentKeys.CHARISMA);
        }
        return Math.min(total, 50);
    }

    public static boolean isValidTeleporter(LivingEntity owner, Mob animal) {
        if (!hasEnchant(animal, DIEnchantmentKeys.TETHERED_TELEPORT)) return false;

        if (animal instanceof ModifedToBeTameable commandable) {
            return commandable.getCommand() == 2;
        } else if (animal instanceof TamableAnimal tame) {
            return !tame.isOrderedToSit() && animal.distanceTo(owner) < 10;
        }
        return false;
    }

    // =========================================================================
    // Enchantment behavior helpers
    // =========================================================================

    public static void attractAnimals(LivingEntity attractor, int max) {
        if ((attractor.tickCount + attractor.getId()) % 8 != 0) return;
        Predicate<Entity> notOnTeam = a -> !hasSameOwnerAs((LivingEntity) a, attractor)
                && a.distanceTo(attractor) > 3 + attractor.getBbWidth() * 1.6F;
        List<Animal> list = attractor.level().getEntitiesOfClass(Animal.class,
                attractor.getBoundingBox().inflate(16, 8, 16), EntitySelector.NO_SPECTATORS.and(notOnTeam));
        list.sort(Comparator.comparingDouble(attractor::distanceToSqr));
        for (int i = 0; i < Math.min(max, list.size()); i++) {
            Animal e = list.get(i);
            e.setTarget(null);
            e.setLastHurtByMob(null);
            e.getNavigation().moveTo(attractor, 1.1D);
        }
    }

    public static void aggroRandomMonsters(LivingEntity attractor) {
        if ((attractor.tickCount + attractor.getId()) % 400 != 0) return;
        Predicate<Entity> notOnTeamMonster = a -> a instanceof Monster
                && !hasSameOwnerAs((LivingEntity) a, attractor) && a.distanceTo(attractor) > 3 + attractor.getBbWidth() * 1.6F;
        List<Mob> list = attractor.level().getEntitiesOfClass(Mob.class,
                attractor.getBoundingBox().inflate(20, 8, 20), EntitySelector.NO_SPECTATORS.and(notOnTeamMonster));
        list.sort(Comparator.comparingDouble(attractor::distanceToSqr));
        if (!list.isEmpty()) list.get(0).setTarget(attractor);
    }

    public static void scareRandomMonsters(LivingEntity scary, int level) {
        boolean interval = (scary.tickCount + scary.getId()) % Math.max(140, 600 - level * 200) == 0;
        if (!interval && scary.hurtTime != 4 && getIntimidationCooldown(scary) <= 0) return;

        Predicate<Entity> notOnTeamMonster = a -> a instanceof Monster
                && !hasSameOwnerAs((LivingEntity) a, scary) && a.distanceTo(scary) > 3 + scary.getBbWidth() * 1.6F;
        List<PathfinderMob> list = scary.level().getEntitiesOfClass(PathfinderMob.class,
                scary.getBoundingBox().inflate(10 * level, 8 * level, 10 * level), EntitySelector.NO_SPECTATORS.and(notOnTeamMonster));
        list.sort(Comparator.comparingDouble(scary::distanceToSqr));
        if (list.isEmpty()) return;

        if (getIntimidationCooldown(scary) > 0 && !interval) {
            setIntimidationCooldown(scary, getIntimidationCooldown(scary) - 1);
        } else {
            Vec3 rots = list.get(0).getEyePosition().subtract(scary.getEyePosition()).normalize();
            float f = Mth.sqrt((float) (rots.x * rots.x + rots.z * rots.z));
            double yRot = Math.atan2(-rots.z, -rots.x) * (180F / (float) Math.PI) + 90F;
            double xRot = Math.atan2(-rots.y, f) * (180F / (float) Math.PI);
            scary.level().addParticle(DIParticleRegistry.INTIMIDATION.get(),
                    scary.getX(), scary.getY(), scary.getZ(), scary.getId(), xRot, yRot);
            setIntimidationCooldown(scary, 70 * level);
            if (scary instanceof Mob mob) mob.playAmbientSound();
        }
        for (PathfinderMob monster : list) {
            Vec3 vec = LandRandomPos.getPosAway(monster, 11 * level, 7, scary.position());
            if (vec != null) monster.getNavigation().moveTo(vec.x, vec.y, vec.z, 1.5D);
        }
    }

    /**
     * Ore scenting - ATM10 compatible via c:ores convention tag.
     */
    public static void detectRandomOres(LivingEntity attractor, int interval, int range, int effectLength, int maxOres) {
        int tick = (attractor.tickCount + attractor.getId()) % interval;
        if (tick <= 30) {
            attractor.xRotO = attractor.getXRot();
            attractor.setXRot((float) Math.sin(tick * 0.6F) * 30F);
            Vec3 look = attractor.getEyePosition().add(attractor.getViewVector(1.0F).scale(attractor.getBbWidth()));
            for (int i = 0; i < 3; i++) {
                attractor.level().addParticle(DIParticleRegistry.SNIFF.get(),
                        attractor.getRandomX(2.0F), attractor.position().y, attractor.getRandomZ(2.0F),
                        look.x, look.y, look.z);
            }
        }
        if (tick == 30) {
            List<BlockPos> ores = new ArrayList<>();
            BlockPos center = attractor.blockPosition();
            int half = range / 2;
            for (int i = 0; i <= half && i >= -half; i = (i <= 0 ? 1 : 0) - i) {
                for (int j = 0; j <= range && j >= -range; j = (j <= 0 ? 1 : 0) - j) {
                    for (int k = 0; k <= range && k >= -range; k = (k <= 0 ? 1 : 0) - k) {
                        BlockPos offset = center.offset(j, i, k);
                        BlockState state = attractor.level().getBlockState(offset);
                        // ATM10: Use convention c:ores tag if enabled in config, otherwise vanilla ores tag
                        boolean isOre = DomesticationMod.CONFIG.useConventionOreTags.get()
                                ? state.is(DITagRegistry.CONVENTION_ORES)
                                : state.is(BlockTags.GOLD_ORES) || state.is(BlockTags.IRON_ORES)
                                || state.is(BlockTags.DIAMOND_ORES) || state.is(BlockTags.EMERALD_ORES)
                                || state.is(BlockTags.COPPER_ORES) || state.is(BlockTags.COAL_ORES)
                                || state.is(BlockTags.LAPIS_ORES) || state.is(BlockTags.REDSTONE_ORES);
                        if (isOre) {
                            if (ores.size() < maxOres) ores.add(offset);
                            else break;
                        }
                    }
                }
            }
            for (BlockPos ore : ores) {
                HighlightedBlockEntity highlight = DIEntityRegistry.HIGHLIGHTED_BLOCK.get().create(attractor.level());
                highlight.setPos(Vec3.atBottomCenterOf(ore));
                highlight.setLifespan(effectLength);
                highlight.setXRot(0);
                highlight.setYRot(0);
                attractor.level().addFreshEntity(highlight);
            }
        }
    }

    public static void destroyRandomPlants(LivingEntity living) {
        if ((living.tickCount + living.getId()) % 200 != 0) return;
        int range = 2;
        BlockPos center = living.blockPosition();
        int half = range / 2;
        RandomSource r = living.getRandom();
        for (int i = 0; i <= half && i >= -half; i = (i <= 0 ? 1 : 0) - i) {
            for (int j = 0; j <= range && j >= -range; j = (j <= 0 ? 1 : 0) - j) {
                for (int k = 0; k <= range && k >= -range; k = (k <= 0 ? 1 : 0) - k) {
                    BlockPos offset = center.offset(j, i, k);
                    BlockState state = living.level().getBlockState(offset);
                    if (!state.isAir() && r.nextInt(4) == 0) {
                        if (state.is(BlockTags.FLOWERS) || state.is(BlockTags.REPLACEABLE_BY_TREES) || state.is(BlockTags.CROPS)) {
                            living.level().setBlockAndUpdate(offset, Blocks.AIR.defaultBlockState());
                            for (int p = 0; p < 1 + r.nextInt(2); p++) {
                                living.level().addParticle(DIParticleRegistry.BLIGHT.get(),
                                        offset.getX() + r.nextFloat(), offset.getY() + r.nextFloat(), offset.getZ() + r.nextFloat(), 0, 0.08F, 0);
                            }
                        } else if ((state.is(BlockTags.DIRT) && !state.is(Blocks.DIRT) && !state.is(Blocks.COARSE_DIRT)) || state.is(Blocks.FARMLAND)) {
                            living.level().setBlockAndUpdate(offset, r.nextBoolean() ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.DIRT.defaultBlockState());
                            for (int p = 0; p < 1 + r.nextInt(2); p++) {
                                living.level().addParticle(DIParticleRegistry.BLIGHT.get(),
                                        offset.getX() + r.nextFloat(), offset.getY() + 1, offset.getZ() + r.nextFloat(), 0, 0.08F, 0);
                            }
                        }
                    }
                }
            }
        }
    }

    public static List<LivingEntity> getAuraHealables(LivingEntity pet) {
        Predicate<Entity> hurtAndOnTeam = a -> hasSameOwnerAs((LivingEntity) a, pet)
                && a.distanceTo(pet) < 4 && ((LivingEntity) a).getHealth() < ((LivingEntity) a).getMaxHealth();
        return pet.level().getEntitiesOfClass(LivingEntity.class,
                pet.getBoundingBox().inflate(4, 4, 4), EntitySelector.NO_SPECTATORS.and(hurtAndOnTeam));
    }

    public static List<LivingEntity> getNearbyHealers(LivingEntity hurtOwner) {
        Predicate<Entity> healer = a -> hasSameOwnerAs((LivingEntity) a, hurtOwner)
                && hasEnchant((LivingEntity) a, DIEnchantmentKeys.HEALING_AURA) && getHealingAuraTime((LivingEntity) a) == 0;
        return hurtOwner.level().getEntitiesOfClass(LivingEntity.class,
                hurtOwner.getBoundingBox().inflate(16, 4, 16), EntitySelector.NO_SPECTATORS.and(healer));
    }

    public static void absorbExpOrbs(LivingEntity living) {
        if (living.getHealth() >= living.getMaxHealth() || living.level().isClientSide) return;
        for (ExperienceOrb orb : living.level().getEntitiesOfClass(ExperienceOrb.class, living.getBoundingBox().inflate(3D))) {
            if (living.getHealth() >= living.getMaxHealth()) break;
            Vec3 vec = new Vec3(living.getX() - orb.getX(), living.getY() + (double) living.getEyeHeight() / 2.0D - orb.getY(), living.getZ() - orb.getZ());
            double distSq = vec.lengthSqr();
            if (distSq < 2.0D) {
                float newHealth = living.getHealth() + orb.value;
                living.setHealth(newHealth);
                if (newHealth - living.getMaxHealth() > 0) {
                    orb.value = (int) Math.floor(newHealth - living.getMaxHealth());
                    break;
                } else {
                    orb.discard();
                }
            }
            if (distSq < 64.0D) {
                double pull = 1.0D - Math.sqrt(distSq) / 8.0D;
                orb.setDeltaMovement(orb.getDeltaMovement().add(vec.normalize().scale(pull * pull * 0.5D)));
            }
        }
    }
}
