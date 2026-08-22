package com.github.alexthe668.domesticationinnovation.server.misc.effect;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.monster.Monster;

import java.util.List;

/**
 * Drunk - a hostile mob afflicted with this stumbles into fights with its own
 * kind: every effect tick it picks a random other Monster nearby and attacks
 * it instead of whatever it was doing.
 *
 * Applied by the Chaos enchantment (120 ticks, when the pet is hurt) and by
 * one of Violent's on-hit rolls (100 ticks). Harmless on non-Monster mobs.
 *
 * Unlike the mod this behavior is adopted from, the confusion runs inside the
 * effect itself (no per-mob global tick scan) and the "?" marker reuses the
 * vanilla angry-villager particle instead of a custom one.
 */
public class DrunkEffect extends MobEffect {

    /** Wine-dark potion color. */
    private static final int DRUNK_COLOR = 0x660033;
    /** How far the drunk mob looks for a new victim, in blocks. */
    private static final double RETARGET_RANGE = 10.0D;

    public DrunkEffect() {
        super(MobEffectCategory.HARMFUL, DRUNK_COLOR);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return duration % 5 == 0;
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (entity.level() instanceof ServerLevel serverLevel && entity instanceof Monster drunk) {
            // "?"-style confusion marker above the head; every other effect
            // tick, so one particle per ~10 game ticks
            if (serverLevel.getGameTime() % 10 < 5) {
                serverLevel.sendParticles(ParticleTypes.ANGRY_VILLAGER,
                        drunk.getX(), drunk.getY() + drunk.getBbHeight() + 0.4D, drunk.getZ(),
                        1, 0, 0, 0, 0);
            }
            List<Monster> victims = serverLevel.getEntitiesOfClass(Monster.class,
                    drunk.getBoundingBox().inflate(RETARGET_RANGE),
                    EntitySelector.NO_SPECTATORS.and(e -> e != drunk && e.isAlive()));
            if (!victims.isEmpty()) {
                retarget(drunk, victims.get(drunk.getRandom().nextInt(victims.size())));
            }
        }
        return true;
    }

    private static void retarget(Monster drunk, Monster victim) {
        // Brain-driven monsters (piglins, zoglins, warden...) ignore plain
        // setTarget, so poke the attack-target memory when the brain has one
        Brain<?> brain = drunk.getBrain();
        if (brain.checkMemory(MemoryModuleType.ATTACK_TARGET, MemoryStatus.REGISTERED)) {
            brain.setMemory(MemoryModuleType.ATTACK_TARGET, victim);
        }
        drunk.setTarget(victim);
    }
}
