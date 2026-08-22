package com.github.alexthe668.domesticationinnovation.mixin;

import com.github.alexthe668.domesticationinnovation.server.entity.ModifedToBeTameable;
import com.github.alexthe668.domesticationinnovation.server.entity.TameableUtils;
import com.github.alexthe668.domesticationinnovation.server.entity.ai.PetCombatRules;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.sensing.FrogAttackablesSensor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

@Mixin(FrogAttackablesSensor.class)
public class FrogAttackablesSensorMixin {

    @Inject(
            method = {"Lnet/minecraft/world/entity/ai/sensing/FrogAttackablesSensor;isMatchingEntity(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/LivingEntity;)Z"},
            remap = true,
            at = @At(value = "HEAD"),
            cancellable = true
    )
    private void di_isHuntTarget(LivingEntity frog, LivingEntity livingEntity, CallbackInfoReturnable<Boolean> cir) {
        if(frog instanceof ModifedToBeTameable tamed && tamed.getTameOwner() != null){
            UUID ownerUUID = tamed.getTameOwnerUUID();
            if((ownerUUID != null && ownerUUID.equals(livingEntity.getUUID())) || TameableUtils.hasSameOwnerAs(frog, livingEntity)){
                cir.setReturnValue(false);
            }else if(!tamed.isStayingStill() && tamed.isValidAttackTarget(livingEntity) && PetCombatRules.wantsToFight(frog, livingEntity)){
                cir.setReturnValue(true);
            }
        }
    }
}
