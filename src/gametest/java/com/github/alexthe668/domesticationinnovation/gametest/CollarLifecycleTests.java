package com.github.alexthe668.domesticationinnovation.gametest;

import com.github.alexthe668.domesticationinnovation.DomesticationMod;
import com.github.alexthe668.domesticationinnovation.server.block.DIBlockRegistry;
import com.github.alexthe668.domesticationinnovation.server.block.PetBedBlockEntity;
import com.github.alexthe668.domesticationinnovation.server.enchantment.DIEnchantmentKeys;
import com.github.alexthe668.domesticationinnovation.server.entity.TameableUtils;
import com.github.alexthe668.domesticationinnovation.server.item.DIItemRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

/**
 * Collar + pet-bed lifecycle: the CommonProxy death paths (collar tag drop,
 * vanishing-curse destruction, undead-curse zombie resurrection) and the
 * PetBedBlockEntity claim/respawn machinery.
 *
 * Determinism notes (verified against the main-source code these tests link
 * against):
 * <ul>
 *   <li>All gating configs default true (collar_drops_on_death,
 *       pet_bed_respawns, exclusive_pet_beds, per-enchant enables), asserted
 *       as preconditions rather than mutated.</li>
 *   <li>Enchants are injected through the mod's own attachment API
 *       ({@link TameableUtils#addEnchant}), which also sets the collar flag -
 *       no raw NBT, no RNG.</li>
 *   <li>Bed claiming via walking is deterministic when the pet has NO bed yet:
 *       PetBedBlock.entityInside claims on the (tickCount+id)%10==0 window
 *       whenever seeksBed() is true (exclusive beds on + no stored bed),
 *       skipping the random 1-in-6 re-bind roll entirely.</li>
 *   <li>Bed respawns process only when level.dayTime() % 24000 == 1 in the
 *       bed's block-entity tick. The respawn test freezes the daylight rule
 *       and pins dayTime to a %24000==1 value, so the gate is hit on the
 *       bed's next tick regardless of tick ordering - then restores both.
 *       That (and the global difficulty poke in the zombie test) is why the
 *       clock/zombie tests run in their own sequential batches instead of
 *       concurrently with the rest.</li>
 * </ul>
 */
@GameTestHolder(DomesticationMod.MODID)
@PrefixGameTestTemplate(false)
public class CollarLifecycleTests {

    /** Platform floor is the template's y=0 stone layer; entities stand at y=1. */
    private static final BlockPos BED_REL = new BlockPos(4, 1, 4);

    private static Wolf tamedWolf(GameTestHelper helper, BlockPos rel) {
        Wolf wolf = helper.spawnWithNoFreeWill(EntityType.WOLF, rel);
        Player owner = helper.makeMockPlayer(GameType.SURVIVAL);
        wolf.tame(owner);
        helper.assertTrue(TameableUtils.isTamed(wolf), "wolf did not read as tamed after tame()");
        return wolf;
    }

    private static void kill(Wolf wolf) {
        wolf.hurt(wolf.damageSources().genericKill(), 1000.0F);
    }

    private static List<ItemEntity> collarDrops(GameTestHelper helper) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds(),
                e -> e.getItem().is(DIItemRegistry.COLLAR_TAG.get()));
    }

    private static Holder<Enchantment> enchantHolder(GameTestHelper helper,
                                                     net.minecraft.resources.ResourceKey<Enchantment> key) {
        return helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
    }

    /**
     * Bedless death drops the collar tag WITH its enchantments. The death
     * handler rebuilds the item via rebuildCollarStack -> ItemStack.enchant,
     * which writes the minecraft:enchantments component (DataComponents
     * .ENCHANTMENTS - not stored_enchantments), so that is the component
     * asserted here, at the exact levels that were on the pet.
     */
    @GameTest(template = "platform", timeoutTicks = 100)
    public void collar_drops_on_bedless_death(GameTestHelper helper) {
        helper.assertTrue(DomesticationMod.CONFIG.collarDropsOnDeath.get(),
                "precondition: collar_drops_on_death config must default true");
        Wolf wolf = tamedWolf(helper, new BlockPos(4, 2, 4));
        TameableUtils.addEnchant(wolf, DIEnchantmentKeys.SPEEDSTER.location(), 1);
        TameableUtils.addEnchant(wolf, DIEnchantmentKeys.FROST_FANG.location(), 2);
        helper.assertTrue(TameableUtils.hasCollar(wolf), "addEnchant must set the collar flag");
        helper.assertTrue(TameableUtils.getPetBedPos(wolf) == null,
                "precondition: wolf must be bedless so death takes the collar-drop path");
        kill(wolf);
        helper.succeedWhen(() -> {
            helper.assertFalse(wolf.isAlive(), "wolf survived a 1000-damage generic kill");
            List<ItemEntity> drops = collarDrops(helper);
            helper.assertTrue(drops.size() == 1,
                    "expected exactly 1 collar tag drop, found " + drops.size());
            ItemStack stack = drops.get(0).getItem();
            ItemEnchantments enchants = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
            int speedster = enchants.getLevel(enchantHolder(helper, DIEnchantmentKeys.SPEEDSTER));
            int frostFang = enchants.getLevel(enchantHolder(helper, DIEnchantmentKeys.FROST_FANG));
            helper.assertTrue(speedster == 1,
                    "dropped collar lost speedster (level " + speedster + ", wanted 1)");
            helper.assertTrue(frostFang == 2,
                    "dropped collar lost frost_fang (level " + frostFang + ", wanted 2)");
            drops.get(0).discard();
        });
    }

    /**
     * The vanishing curse destroys the collar instead of dropping it. The
     * death handler checks the RAW stored enchant map for the VANILLA
     * minecraft:vanishing_curse id before building any drop, so the item
     * must simply never appear. A second real enchant rides along to prove
     * the curse suppresses the whole stack, not just itself. Hard-checked
     * once at t=30 (well past the synchronous death-tick drop) rather than
     * polled, so the absence assertion cannot pass vacuously early.
     */
    @GameTest(template = "platform", timeoutTicks = 100)
    public void vanishing_curse_destroys_collar(GameTestHelper helper) {
        helper.assertTrue(DomesticationMod.CONFIG.collarDropsOnDeath.get(),
                "precondition: collar_drops_on_death config must default true");
        Wolf wolf = tamedWolf(helper, new BlockPos(4, 2, 4));
        TameableUtils.addEnchant(wolf, DIEnchantmentKeys.SPEEDSTER.location(), 1);
        TameableUtils.addEnchant(wolf, ResourceLocation.withDefaultNamespace("vanishing_curse"), 1);
        helper.assertTrue(TameableUtils.hasCollar(wolf), "addEnchant must set the collar flag");
        helper.assertTrue(TameableUtils.getPetBedPos(wolf) == null,
                "precondition: wolf must be bedless (same path the drop test proves live)");
        kill(wolf);
        helper.runAfterDelay(30, () -> {
            helper.assertFalse(wolf.isAlive(), "wolf survived a 1000-damage generic kill");
            List<ItemEntity> drops = collarDrops(helper);
            helper.assertTrue(drops.isEmpty(),
                    "vanishing_curse collar must be destroyed, but " + drops.size() + " collar drop(s) appeared");
            helper.succeed();
        });
    }

    /**
     * Bed claim: a tamed, bedless wolf dropped onto a pet bed claims it via
     * PetBedBlock.entityInside -> PetBedBlockEntity.tryClaim. seeksBed() is
     * deterministic for a bedless pet under the default exclusive_pet_beds
     * config, so the claim lands within one (tickCount+id)%10 window - both
     * the bed's claim UUID and the wolf's stored bed link must agree.
     */
    @GameTest(template = "platform", timeoutTicks = 200)
    public void wolf_claims_pet_bed(GameTestHelper helper) {
        helper.assertTrue(DomesticationMod.CONFIG.petBedRespawns.get()
                        && DomesticationMod.CONFIG.exclusivePetBeds.get(),
                "precondition: pet_bed_respawns + exclusive_pet_beds must default true");
        helper.setBlock(BED_REL, DIBlockRegistry.WHITE_PET_BED.get());
        Wolf wolf = tamedWolf(helper, BED_REL.above(2));
        BlockPos absBed = helper.absolutePos(BED_REL);
        helper.succeedWhen(() -> {
            helper.assertTrue(
                    helper.getLevel().getBlockEntity(absBed) instanceof PetBedBlockEntity bed
                            && wolf.getUUID().equals(bed.getClaimedPet()),
                    "bed claim UUID does not match the wolf standing on it");
            helper.assertTrue(absBed.equals(TameableUtils.getPetBedPos(wolf)),
                    "wolf's stored bed link does not point at the claimed bed");
            wolf.discard();
        });
    }

    /**
     * Bed respawn carries the collar: a claimed, enchanted wolf dies, its
     * RespawnRequest is queued in DIWorldData, and the bed block entity
     * rebuilds the pet - enchants intact, claim moved to the fresh UUID,
     * and NO collar item dropped (the death handler skips the drop when a
     * respawn was queued).
     *
     * "Next morning" gate: PetBedBlockEntity.tick only processes requests
     * when level.dayTime() % 24000 == 1. Freezing RULE_DAYLIGHT and pinning
     * dayTime to 240001 makes the bed hit that gate on its very next tick,
     * independent of the advance-vs-blockentity-vs-gametest tick ordering;
     * both are restored on success. Own batch: the pinned clock is global.
     */
    @GameTest(template = "platform", timeoutTicks = 300, batch = "collarLifecycleClock")
    public void bed_respawn_carries_collar_enchants(GameTestHelper helper) {
        helper.assertTrue(DomesticationMod.CONFIG.petBedRespawns.get(),
                "precondition: pet_bed_respawns config must default true");
        ServerLevel level = helper.getLevel();
        helper.setBlock(BED_REL, DIBlockRegistry.WHITE_PET_BED.get());
        BlockPos absBed = helper.absolutePos(BED_REL);

        Wolf wolf = tamedWolf(helper, new BlockPos(2, 2, 2));
        TameableUtils.addEnchant(wolf, DIEnchantmentKeys.SPEEDSTER.location(), 1);
        TameableUtils.addEnchant(wolf, DIEnchantmentKeys.FROST_FANG.location(), 2);
        // Deterministic claim through the mod's own API - the exact calls
        // PetBedBlock.entityInside makes on a successful walk-on claim
        helper.assertTrue(PetBedBlockEntity.tryClaim(level, absBed, wolf), "initial bed claim refused");
        TameableUtils.setPetBedPos(wolf, absBed);
        TameableUtils.setPetBedDimension(wolf, level.dimension().toString());

        final UUID deadWolfId = wolf.getUUID();
        final long dayTimeBefore = level.dayTime();
        final boolean daylightBefore = level.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT);
        kill(wolf);
        helper.runAfterDelay(5, () -> {
            helper.assertFalse(wolf.isAlive(), "wolf survived a 1000-damage generic kill");
            level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, level.getServer());
            level.setDayTime(240001L); // % 24000 == 1 -> the bed's "next morning" gate
        });
        helper.succeedWhen(() -> {
            List<Wolf> respawned = level.getEntitiesOfClass(Wolf.class, helper.getBounds(),
                    w -> w.isAlive() && !w.getUUID().equals(deadWolfId));
            helper.assertTrue(respawned.size() == 1,
                    "expected exactly 1 respawned wolf, found " + respawned.size());
            Wolf fresh = respawned.get(0);
            helper.assertTrue(fresh.distanceToSqr(absBed.getX() + 0.5, absBed.getY() + 0.5, absBed.getZ() + 0.5) < 9.0D,
                    "respawned wolf did not appear at its bed");
            int speedster = TameableUtils.getEnchantLevel(fresh, DIEnchantmentKeys.SPEEDSTER);
            int frostFang = TameableUtils.getEnchantLevel(fresh, DIEnchantmentKeys.FROST_FANG);
            helper.assertTrue(speedster == 1 && frostFang == 2,
                    "respawned wolf lost its collar enchants (speedster=" + speedster
                            + ", frost_fang=" + frostFang + ", wanted 1/2)");
            helper.assertTrue(TameableUtils.hasCollar(fresh), "respawned wolf lost its collar flag");
            helper.assertTrue(
                    level.getBlockEntity(absBed) instanceof PetBedBlockEntity bed
                            && fresh.getUUID().equals(bed.getClaimedPet()),
                    "bed claim did not move to the respawned wolf's fresh UUID");
            helper.assertTrue(collarDrops(helper).isEmpty(),
                    "collar must ride the respawn, not ALSO drop as an item");
            // restore the pinned global clock for later batches
            level.setDayTime(dayTimeBefore);
            level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(daylightBefore, level.getServer());
            fresh.discard();
        });
    }

    /**
     * Exclusive beds: while a living, tamed claimant still points at its bed,
     * a second bedless wolf standing on that bed must be refused - the claim
     * stays with the first wolf and the second never records a bed link. The
     * 100-tick window covers many entityInside claim attempts (every 10
     * ticks) plus two of the bed's 40-tick claim-validation sweeps.
     */
    @GameTest(template = "platform", timeoutTicks = 200)
    public void exclusive_bed_refuses_second_wolf(GameTestHelper helper) {
        helper.assertTrue(DomesticationMod.CONFIG.exclusivePetBeds.get()
                        && DomesticationMod.CONFIG.petBedRespawns.get(),
                "precondition: exclusive_pet_beds + pet_bed_respawns must default true");
        ServerLevel level = helper.getLevel();
        helper.setBlock(BED_REL, DIBlockRegistry.WHITE_PET_BED.get());
        BlockPos absBed = helper.absolutePos(BED_REL);

        Wolf first = tamedWolf(helper, new BlockPos(2, 2, 2));
        helper.assertTrue(PetBedBlockEntity.tryClaim(level, absBed, first), "first wolf's claim refused");
        TameableUtils.setPetBedPos(first, absBed);
        TameableUtils.setPetBedDimension(first, level.dimension().toString());

        Wolf second = tamedWolf(helper, BED_REL.above(2)); // drops onto the claimed bed
        helper.assertTrue(TameableUtils.getPetBedPos(second) == null, "second wolf must start bedless");

        helper.runAfterDelay(100, () -> {
            helper.assertTrue(
                    level.getBlockEntity(absBed) instanceof PetBedBlockEntity bed
                            && first.getUUID().equals(bed.getClaimedPet()),
                    "second wolf stole the bed claim from its living owner");
            helper.assertTrue(TameableUtils.getPetBedPos(second) == null,
                    "second wolf recorded a bed link to a bed it must not own");
            helper.assertTrue(absBed.equals(TameableUtils.getPetBedPos(first)),
                    "first wolf's bed link was dropped");
            first.discard();
            second.discard();
            helper.succeed();
        });
    }

    /**
     * Undead curse: a cursed pet's death spawns an untamed zombie copy
     * carrying the pet data - asserted through the mod's own
     * {@link TameableUtils#isZombiePet} (the dying original is guarded OUT of
     * that predicate, so a match is necessarily the resurrected copy). The
     * curse only fires outside Peaceful, so difficulty is forced to NORMAL
     * first; own batch because that poke is server-global.
     */
    @GameTest(template = "platform", timeoutTicks = 200, batch = "collarLifecycleZombie")
    public void undead_curse_spawns_zombie_pet(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        level.getServer().setDifficulty(Difficulty.NORMAL, true);
        helper.assertTrue(level.getDifficulty() != Difficulty.PEACEFUL,
                "precondition: undead_curse resurrection requires non-Peaceful difficulty");
        Wolf wolf = tamedWolf(helper, new BlockPos(4, 2, 4));
        TameableUtils.addEnchant(wolf, DIEnchantmentKeys.UNDEAD_CURSE.location(), 1);
        helper.assertTrue(TameableUtils.hasEnchant(wolf, DIEnchantmentKeys.UNDEAD_CURSE),
                "undead_curse enchant did not stick (config-disabled?)");
        helper.assertFalse(TameableUtils.isZombiePet(wolf), "living pet must not read as zombie pet");
        kill(wolf);
        helper.succeedWhen(() -> {
            helper.assertFalse(wolf.isAlive(), "wolf survived a 1000-damage generic kill");
            List<Wolf> zombies = level.getEntitiesOfClass(Wolf.class, helper.getBounds(),
                    w -> w != wolf && w.isAlive() && TameableUtils.isZombiePet(w));
            helper.assertTrue(zombies.size() == 1,
                    "expected exactly 1 zombie pet, found " + zombies.size());
            Wolf zombie = zombies.get(0);
            helper.assertFalse(TameableUtils.isTamed(zombie), "zombie pet must not still be tamed");
            helper.assertTrue(TameableUtils.hasEnchant(zombie, DIEnchantmentKeys.UNDEAD_CURSE),
                    "zombie copy must inherit the pet data (undead_curse missing)");
            zombie.discard();
        });
    }
}
