package com.github.alexthe668.domesticationinnovation.server.misc.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.Optional;

/**
 * A datapack-defined mob-to-mob conversion, loaded from
 * {@code data/<namespace>/domesticationinnovation/transformation/*.json}.
 *
 * JSON shape (field-compatible with other datapack-transformation mods):
 * <pre>
 * {
 *   "target_entity": "minecraft:horse" | [...] | "#tag",
 *   "trigger_item": vanilla Ingredient,
 *   "result_entity": "minecraft:zombie_horse",
 *   "sound": "minecraft:entity.zombie.infect",   // optional here; defaults
 *                                                // to the zombie infect sound
 *   "required_data": "{Tame: 1b}"                // optional SNBT subset
 * }
 * </pre>
 *
 * No transformation entries ship by default: the Rotten Apple and Sinister
 * Carrot keep their code paths, and this registry exists for packs to add
 * their own conversion chains.
 */
public record TransformationDefinition(HolderSet<EntityType<?>> targetEntity,
                                       Ingredient triggerItem,
                                       EntityType<?> resultEntity,
                                       Optional<Holder<SoundEvent>> sound,
                                       Optional<CompoundTag> requiredData) {

    public static final Codec<TransformationDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            RegistryCodecs.homogeneousList(Registries.ENTITY_TYPE).fieldOf("target_entity").forGetter(TransformationDefinition::targetEntity),
            Ingredient.CODEC_NONEMPTY.fieldOf("trigger_item").forGetter(TransformationDefinition::triggerItem),
            BuiltInRegistries.ENTITY_TYPE.byNameCodec().fieldOf("result_entity").forGetter(TransformationDefinition::resultEntity),
            SoundEvent.CODEC.optionalFieldOf("sound").forGetter(TransformationDefinition::sound),
            TamingDefinition.SNBT_CODEC.optionalFieldOf("required_data").forGetter(TransformationDefinition::requiredData)
    ).apply(instance, TransformationDefinition::new));

    public boolean appliesTo(Mob mob) {
        return this.targetEntity.contains(mob.getType().builtInRegistryHolder());
    }

    /** Same lazy subset-match contract as {@link TamingDefinition#matchesRequiredData}. */
    public boolean matchesRequiredData(Mob mob) {
        if (this.requiredData.isEmpty()) {
            return true;
        }
        CompoundTag saved = new CompoundTag();
        mob.saveWithoutId(saved);
        return NbtUtils.compareNbt(this.requiredData.get(), saved, true);
    }
}
