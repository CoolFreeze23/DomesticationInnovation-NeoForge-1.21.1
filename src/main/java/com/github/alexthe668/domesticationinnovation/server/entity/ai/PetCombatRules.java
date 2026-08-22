package com.github.alexthe668.domesticationinnovation.server.entity.ai;

import com.github.alexthe668.domesticationinnovation.DomesticationMod;
import com.github.alexthe668.domesticationinnovation.server.entity.TameableUtils;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Monster;

import javax.annotation.Nullable;

/**
 * Shared combat-policy predicates for tamed pets, used by our owner-hurt
 * target goals, the {@code TargetGoal} pipeline and the brain-AI target
 * paths so all pet species follow the same rules.
 */
public class PetCombatRules {

    /**
     * Whether a pet is currently willing to start or keep fighting the given
     * target. When injured_pets_stop_fighting is enabled, a tamed pet at or
     * below injured_health_ratio of its max health backs out of combat so it
     * can retreat and heal - unless the target is a Monster or Iron Golem,
     * which would keep attacking the pet regardless.
     */
    public static boolean wantsToFight(LivingEntity pet, @Nullable LivingEntity target) {
        if (!DomesticationMod.CONFIG.injuredPetsStopFighting.get()) {
            return true;
        }
        if (target == null || !TameableUtils.isTamed(pet)) {
            return true;
        }
        if (target instanceof Monster || target instanceof IronGolem) {
            return true;
        }
        return pet.getHealth() > pet.getMaxHealth() * DomesticationMod.CONFIG.injuredHealthRatio.get();
    }
}
