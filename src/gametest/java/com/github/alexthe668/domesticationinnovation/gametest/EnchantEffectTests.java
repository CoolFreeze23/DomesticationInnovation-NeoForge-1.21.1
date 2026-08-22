package com.github.alexthe668.domesticationinnovation.gametest;

import com.github.alexthe668.domesticationinnovation.DomesticationMod;
import com.github.alexthe668.domesticationinnovation.server.enchantment.DIEnchantmentKeys;
import com.github.alexthe668.domesticationinnovation.server.entity.TameableUtils;
import com.github.alexthe668.domesticationinnovation.server.misc.DIEffectRegistry;

import java.util.Objects;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Collar-enchantment effect tests, exercised through the mod's own APIs
 * ({@link TameableUtils#addEnchant} -&gt; onUpdateEnchants and the
 * CommonProxy tick / LivingDamageEvent.Pre handlers) rather than raw NBT.
 *
 * <p>Conventions (mirroring the owner's OreSpawn suite):
 * <ul>
 *   <li>Every pet and target is spawned with
 *       {@link GameTestHelper#spawnWithNoFreeWill} so no AI goal moves,
 *       retargets, or attacks on its own — every hit in these tests is dealt
 *       explicitly, making the damage-event assertions deterministic.</li>
 *   <li>Wolves are tamed to a mock player: the retaliation enchants
 *       (chaos/paralysis/share/violent) are gated on
 *       {@code TameableUtils.isTamed} in CommonProxy.onLivingDamage.</li>
 *   <li>Zombies/skeletons wear iron helmets so daylight burning can never
 *       inject stray fire damage into an HP assertion.</li>
 *   <li>The share test runs in its own batch: shareDamageWithEnemies hits
 *       every Enemy within 20 blocks, which could reach a neighboring test's
 *       template on the shared structure grid.</li>
 * </ul>
 *
 * <p>Deliberately NOT covered: survival of the attribute modifiers across a
 * world reload. The modifiers are transient by design and re-applied by the
 * entity-join path (CommonProxy.refreshEnchantAttributeModifiers); a gametest
 * cannot serialize + rejoin an entity through a real save/load cycle, so that
 * path is left to manual testing.
 */
@GameTestHolder(DomesticationMod.MODID)
@PrefixGameTestTemplate(false)
public class EnchantEffectTests {

    // ==================== shared helpers ====================

    /**
     * Spawns a goal-less wolf and tames it to a fresh mock player. The mock
     * player is never added to the level — taming only records the owner UUID,
     * which is all {@code TameableUtils.isTamed}/{@code hasSameOwnerAs} need.
     * setTame(true, true) applies Wolf's taming side effects (raised tamed
     * max health), so callers must read attribute baselines AFTER this
     * returns.
     */
    private static Wolf tamedWolf(GameTestHelper helper, BlockPos rel) {
        Wolf wolf = helper.spawnWithNoFreeWill(EntityType.WOLF, rel);
        Player owner = helper.makeMockPlayer(GameType.SURVIVAL);
        wolf.setTame(true, true);
        wolf.setOwnerUUID(owner.getUUID());
        return wolf;
    }

    /** Stores a collar enchant through the mod's real API (triggers onUpdateEnchants). */
    private static void enchant(LivingEntity pet, ResourceKey<Enchantment> key, int level) {
        TameableUtils.addEnchant(pet, key.location(), level);
    }

    /** Iron helmet so an undead mob never burns in daylight mid-assertion. */
    private static <E extends Mob> E sunProof(E mob) {
        mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        return mob;
    }

    private static void setMaxHealth(LivingEntity e, double hp) {
        Objects.requireNonNull(e.getAttribute(Attributes.MAX_HEALTH)).setBaseValue(hp);
        e.setHealth((float) hp);
    }

    // =====================================================================
    // (1) health_boost + tough — attribute modifier application
    // =====================================================================

    /**
     * addEnchant -&gt; onUpdateEnchants applies the attribute modifiers
     * synchronously: HEALTH_BOOST I adds +10 max health (level * 10 *
     * enchant_power_multiplier, default 1.0); TOUGH I adds +3 armor and +3
     * knockback resistance (TameableUtils.applyToughModifiers). Vanilla's
     * KNOCKBACK_RESISTANCE attribute is range-clamped to [0,1], so the value
     * reads exactly 1.0 rather than 3.0.
     */
    @GameTest(template = "platform", timeoutTicks = 100)
    public void health_boost_and_tough_apply_attribute_modifiers(GameTestHelper helper) {
        Wolf wolf = tamedWolf(helper, new BlockPos(4, 2, 4));

        double baseMax = wolf.getMaxHealth();
        double baseArmor = wolf.getAttributeValue(Attributes.ARMOR);
        double baseKb = wolf.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
        helper.assertTrue(baseKb < 0.01, "wolf should start with no knockback resistance, got " + baseKb);

        enchant(wolf, DIEnchantmentKeys.HEALTH_BOOST, 1);
        enchant(wolf, DIEnchantmentKeys.TOUGH, 1);

        double maxDelta = wolf.getMaxHealth() - baseMax;
        helper.assertTrue(Math.abs(maxDelta - 10.0) < 0.01,
                "HEALTH_BOOST I should add +10 max health, delta was " + maxDelta);

        double armorDelta = wolf.getAttributeValue(Attributes.ARMOR) - baseArmor;
        helper.assertTrue(Math.abs(armorDelta - 3.0) < 0.01,
                "TOUGH I should add +3 armor, delta was " + armorDelta);

        double kb = wolf.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
        helper.assertTrue(kb > 0.99,
                "TOUGH I should max out (clamp) knockback resistance at 1.0, got " + kb);

        // Health must never exceed the new max, and the pet must stay alive.
        helper.assertTrue(wolf.isAlive() && wolf.getHealth() <= wolf.getMaxHealth() + 0.01,
                "health must remain valid after the modifier swap");
        helper.succeed();
    }

    // =====================================================================
    // (3) insight — glowing on enemies in the dark
    // =====================================================================

    /**
     * CommonProxy.tickUtilityEnchants: an insight pet standing where local
     * brightness &lt; 9 pulses Glowing (20t) onto every Enemy within
     * level * 15 blocks, on a 10-tick stagger. The level is forced to
     * midnight so the sky-lit platform reads dark (raw brightness 15 minus
     * skyDarken 11 = 4 &lt; 9).
     */
    @GameTest(template = "platform", timeoutTicks = 200)
    public void insight_reveals_enemies_in_the_dark(GameTestHelper helper) {
        helper.getLevel().setDayTime(18000L); // midnight; skyDarken updates next level tick

        Wolf wolf = tamedWolf(helper, new BlockPos(2, 2, 4));
        enchant(wolf, DIEnchantmentKeys.INSIGHT, 1);

        Zombie zombie = sunProof(helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(7, 2, 4)));

        helper.succeedWhen(() -> helper.assertTrue(zombie.hasEffect(MobEffects.GLOWING),
                "insight should apply Glowing to the zombie in the dark"));
    }

    // =====================================================================
    // (4) chaos + paralysis — retaliation on the pet's attacker
    // =====================================================================

    /**
     * CommonProxy.onLivingDamage victim-side: when a tamed pet with CHAOS and
     * PARALYSIS takes a hit from a living attacker, the attacker receives the
     * DRUNK effect (120t) plus the paralysis debuff trio (slowdown, mining
     * fatigue, weakness — level * 20 ticks). One zombie melee hit through
     * doHurtTarget gives full attacker attribution.
     */
    @GameTest(template = "platform", timeoutTicks = 100)
    public void chaos_and_paralysis_strike_back_at_attacker(GameTestHelper helper) {
        Wolf wolf = tamedWolf(helper, new BlockPos(3, 2, 4));
        enchant(wolf, DIEnchantmentKeys.CHAOS, 1);
        enchant(wolf, DIEnchantmentKeys.PARALYSIS, 1);

        Zombie attacker = sunProof(helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(4, 2, 4)));

        helper.runAfterDelay(5, () -> helper.assertTrue(attacker.doHurtTarget(wolf),
                "zombie melee hit on the wolf must connect"));

        // Paralysis I lasts only 20 ticks, but succeedWhen samples every tick,
        // so the window right after the tick-5 hit is always observed.
        helper.succeedWhen(() -> {
            helper.assertTrue(attacker.hasEffect(DIEffectRegistry.DRUNK),
                    "chaos should make the attacker drunk");
            helper.assertTrue(attacker.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)
                            && attacker.hasEffect(MobEffects.DIG_SLOWDOWN)
                            && attacker.hasEffect(MobEffects.WEAKNESS),
                    "paralysis should apply the slowdown/fatigue/weakness trio to the attacker");
        });
    }

    // =====================================================================
    // (5) share — damage spread to every nearby enemy
    // =====================================================================

    /**
     * CommonProxy.shareDamageWithEnemies: when the tamed SHARE pet takes a
     * hit and more than one Enemy stands within 20 blocks, each of them takes
     * 30% of the original damage with the same source. A 10.0 hit shares 3.0
     * raw; the bystander zombies' armor (2 natural + 2 helmet) reduces that
     * to ~2.7 actual HP loss, asserted as a [2.0, 3.5] band.
     *
     * <p>Own batch: the 20-block enemy scan would otherwise reach zombies
     * standing in neighboring templates on the shared test grid.
     */
    @GameTest(template = "platform", timeoutTicks = 100, batch = "di_share_isolated")
    public void share_spreads_damage_to_nearby_enemies(GameTestHelper helper) {
        Wolf wolf = tamedWolf(helper, new BlockPos(4, 2, 4));
        enchant(wolf, DIEnchantmentKeys.SHARE, 1);

        Zombie attacker = sunProof(helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(2, 2, 2)));
        Zombie bystanderA = sunProof(helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(6, 2, 2)));
        Zombie bystanderB = sunProof(helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(6, 2, 6)));
        float fullHealth = bystanderA.getMaxHealth();

        helper.runAfterDelay(5, () -> helper.assertTrue(
                wolf.hurt(helper.getLevel().damageSources().mobAttack(attacker), 10.0F),
                "the attributed 10.0 hit on the SHARE wolf must connect"));

        helper.succeedWhen(() -> {
            for (Zombie bystander : new Zombie[]{bystanderA, bystanderB}) {
                float lost = fullHealth - bystander.getHealth();
                helper.assertTrue(lost >= 2.0F && lost <= 3.5F,
                        "bystander zombie should lose ~2.7 HP (30% of 10 after armor), lost " + lost);
            }
        });
    }

    // =====================================================================
    // (6) violent — on-hit mayhem wheel (tolerant smoke test)
    // =====================================================================

    /**
     * CommonProxy.rollViolentWheel rolls once per landed hit; every branch is
     * observable as an effect (poison / paralysis trio / drunk / slow+weak),
     * fire, or death (instakill or accumulated bonus damage on the 200-HP
     * dummy). The wheel is random, so the wolf keeps hitting every 15 ticks
     * (past the 10-tick invulnerability window) and the test succeeds on the
     * first observable outcome: with a &gt;=59% per-hit chance of an
     * effect/fire branch across ~25 hits, a false timeout is vanishingly
     * unlikely, and pure-damage streaks end in a detectable death anyway.
     */
    @GameTest(template = "platform", timeoutTicks = 400)
    public void violent_wheel_smoke(GameTestHelper helper) {
        Wolf wolf = tamedWolf(helper, new BlockPos(3, 2, 4));
        enchant(wolf, DIEnchantmentKeys.VIOLENT, 1);

        // Passive high-HP dummy: not an Enemy (no cross-enchant interactions),
        // no effect immunities, and 200 HP so base wolf bites alone cannot
        // kill it inside the test window.
        Cow dummy = helper.spawnWithNoFreeWill(EntityType.COW, new BlockPos(4, 2, 4));
        setMaxHealth(dummy, 200.0D);

        for (int hit = 1; hit <= 25; hit++) {
            helper.runAfterDelay(hit * 15L, () -> {
                if (wolf.isAlive() && dummy.isAlive()) {
                    wolf.doHurtTarget(dummy);
                }
            });
        }

        helper.succeedWhen(() -> helper.assertTrue(
                !dummy.isAlive()
                        || dummy.isOnFire()
                        || dummy.hasEffect(MobEffects.POISON)
                        || dummy.hasEffect(DIEffectRegistry.DRUNK)
                        || dummy.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)
                        || dummy.hasEffect(MobEffects.DIG_SLOWDOWN)
                        || dummy.hasEffect(MobEffects.WEAKNESS),
                "violent wheel should eventually produce an observable outcome on the dummy"));
    }

    // =====================================================================
    // (7) sonic_boom — 200-tick warden boom at the pet's combat target
    // =====================================================================

    /**
     * CommonProxy.tickSonicBoom fires only when {@code mob.tickCount % 200 ==
     * 0} and the target is within 10 blocks. The target is set directly (no
     * AI goals exist on either side to add or clear it), so by tick 230 the
     * skeleton must have eaten the 10.0 armor-bypassing boom. The skeleton's
     * only other possible damage sources (sun, wolf melee) are removed via
     * helmet + no-free-will.
     */
    @GameTest(template = "platform", timeoutTicks = 280)
    public void sonic_boom_strikes_target_after_timer(GameTestHelper helper) {
        Wolf wolf = tamedWolf(helper, new BlockPos(2, 2, 4));
        enchant(wolf, DIEnchantmentKeys.SONIC_BOOM, 1);

        Skeleton target = sunProof(helper.spawnWithNoFreeWill(EntityType.SKELETON, new BlockPos(6, 2, 4)));
        wolf.setTarget(target);

        helper.runAfterDelay(230, () -> {
            float lost = target.getMaxHealth() - target.getHealth();
            helper.assertTrue(lost >= 5.0F,
                    "sonic boom (10.0, bypasses armor) should have hit the skeleton by tick 230, lost only " + lost);
            helper.succeed();
        });
    }
}
