package com.github.alexthe668.domesticationinnovation.server.entity.ai;

import com.github.alexthe668.domesticationinnovation.server.entity.ModifedToBeTameable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.animal.Animal;

import java.util.EnumSet;

public class OwnerHurtByTarget2Goal extends TargetGoal {
    private final Animal tameAnimal;
    private LivingEntity ownerLastHurt;
    private int timestamp;

    public OwnerHurtByTarget2Goal(Animal p_26114_) {
        super(p_26114_, false);
        this.tameAnimal = p_26114_;
        this.setFlags(EnumSet.of(Flag.TARGET));
    }

    public boolean canUse() {
        if (((ModifedToBeTameable)this.tameAnimal).isTame() && !((ModifedToBeTameable)this.tameAnimal).isStayingStill()) {
            LivingEntity livingentity = ((ModifedToBeTameable)this.tameAnimal).getTameOwner();
            if (livingentity == null) {
                return false;
            } else {
                this.ownerLastHurt = livingentity.getLastHurtByMob();
                int i = livingentity.getLastHurtByMobTimestamp();
                return i != this.timestamp && PetCombatRules.wantsToFight(this.tameAnimal, this.ownerLastHurt) && this.canAttack(this.ownerLastHurt, TargetingConditions.DEFAULT) && ((ModifedToBeTameable)this.tameAnimal).isValidAttackTarget(this.ownerLastHurt);
            }
        } else {
            return false;
        }
    }

    public boolean canContinueToUse() {
        return PetCombatRules.wantsToFight(this.tameAnimal, this.mob.getTarget()) && super.canContinueToUse();
    }

    public void start() {
        this.mob.setTarget(this.ownerLastHurt);
        LivingEntity livingentity = ((ModifedToBeTameable)this.tameAnimal).getTameOwner();
        if (livingentity != null) {
            this.timestamp = livingentity.getLastHurtByMobTimestamp();
        }

        super.start();
    }
}