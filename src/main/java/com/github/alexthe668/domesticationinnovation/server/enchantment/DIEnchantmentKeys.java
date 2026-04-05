package com.github.alexthe668.domesticationinnovation.server.enchantment;

import com.github.alexthe668.domesticationinnovation.DomesticationMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * In 1.21.1, enchantments are fully data-driven via JSON.
 * This class holds ResourceKey references to our enchantments defined in:
 *   data/domesticationinnovation/enchantment/*.json
 *
 * Behavior code checks for these keys via TameableUtils helper methods.
 * Enchantment properties (max level, rarity, cost) are defined in the JSON files.
 *
 * Replaces the old PetEnchantment class hierarchy and DIEnchantmentKeys.
 */
public final class DIEnchantmentKeys {

    // === Combat Enchantments ===
    public static final ResourceKey<Enchantment> CHAIN_LIGHTNING = key("chain_lightning");
    public static final ResourceKey<Enchantment> FROST_FANG = key("frost_fang");
    public static final ResourceKey<Enchantment> MAGNETIC = key("magnetic");
    public static final ResourceKey<Enchantment> SHADOW_HANDS = key("shadow_hands");
    public static final ResourceKey<Enchantment> BUBBLING = key("bubbling");
    public static final ResourceKey<Enchantment> WARPING_BITE = key("warping_bite");
    public static final ResourceKey<Enchantment> PSYCHIC_WALL = key("psychic_wall");
    public static final ResourceKey<Enchantment> INTIMIDATION = key("intimidation");

    // === Defense Enchantments ===
    public static final ResourceKey<Enchantment> HEALTH_BOOST = key("health_boost");
    public static final ResourceKey<Enchantment> FIREPROOF = key("fireproof");
    public static final ResourceKey<Enchantment> IMMUNITY_FRAME = key("immunity_frame");
    public static final ResourceKey<Enchantment> DEFLECTION = key("deflection");
    public static final ResourceKey<Enchantment> POISON_RESISTANCE = key("poison_resistance");
    public static final ResourceKey<Enchantment> BLAZING_PROTECTION = key("blazing_protection");
    public static final ResourceKey<Enchantment> VOID_CLOUD = key("void_cloud");
    public static final ResourceKey<Enchantment> DEFUSAL = key("defusal");
    public static final ResourceKey<Enchantment> TOTAL_RECALL = key("total_recall");
    public static final ResourceKey<Enchantment> HEALTH_SIPHON = key("health_siphon");

    // === Utility Enchantments ===
    public static final ResourceKey<Enchantment> SPEEDSTER = key("speedster");
    public static final ResourceKey<Enchantment> LINKED_INVENTORY = key("linked_inventory");
    public static final ResourceKey<Enchantment> SHEPHERD = key("herding");
    public static final ResourceKey<Enchantment> AMPHIBIOUS = key("amphibious");
    public static final ResourceKey<Enchantment> VAMPIRE = key("vampire");
    public static final ResourceKey<Enchantment> CHARISMA = key("charisma");
    public static final ResourceKey<Enchantment> DISK_JOCKEY = key("disc_jockey");
    public static final ResourceKey<Enchantment> ORE_SCENTING = key("ore_scenting");
    public static final ResourceKey<Enchantment> GLUTTONOUS = key("gluttonous");
    public static final ResourceKey<Enchantment> TETHERED_TELEPORT = key("tethered_teleport");
    public static final ResourceKey<Enchantment> MUFFLED = key("muffled");
    public static final ResourceKey<Enchantment> HEALING_AURA = key("healing_aura");
    public static final ResourceKey<Enchantment> REJUVENATION = key("rejuvenation");

    // === Curse Enchantments ===
    public static final ResourceKey<Enchantment> UNDEAD_CURSE = key("undead_curse");
    public static final ResourceKey<Enchantment> INFAMY_CURSE = key("infamy_curse");
    public static final ResourceKey<Enchantment> BLIGHT_CURSE = key("blight_curse");
    public static final ResourceKey<Enchantment> IMMATURITY_CURSE = key("immaturity_curse");

    private static ResourceKey<Enchantment> key(String name) {
        return ResourceKey.create(Registries.ENCHANTMENT, ResourceLocation.fromNamespaceAndPath(DomesticationMod.MODID, name));
    }

    private DIEnchantmentKeys() {}
}
