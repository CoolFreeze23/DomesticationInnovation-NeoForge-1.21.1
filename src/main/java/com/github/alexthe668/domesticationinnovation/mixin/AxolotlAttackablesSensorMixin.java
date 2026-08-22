package com.github.alexthe668.domesticationinnovation.mixin;

import com.github.alexthe668.domesticationinnovation.server.entity.ModifedToBeTameable;
import com.github.alexthe668.domesticationinnovation.server.entity.TameableUtils;
import com.github.alexthe668.domesticationinnovation.server.entity.ai.PetCombatRules;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.sensing.AxolotlAttackablesSensor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

@Mixin(AxolotlAttackablesSensor.class)
public class AxolotlAttackablesSensorMixin {

    @Inject(
            method = {"Lnet/minecraft/world/entity/ai/sensing/AxolotlAttackablesSensor;isMatchingEntity(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/LivingEntity;)Z"},
            remap = true,
            at = @At(value = "HEAD"),
            cancellable = true
    )
    private void di_isHuntTarget(LivingEntity axolotl, LivingEntity livingEntity, CallbackInfoReturnable<Boolean> cir) {
        if(axolotl instanceof ModifedToBeTameable tamed && tamed.getTameOwner() != null){
            UUID ownerUUID = tamed.getTameOwnerUUID();
            if((ownerUUID != null && ownerUUID.equals(livingEntity.getUUID())) || TameableUtils.hasSameOwnerAs(axolotl, livingEntity)){
                cir.setReturnValue(false);
            }else if(!tamed.isStayingStill()){
                if(tamed.isValidAttackTarget(livingEntity) && PetCombatRules.wantsToFight(axolotl, livingEntity)){
                    cir.setReturnValue(true);
                }else{
                    cir.setReturnValue(false);
                }
            }
        }
    }
}
