package com.github.alexthe668.domesticationinnovation.server.misc.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.TagParser;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.Optional;

/**
 * A datapack-defined taming rule, loaded from
 * {@code data/<namespace>/domesticationinnovation/taming/*.json}.
 *
 * JSON shape (kept field-compatible with other datapack-taming mods so packs
 * are portable):
 * <pre>
 * {
 *   "entities": "minecraft:ocelot" | ["a", "b"] | "#some/entity_tag",
 *   "items": vanilla Ingredient ({"item": ...} / {"tag": ...} / [...]),
 *   "chance": 0.33,                       // optional, default 0.33
 *   "required_data": "{Variant: 3}"       // optional SNBT, subset-matched
 * }
 * </pre>
 *
 * {@code required_data} is compared as an NBT subset against the mob's full
 * saved data, so entries can target variants (colors, castes, mod flags).
 */
public record TamingDefinition(HolderSet<EntityType<?>> entities,
                               Ingredient items,
                               float chance,
                               Optional<CompoundTag> requiredData) {

    /**
     * SNBT-string-backed CompoundTag codec shared by both datapack definition
     * records; a malformed string fails the file load with the parser message
     * instead of silently matching nothing.
     */
    public static final Codec<CompoundTag> SNBT_CODEC = Codec.STRING.comapFlatMap(
            snbt -> {
                try {
                    return DataResult.success(TagParser.parseTag(snbt));
                } catch (Exception e) {
                    return DataResult.error(() -> "Invalid SNBT in required_data: " + e.getMessage());
                }
            },
            CompoundTag::toString);

    public static final Codec<TamingDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            RegistryCodecs.homogeneousList(Registries.ENTITY_TYPE).fieldOf("entities").forGetter(TamingDefinition::entities),
            Ingredient.CODEC_NONEMPTY.fieldOf("items").forGetter(TamingDefinition::items),
            Codec.FLOAT.optionalFieldOf("chance", 0.33F).forGetter(TamingDefinition::chance),
            SNBT_CODEC.optionalFieldOf("required_data").forGetter(TamingDefinition::requiredData)
    ).apply(instance, TamingDefinition::new));

    public boolean appliesTo(Mob mob) {
        return this.entities.contains(mob.getType().builtInRegistryHolder());
    }

    /**
     * Subset-match of {@code required_data} against the mob's saved NBT. Only
     * serializes the mob when the definition actually carries a predicate -
     * a full save per right-click is fine for a match candidate, not for the
     * common no-predicate case.
     */
    public boolean matchesRequiredData(Mob mob) {
        if (this.requiredData.isEmpty()) {
            return true;
        }
        CompoundTag saved = new CompoundTag();
        mob.saveWithoutId(saved);
        return NbtUtils.compareNbt(this.requiredData.get(), saved, true);
    }
}
