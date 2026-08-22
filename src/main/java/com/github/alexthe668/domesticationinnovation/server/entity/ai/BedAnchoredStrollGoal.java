package com.github.alexthe668.domesticationinnovation.server.entity.ai;

import com.github.alexthe668.domesticationinnovation.DomesticationMod;
import com.github.alexthe668.domesticationinnovation.server.entity.TameableUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.Goal;

import javax.annotation.Nullable;
import java.util.EnumSet;

/**
 * Keeps a wandering pet close to its claimed pet bed by maintaining the
 * vanilla Mob movement restriction while the pet is in wander mode.
 *
 * The goal declares no flags, so it runs alongside every movement goal
 * instead of competing with them: vanilla random-stroll position generators
 * reject targets outside the mob's restriction, which is what actually
 * constrains the wandering. Follow and teleport behavior ignore the
 * restriction, so a pet ordered to follow still leaves the radius.
 */
public class BedAnchoredStrollGoal extends Goal {

    private final Mob mob;
    private BlockPos appliedAnchor;

    public BedAnchoredStrollGoal(Mob mob) {
        this.mob = mob;
        this.setFlags(EnumSet.noneOf(Goal.Flag.class));
    }

    @Override
    public boolean canUse() {
        return getRoamAnchor(mob) != null;
    }

    @Override
    public boolean canContinueToUse() {
        return getRoamAnchor(mob) != null;
    }

    @Override
    public void start() {
        this.syncRestriction();
    }

    @Override
    public void tick() {
        this.syncRestriction();
    }

    @Override
    public void stop() {
        // Only release a restriction this goal placed, so restrictions set by
        // other systems are never clobbered.
        if (this.appliedAnchor != null && mob.hasRestriction() && this.appliedAnchor.equals(mob.getRestrictCenter())) {
            mob.clearRestriction();
        }
        this.appliedAnchor = null;
    }

    private void syncRestriction() {
        BlockPos anchor = getRoamAnchor(mob);
        if (anchor == null) {
            return;
        }
        int radius = DomesticationMod.CONFIG.petRoamingRadius.get();
        if (!mob.hasRestriction() || !anchor.equals(mob.getRestrictCenter()) || mob.getRestrictRadius() != (float) radius) {
            mob.restrictTo(anchor, radius);
        }
        this.appliedAnchor = anchor;
    }

    /**
     * The bed position this pet should currently roam around, or null when
     * roaming does not apply: the radius option is 0, the pet is untamed or
     * not in wander mode, or it has no claimed bed in its current dimension.
     * Brain-driven pets that cannot run goals can drive the same
     * restrictTo/clearRestriction cycle off this from a tick hook.
     */
    @Nullable
    public static BlockPos getRoamAnchor(Mob mob) {
        if (DomesticationMod.CONFIG.petRoamingRadius.get() <= 0 || !TameableUtils.isTamed(mob) || !isWandering(mob)) {
            return null;
        }
        BlockPos bedPos = TameableUtils.getPetBedPos(mob);
        return bedPos != null && isBedInCurrentDimension(mob) ? bedPos : null;
    }

    private static boolean isWandering(Mob mob) {
        if (DomesticationMod.CONFIG.trinaryCommandSystem.get()) {
            int command = TameableUtils.tryGetCommand(mob);
            if (command != -1) {
                return command == 0;
            }
        }
        return !(mob instanceof TamableAnimal animal) || !animal.isOrderedToSit();
    }

    private static boolean isBedInCurrentDimension(Mob mob) {
        // The stored dimension is a ResourceKey#toString, but pets that
        // claimed a bed before dimensions were recorded fall back to a bare
        // id - accept both spellings.
        String bedDimension = TameableUtils.getPetBedDimension(mob);
        return bedDimension.equals(mob.level().dimension().toString())
                || bedDimension.equals(mob.level().dimension().location().toString());
    }
}
