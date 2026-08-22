package com.github.alexthe668.domesticationinnovation.server.misc;

import com.github.alexthe668.domesticationinnovation.DomesticationMod;
import com.github.alexthe668.domesticationinnovation.server.misc.effect.DrunkEffect;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Mob effects added by the port (upstream Domestication Innovation ships none).
 *
 * DRUNK backs the Chaos and Violent enchantments - see {@link DrunkEffect}.
 * DeferredHolder implements Holder&lt;MobEffect&gt;, so these constants plug
 * straight into hasEffect / MobEffectInstance.
 */
public class DIEffectRegistry {

    public static final DeferredRegister<MobEffect> DEF_REG = DeferredRegister.create(BuiltInRegistries.MOB_EFFECT, DomesticationMod.MODID);

    public static final DeferredHolder<MobEffect, MobEffect> DRUNK = DEF_REG.register("drunk", DrunkEffect::new);
}
