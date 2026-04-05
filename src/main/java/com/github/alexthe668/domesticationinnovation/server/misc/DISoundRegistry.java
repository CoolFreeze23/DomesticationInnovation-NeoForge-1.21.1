package com.github.alexthe668.domesticationinnovation.server.misc;

import com.github.alexthe668.domesticationinnovation.DomesticationMod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class DISoundRegistry {

    public static final DeferredRegister<SoundEvent> DEF_REG = DeferredRegister.create(BuiltInRegistries.SOUND_EVENT, DomesticationMod.MODID);

    public static final Supplier<SoundEvent> COLLAR_TAG = createSoundEvent("collar_tag");
    public static final Supplier<SoundEvent> MAGNET_LOOP = createSoundEvent("magnet_loop");
    public static final Supplier<SoundEvent> CHAIN_LIGHTNING = createSoundEvent("chain_lightning");
    public static final Supplier<SoundEvent> GIANT_BUBBLE_INFLATE = createSoundEvent("giant_bubble_inflate");
    public static final Supplier<SoundEvent> GIANT_BUBBLE_POP = createSoundEvent("giant_bubble_pop");
    public static final Supplier<SoundEvent> PET_BED_USE = createSoundEvent("pet_bed_use");
    public static final Supplier<SoundEvent> DRUM = createSoundEvent("drum");
    public static final Supplier<SoundEvent> PSYCHIC_WALL = createSoundEvent("psychic_wall");
    public static final Supplier<SoundEvent> PSYCHIC_WALL_DEFLECT = createSoundEvent("psychic_wall_deflect");
    public static final Supplier<SoundEvent> BLAZING_PROTECTION = createSoundEvent("blazing_protection");

    private static Supplier<SoundEvent> createSoundEvent(String soundName) {
        return DEF_REG.register(soundName,
                () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(DomesticationMod.MODID, soundName)));
    }
}
