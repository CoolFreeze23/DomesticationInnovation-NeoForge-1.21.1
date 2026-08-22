package com.github.alexthe668.domesticationinnovation.mixin;

import com.github.alexthe668.domesticationinnovation.server.entity.ai.PetCombatRules;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.annotation.Nullable;

@Mixin(TargetGoal.class)
public abstract class TargetGoalMixin {

    @Shadow @Final protected Mob mob;

    @Shadow @Nullable protected LivingEntity targetMob;

    @Inject(
            method = {"Lnet/minecraft/world/entity/ai/goal/target/TargetGoal;canAttack(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/ai/targeting/TargetingConditions;)Z"},
            remap = true,
            at = @At(value = "HEAD"),
            cancellable = true
    )
    private void di_canAttack(LivingEntity target, TargetingConditions conditions, CallbackInfoReturnable<Boolean> cir) {
        if(!PetCombatRules.wantsToFight(this.mob, target)){
            cir.setReturnValue(false);
        }
    }

    @Inject(
            method = {"Lnet/minecraft/world/entity/ai/goal/target/TargetGoal;canContinueToUse()Z"},
            remap = true,
            at = @At(value = "HEAD"),
            cancellable = true
    )
    private void di_canContinueToUse(CallbackInfoReturnable<Boolean> cir) {
        LivingEntity target = this.mob.getTarget() == null ? this.targetMob : this.mob.getTarget();
        if(!PetCombatRules.wantsToFight(this.mob, target)){
            cir.setReturnValue(false);
        }
    }
}
