package com.github.alexthe668.domesticationinnovation.server.entity;

import com.github.alexthe668.domesticationinnovation.DomesticationMod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.schedule.Activity;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class DIActivityRegistry {

    public static final DeferredRegister<Activity> DEF_REG = DeferredRegister.create(BuiltInRegistries.ACTIVITY, DomesticationMod.MODID);

    public static final Supplier<Activity> AXOLOTL_FOLLOW = DEF_REG.register("axolotl_follow", () -> new Activity("axolotl_follow"));
    public static final Supplier<Activity> AXOLOTL_STAY = DEF_REG.register("axolotl_stay", () -> new Activity("axolotl_stay"));
    public static final Supplier<Activity> FROG_FOLLOW = DEF_REG.register("frog_follow", () -> new Activity("frog_follow"));
    public static final Supplier<Activity> FROG_STAY = DEF_REG.register("frog_stay", () -> new Activity("frog_stay"));
}
