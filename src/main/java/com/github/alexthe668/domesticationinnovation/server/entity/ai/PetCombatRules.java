package com.github.alexthe668.domesticationinnovation.server.entity.ai;

import com.github.alexthe668.domesticationinnovation.DomesticationMod;
import com.github.alexthe668.domesticationinnovation.server.entity.TameableUtils;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Enemy;

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
     * below injured_health_ratio of its max health refuses to engage
     * DANGEROUS targets (hostile {@link Enemy} mobs and iron golems) so it
     * can flee and heal instead of fighting to the death. Harmless targets
     * pose no threat to the injured pet, so those fights are still allowed.
     */
    public static boolean wantsToFight(LivingEntity pet, @Nullable LivingEntity target) {
        if (!DomesticationMod.CONFIG.injuredPetsStopFighting.get()) {
            return true;
        }
        if (target == null || !TameableUtils.isTamed(pet)) {
            return true;
        }
        float health = pet.getHealth();
        float maxHealth = pet.getMaxHealth();
        boolean injured = health < maxHealth
                && health <= maxHealth * DomesticationMod.CONFIG.injuredHealthRatio.get().floatValue();
        if (!injured) {
            return true;
        }
        return !(target instanceof Enemy || target instanceof IronGolem);
    }
}
