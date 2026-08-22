package com.github.alexthe668.domesticationinnovation.server.entity.ai;

import com.github.alexthe668.domesticationinnovation.DomesticationMod;
import com.github.alexthe668.domesticationinnovation.server.enchantment.DIEnchantmentKeys;
import com.github.alexthe668.domesticationinnovation.server.entity.ModifedToBeTameable;
import com.github.alexthe668.domesticationinnovation.server.entity.TameableUtils;
import com.github.alexthe668.domesticationinnovation.server.misc.DIDataRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * Goal surgery and owner-aware AI for mobs tamed through the datapack taming
 * registry (see DIDataRegistries.TAMING). These are arbitrary Mobs with no
 * species mixin: their owner and command live in the PET_DATA attachment and
 * are reached exclusively through the TameableUtils generic accessors, so the
 * goals here never cast to ModifedToBeTameable (the existing Sit2Goal /
 * FollowOwner2Goal family does, and would throw on a mob that lacks it).
 *
 * Safety posture, learned from the compat churn generic pet AI caused
 * elsewhere: everything is server-side, every per-goal operation is
 * individually guarded, goal classes are only matched by instanceof against
 * vanilla types, and mobs whose goal selectors misbehave are skipped with a
 * log line instead of crashing the interaction. Mobs never tamed through the
 * data path are never touched.
 */
public class GenericPetAI {

    private GenericPetAI() {}

    /**
     * Goal priorities for injected pet AI, documented once here so future
     * species work stays consistent:
     * <ul>
     *   <li>goalSelector: sit {@value #SIT_PRIORITY}, follow
     *       {@value #FOLLOW_PRIORITY} - both still below typical RandomStroll
     *       (4-5), so follow keeps preempting idle wandering. Vanilla mobs
     *       keep FloatGoal at 0 and PanicGoal at ~1, and GoalSelector only
     *       displaces a running goal for a STRICTLY lower priority number, so
     *       sit deliberately lives at 2 (like vanilla Cat: panic 1, sit 2):
     *       a "staying" pet that catches fire can still panic to water
     *       instead of sitting in the flames.</li>
     *   <li>goalSelector: bed stroll {@value #BED_STROLL_PRIORITY} -
     *       BedAnchoredStrollGoal is flagless so its priority is cosmetic;
     *       10 matches FoxMixin/CatMixin.</li>
     *   <li>targetSelector: owner-hurt-by {@value #OWNER_HURT_BY_PRIORITY},
     *       owner-hurt {@value #OWNER_HURT_PRIORITY} - vanilla Wolf's
     *       ordering (defend the owner first), in the slots the stripped
     *       HurtByTargetGoal/NearestAttackableTargetGoal typically held.</li>
     * </ul>
     */
    public static final int SIT_PRIORITY = 2;
    public static final int FOLLOW_PRIORITY = 3;
    public static final int BED_STROLL_PRIORITY = 10;
    public static final int OWNER_HURT_BY_PRIORITY = 1;
    public static final int OWNER_HURT_PRIORITY = 2;

    /**
     * Installs pet AI on a data-tamed mob. Called once at tame time by the
     * interaction path; {@link #reapplyOnLoad} routes joins back through here.
     * Idempotent: applying twice never duplicates a goal.
     *
     * <ul>
     *   <li>Blacklisted mobs (taming_blacklist tag): untouched, always.</li>
     *   <li>Brain-AI mobs (uses_brain_ai tag): no goal surgery at all - their
     *       hostility memories are cleared and bed-anchored roaming is driven
     *       per tick by {@link #tickBrainRoamRestriction}.</li>
     *   <li>All goal mobs: hostile targeting goals (NearestAttackableTarget,
     *       HurtByTarget) are stripped so the new pet stops hunting.</li>
     *   <li>TamableAnimal subclasses keep their own vanilla sit/follow AI, and
     *       ModifedToBeTameable species keep their mixin-installed DI AI;
     *       injecting a second follow goal would fight the native one. (For
     *       ModifedToBeTameable this also avoids a real trap: AbstractHorse's
     *       command store always reports 1, so a generic sit goal would park a
     *       data-tamed horse forever.)</li>
     *   <li>Everything else gets the generic sit/follow/owner-defense goals
     *       plus the bed-anchored stroll goal, each only if its role is not
     *       already filled.</li>
     * </ul>
     */
    public static void applyGenericPetAI(Mob mob) {
        if (mob.level().isClientSide || mob.getType().is(DIDataRegistries.TAMING_BLACKLIST)) {
            return;
        }
        try {
            clearHostileIntent(mob);
            if (mob.getType().is(DIDataRegistries.USES_BRAIN_AI)) {
                // No goal surgery on brain mobs. Roaming follows the same
                // config/command/bed-gated getRoamAnchor cycle as everyone
                // else, driven from the per-entity server tick.
                return;
            }
            stripHostileTargetGoals(mob);
            if (mob instanceof TamableAnimal || mob instanceof ModifedToBeTameable) {
                return;
            }
            addGoalIfRoleAbsent(mob.goalSelector, SIT_PRIORITY,
                    () -> new GenericSitGoal(mob), GenericSitGoal.class, Sit2Goal.class);
            addGoalIfRoleAbsent(mob.goalSelector, FOLLOW_PRIORITY,
                    () -> new GenericFollowOwnerGoal(mob, 1.2D, 10.0F, 3.0F),
                    GenericFollowOwnerGoal.class, FollowOwner2Goal.class);
            addGoalIfRoleAbsent(mob.goalSelector, BED_STROLL_PRIORITY,
                    () -> new BedAnchoredStrollGoal(mob), BedAnchoredStrollGoal.class);
            addGoalIfRoleAbsent(mob.targetSelector, OWNER_HURT_BY_PRIORITY,
                    () -> new GenericOwnerHurtByTargetGoal(mob),
                    GenericOwnerHurtByTargetGoal.class, OwnerHurtByTarget2Goal.class);
            addGoalIfRoleAbsent(mob.targetSelector, OWNER_HURT_PRIORITY,
                    () -> new GenericOwnerHurtTargetGoal(mob),
                    GenericOwnerHurtTargetGoal.class, OwnerHurtTarget2Goal.class);
        } catch (Exception e) {
            DomesticationMod.LOGGER.warn("Could not apply generic pet AI to {}: {}",
                    mob.getType().getDescriptionId(), e.toString());
        }
    }

    /**
     * Re-establishes pet AI when an already-data-tamed mob re-enters the
     * world: injected goals are runtime-only and the mob's registerGoals()
     * has just re-added everything we strip. Safe to call for every joining
     * Mob - it self-gates on data-tame state. Wire from the server-side join
     * path (EntityJoinLevelEvent) in CommonProxy.
     *
     * The blacklist is deliberately re-checked on every load, so adding a
     * misbehaving mob to the tag also calms down pets tamed before the tag
     * entry existed.
     */
    public static void reapplyOnLoad(Mob mob) {
        // The DataTameStripped marker catches native-branch pets
        // (TamableAnimal/ModifedToBeTameable) whose ownership lives in the
        // native API, not the generic PET_DATA record isDataTamed reads.
        if (mob.level().isClientSide
                || (!TameableUtils.isDataTamed(mob) && !TameableUtils.isDataTameStripped(mob))) {
            return;
        }
        applyGenericPetAI(mob);
    }

    /**
     * Bed-anchored roaming for brain-AI data pets, mirroring the
     * restrictTo/clearRestriction cycle AxolotlMixin/FrogMixin run from their
     * species tick hooks (Wave 1). Call unconditionally from the per-entity
     * server tick (CommonProxy.onEntityTick); it self-gates on side, the
     * uses_brain_ai tag and data-tame state. Goal-driven pets are excluded -
     * they run BedAnchoredStrollGoal instead.
     */
    public static void tickBrainRoamRestriction(Mob mob) {
        if (mob.level().isClientSide || !mob.getType().is(DIDataRegistries.USES_BRAIN_AI)) {
            return;
        }
        // The release path deliberately runs before the isDataTamed gate:
        // toggling data_driven_taming off mid-session must still release a
        // restriction this helper applied earlier.
        BlockPos recorded = TameableUtils.getGenericRoamAnchor(mob);
        BlockPos anchor = TameableUtils.isDataTamed(mob)
                ? BedAnchoredStrollGoal.getRoamAnchor(mob) : null;
        if (anchor != null) {
            int radius = DomesticationMod.CONFIG.petRoamingRadius.get();
            if (!mob.hasRestriction() || !anchor.equals(mob.getRestrictCenter()) || mob.getRestrictRadius() != (float) radius) {
                mob.restrictTo(anchor, radius);
            }
            // Remember what we applied - mirrors BedAnchoredStrollGoal's
            // appliedAnchor field, persisted because this path has no goal
            // lifecycle. The record is what lets us recognize our own
            // restriction after the bed link itself is erased (bed broken or
            // claimed: getRoamAnchor and getPetBedPos both go null).
            if (!anchor.equals(recorded)) {
                TameableUtils.setGenericRoamAnchor(mob, anchor);
            }
        } else if (recorded != null) {
            // Only release a restriction still anchored where we put it, so
            // leash anchors and other mods' restrictTo calls are never
            // clobbered; either way the stale record is erased.
            if (mob.hasRestriction() && recorded.equals(mob.getRestrictCenter())) {
                mob.clearRestriction();
            }
            TameableUtils.clearGenericRoamAnchor(mob);
        }
    }

    /**
     * A mob mid-fight when tamed would otherwise keep swinging: attack goals
     * read getTarget(), which survives the removal of whatever targeting goal
     * set it. Clears the goal-system target and - harmlessly on goal mobs,
     * since Brain.eraseMemory no-ops for unregistered modules - the brain's
     * attack memories.
     */
    private static void clearHostileIntent(Mob mob) {
        try {
            mob.setTarget(null);
            mob.setLastHurtByMob(null);
            mob.setAggressive(false);
            mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
            mob.getBrain().eraseMemory(MemoryModuleType.ANGRY_AT);
        } catch (Exception e) {
            DomesticationMod.LOGGER.debug("Could not clear hostile intent on data-tamed {}: {}",
                    mob.getType().getDescriptionId(), e.toString());
        }
    }

    /**
     * Removes vanilla hostile targeting goals so a freshly tamed mob stops
     * attacking players and livestock. Both selectors are swept - vanilla
     * registers these on the target selector, but modded mobs sometimes put
     * them on the goal selector. Each step is guarded per goal: a modded goal
     * whose accessors throw only loses itself, never the whole pass.
     */
    private static void stripHostileTargetGoals(Mob mob) {
        stripHostileTargetGoals(mob, mob.targetSelector);
        stripHostileTargetGoals(mob, mob.goalSelector);
    }

    private static void stripHostileTargetGoals(Mob mob, GoalSelector selector) {
        List<Goal> toRemove = new ArrayList<>();
        try {
            for (var wrapped : selector.getAvailableGoals()) {
                try {
                    Goal goal = wrapped == null ? null : wrapped.getGoal();
                    if (goal instanceof NearestAttackableTargetGoal<?> || goal instanceof HurtByTargetGoal) {
                        toRemove.add(goal);
                    }
                } catch (Exception ignored) {
                    // A single misbehaving wrapped goal must not stop the scan
                }
            }
        } catch (Exception e) {
            DomesticationMod.LOGGER.warn("Skipping goal surgery on {}: goal selector unreadable ({})",
                    mob.getType().getDescriptionId(), e.toString());
            return;
        }
        for (Goal goal : toRemove) {
            try {
                selector.removeGoal(goal);
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Adds a goal only when no goal of the same role - this class's generic
     * variant or the species-mixin equivalent - is already installed, which is
     * what makes repeated application (tame time, then every load) safe.
     * An unreadable selector counts as "role filled" so nothing is ever
     * double-added or forced onto an exotic modded selector.
     */
    private static void addGoalIfRoleAbsent(GoalSelector selector, int priority,
                                            java.util.function.Supplier<Goal> factory, Class<?>... roleClasses) {
        try {
            for (var wrapped : new ArrayList<>(selector.getAvailableGoals())) {
                Goal goal = wrapped == null ? null : wrapped.getGoal();
                if (goal == null) {
                    continue;
                }
                for (Class<?> roleClass : roleClasses) {
                    if (roleClass.isInstance(goal)) {
                        return;
                    }
                }
            }
            selector.addGoal(priority, factory.get());
        } catch (Exception ignored) {
            // Unreadable or rejecting selector: skip this role entirely
        }
    }

    // =========================================================================
    // Generic command semantics (trinary: 0 = wander, 1 = stay, 2 = follow)
    // =========================================================================

    static boolean isStayingStill(Mob mob) {
        return DomesticationMod.CONFIG.trinaryCommandSystem.get()
                && TameableUtils.tryGetCommand(mob) == 1;
    }

    static boolean isFollowing(Mob mob) {
        return DomesticationMod.CONFIG.trinaryCommandSystem.get()
                && TameableUtils.tryGetCommand(mob) == 2;
    }

    @Nullable
    static LivingEntity getOwner(Mob mob) {
        return TameableUtils.getOwnerOf(mob) instanceof LivingEntity living ? living : null;
    }

    static boolean isValidPetTarget(Mob pet, @Nullable LivingEntity target) {
        return target != null && target != pet && target.isAlive()
                && !(target == getOwner(pet))
                && !TameableUtils.hasSameOwnerAs(pet, target);
    }

    // =========================================================================
    // Goals - attachment-backed equivalents of the Sit2Goal family
    // =========================================================================

    /** Parks the pet while its command is "stay". */
    public static class GenericSitGoal extends Goal {
        private final Mob mob;

        public GenericSitGoal(Mob mob) {
            this.mob = mob;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            return TameableUtils.isTamed(mob) && !mob.isInWaterOrBubble() && mob.onGround()
                    && isStayingStill(mob);
        }

        @Override
        public boolean canContinueToUse() {
            return TameableUtils.isTamed(mob) && isStayingStill(mob);
        }

        @Override
        public void start() {
            mob.getNavigation().stop();
        }
    }

    /** Follows (and if needed teleports to) the owner while commanded to follow. */
    public static class GenericFollowOwnerGoal extends Goal {
        private final Mob mob;
        private final double speedModifier;
        private final float startDistance;
        private final float stopDistance;
        private final PathNavigation navigation;
        private LivingEntity owner;
        private int timeToRecalcPath;
        private float oldWaterCost;

        public GenericFollowOwnerGoal(Mob mob, double speedModifier, float startDistance, float stopDistance) {
            this.mob = mob;
            this.speedModifier = speedModifier;
            this.startDistance = startDistance;
            this.stopDistance = stopDistance;
            this.navigation = mob.getNavigation();
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (!isFollowing(mob)) {
                return false;
            }
            LivingEntity candidate = getOwner(mob);
            if (candidate == null || !candidate.isAlive() || candidate.isSpectator()) {
                return false;
            }
            if (mob.distanceToSqr(candidate) < (double) (startDistance * startDistance)) {
                return false;
            }
            this.owner = candidate;
            return true;
        }

        @Override
        public boolean canContinueToUse() {
            return isFollowing(mob) && !navigation.isDone()
                    && mob.distanceToSqr(owner) > (double) (stopDistance * stopDistance);
        }

        @Override
        public void start() {
            this.timeToRecalcPath = 0;
            this.oldWaterCost = mob.getPathfindingMalus(PathType.WATER);
            mob.setPathfindingMalus(PathType.WATER, 0.0F);
        }

        @Override
        public void stop() {
            this.owner = null;
            navigation.stop();
            mob.setPathfindingMalus(PathType.WATER, oldWaterCost);
        }

        @Override
        public void tick() {
            if (TameableUtils.hasEnchant(mob, DIEnchantmentKeys.AMPHIBIOUS) && mob.isInWaterOrBubble()
                    && mob.distanceToSqr(owner) < 144.0D) {
                navigation.moveTo(owner, speedModifier);
            }
            mob.getLookControl().setLookAt(owner, 10.0F, (float) mob.getMaxHeadXRot());
            if (--timeToRecalcPath > 0) {
                return;
            }
            timeToRecalcPath = this.adjustedTickDelay(10);
            if (mob.isLeashed() || mob.isPassenger()) {
                return;
            }
            if (mob.distanceToSqr(owner) >= 144.0D && owner.isAlive()
                    && !DomesticationMod.CONFIG.disablePetTeleportation.get()) {
                teleportToOwner();
            } else {
                navigation.moveTo(owner, speedModifier);
            }
        }

        private void teleportToOwner() {
            BlockPos anchor = owner.blockPosition();
            for (int attempt = 0; attempt < 10; ++attempt) {
                int x = anchor.getX() + randomIntInclusive(-3, 3);
                int y = anchor.getY() + randomIntInclusive(-1, 1);
                int z = anchor.getZ() + randomIntInclusive(-3, 3);
                if (maybeTeleportTo(x, y, z)) {
                    return;
                }
            }
        }

        private boolean maybeTeleportTo(int x, int y, int z) {
            if (Math.abs(x - owner.getX()) < 2.0D && Math.abs(z - owner.getZ()) < 2.0D) {
                return false;
            }
            if (!canTeleportTo(new BlockPos(x, y, z))) {
                return false;
            }
            mob.moveTo(x + 0.5D, y, z + 0.5D, mob.getYRot(), mob.getXRot());
            navigation.stop();
            return true;
        }

        // Same acceptance rules as FollowOwner2Goal so a data pet with an
        // Amphibious collar teleports like a species pet would.
        private boolean canTeleportTo(BlockPos pos) {
            if (TameableUtils.hasEnchant(mob, DIEnchantmentKeys.AMPHIBIOUS) && mob.level().isWaterAt(pos)) {
                return true;
            }
            if (WalkNodeEvaluator.getPathTypeStatic(mob, pos.mutable()) != PathType.WALKABLE) {
                return false;
            }
            if (mob.level().getBlockState(pos.below()).getBlock() instanceof LeavesBlock) {
                return false;
            }
            return mob.level().noCollision(mob, mob.getBoundingBox().move(pos.subtract(mob.blockPosition())));
        }

        private int randomIntInclusive(int min, int max) {
            return mob.getRandom().nextInt(max - min + 1) + min;
        }
    }

    /** Retaliates against whatever last hurt the owner. */
    public static class GenericOwnerHurtByTargetGoal extends TargetGoal {
        private final Mob pet;
        private LivingEntity ownerLastHurtBy;
        private int timestamp;

        public GenericOwnerHurtByTargetGoal(Mob pet) {
            super(pet, false);
            this.pet = pet;
            this.setFlags(EnumSet.of(Goal.Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            if (!TameableUtils.isTamed(pet) || isStayingStill(pet)) {
                return false;
            }
            LivingEntity owner = getOwner(pet);
            if (owner == null) {
                return false;
            }
            this.ownerLastHurtBy = owner.getLastHurtByMob();
            return owner.getLastHurtByMobTimestamp() != this.timestamp
                    && isValidPetTarget(pet, ownerLastHurtBy)
                    && PetCombatRules.wantsToFight(pet, ownerLastHurtBy)
                    && this.canAttack(ownerLastHurtBy, TargetingConditions.DEFAULT);
        }

        @Override
        public boolean canContinueToUse() {
            return PetCombatRules.wantsToFight(pet, this.mob.getTarget()) && super.canContinueToUse();
        }

        @Override
        public void start() {
            this.mob.setTarget(this.ownerLastHurtBy);
            LivingEntity owner = getOwner(pet);
            if (owner != null) {
                this.timestamp = owner.getLastHurtByMobTimestamp();
            }
            super.start();
        }
    }

    /** Joins in on whatever the owner attacks. */
    public static class GenericOwnerHurtTargetGoal extends TargetGoal {
        private final Mob pet;
        private LivingEntity ownerLastHurt;
        private int timestamp;

        public GenericOwnerHurtTargetGoal(Mob pet) {
            super(pet, false);
            this.pet = pet;
            this.setFlags(EnumSet.of(Goal.Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            if (!TameableUtils.isTamed(pet) || isStayingStill(pet)) {
                return false;
            }
            LivingEntity owner = getOwner(pet);
            if (owner == null) {
                return false;
            }
            this.ownerLastHurt = owner.getLastHurtMob();
            return owner.getLastHurtMobTimestamp() != this.timestamp
                    && isValidPetTarget(pet, ownerLastHurt)
                    && PetCombatRules.wantsToFight(pet, ownerLastHurt)
                    && this.canAttack(ownerLastHurt, TargetingConditions.DEFAULT);
        }

        @Override
        public boolean canContinueToUse() {
            return PetCombatRules.wantsToFight(pet, this.mob.getTarget()) && super.canContinueToUse();
        }

        @Override
        public void start() {
            this.mob.setTarget(this.ownerLastHurt);
            LivingEntity owner = getOwner(pet);
            if (owner != null) {
                this.timestamp = owner.getLastHurtMobTimestamp();
            }
            super.start();
        }
    }
}
