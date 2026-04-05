package com.github.alexthe668.domesticationinnovation.mixin;
import com.github.alexthe668.domesticationinnovation.server.entity.ModifedToBeTameable;
import com.github.alexthe668.domesticationinnovation.DomesticationMod;
import com.github.alexthe668.domesticationinnovation.server.enchantment.DIEnchantmentKeys;
import com.github.alexthe668.domesticationinnovation.server.entity.TameableUtils;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FollowOwnerGoal.class)
public abstract class FollowOwnerGoalMixin extends Goal {

    @Shadow
    @Final
    private TamableAnimal tamable;
    @Shadow
    private LivingEntity owner;
    @Shadow @Final private double speedModifier;

    @Inject(
            at = {@At("HEAD")},
            remap = true,
            method = {"Lnet/minecraft/world/entity/ai/goal/FollowOwnerGoal;canUse()Z"},
            cancellable = true
    )
    private void di_canUse(CallbackInfoReturnable<Boolean> cir){
        if(tamable instanceof ModifedToBeTameable commandableMob && commandableMob.getCommand() != 2 && DomesticationMod.CONFIG.trinaryCommandSystem.get()){
            cir.setReturnValue(false);
        }
    }

    @Inject(
            at = {@At("HEAD")},
            remap = true,
            method = {"Lnet/minecraft/world/entity/ai/goal/FollowOwnerGoal;canContinueToUse()Z"},
            cancellable = true
    )
    private void di_canContinueToUse(CallbackInfoReturnable<Boolean> cir){
        if(tamable instanceof ModifedToBeTameable commandableMob && commandableMob.getCommand() != 2 && DomesticationMod.CONFIG.trinaryCommandSystem.get()){
            cir.setReturnValue(false);
        }
    }

    @Inject(
            at = {@At("HEAD")},
            remap = true,
            method = {"Lnet/minecraft/world/entity/ai/goal/FollowOwnerGoal;tick()V"},
            cancellable = true
    )
    private void di_tick(CallbackInfo ci) {
        if(TameableUtils.hasEnchant(tamable, DIEnchantmentKeys.AMPHIBIOUS) && tamable.isInWaterOrBubble() && this.tamable.distanceToSqr(this.owner) < 144.0D){
            tamable.getNavigation().moveTo(owner, speedModifier);
            ci.cancel();
        }
    }
}