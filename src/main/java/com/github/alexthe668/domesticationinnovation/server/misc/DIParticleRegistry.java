package com.github.alexthe668.domesticationinnovation.server.misc;

import com.github.alexthe668.domesticationinnovation.DomesticationMod;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class DIParticleRegistry {

    public static final DeferredRegister<ParticleType<?>> DEF_REG = DeferredRegister.create(BuiltInRegistries.PARTICLE_TYPE, DomesticationMod.MODID);

    public static final Supplier<SimpleParticleType> DEFLECTION_SHIELD = DEF_REG.register("deflection_shield", () -> new SimpleParticleType(false));
    public static final Supplier<SimpleParticleType> MAGNET = DEF_REG.register("magnet", () -> new SimpleParticleType(false));
    public static final Supplier<SimpleParticleType> ZZZ = DEF_REG.register("zzz", () -> new SimpleParticleType(false));
    public static final Supplier<SimpleParticleType> GIANT_POP = DEF_REG.register("giant_pop", () -> new SimpleParticleType(false));
    public static final Supplier<SimpleParticleType> SIMPLE_BUBBLE = DEF_REG.register("simple_bubble", () -> new SimpleParticleType(false));
    public static final Supplier<SimpleParticleType> VAMPIRE = DEF_REG.register("vampire", () -> new SimpleParticleType(false));
    public static final Supplier<SimpleParticleType> SNIFF = DEF_REG.register("sniff", () -> new SimpleParticleType(false));
    public static final Supplier<SimpleParticleType> PSYCHIC_WALL = DEF_REG.register("psychic_wall", () -> new SimpleParticleType(false));
    public static final Supplier<SimpleParticleType> INTIMIDATION = DEF_REG.register("intimidation", () -> new SimpleParticleType(false));
    public static final Supplier<SimpleParticleType> BLIGHT = DEF_REG.register("blight", () -> new SimpleParticleType(false));
    public static final Supplier<SimpleParticleType> LANTERN_BUGS = DEF_REG.register("lantern_bugs", () -> new SimpleParticleType(false));
}
