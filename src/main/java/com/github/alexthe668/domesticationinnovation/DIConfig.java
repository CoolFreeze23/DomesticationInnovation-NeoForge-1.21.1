package com.github.alexthe668.domesticationinnovation;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

import java.util.HashMap;
import java.util.Map;

public class DIConfig {

    // All enchantment names for config generation - must be before the static init block
    private static final String[] ENCHANTMENT_NAMES = {
            "health_boost", "fireproof", "immunity_frame", "deflection", "poison_resistance",
            "chain_lightning", "speedster", "frost_fang", "magnetic", "linked_inventory",
            "total_recall", "health_siphon", "bubbling", "herding", "amphibious",
            "vampire", "void_cloud", "charisma", "shadow_hands", "disc_jockey",
            "defusal", "warping_bite", "ore_scenting", "gluttonous", "psychic_wall",
            "intimidation", "tethered_teleport", "muffled", "blazing_protection", "healing_aura",
            "rejuvenation",             "undead_curse", "infamy_curse", "blight_curse", "immaturity_curse"
    };

    public static final DIConfig INSTANCE;
    public static final ModConfigSpec CONFIG_SPEC;

    static {
        final Pair<DIConfig, ModConfigSpec> specPair = new ModConfigSpec.Builder().configure(DIConfig::new);
        INSTANCE = specPair.getLeft();
        CONFIG_SPEC = specPair.getRight();
    }

    // General
    public final ModConfigSpec.BooleanValue trinaryCommandSystem;
    public final ModConfigSpec.BooleanValue tameableAxolotl;
    public final ModConfigSpec.BooleanValue tameableHorse;
    public final ModConfigSpec.BooleanValue tameableFox;
    public final ModConfigSpec.BooleanValue tameableRabbit;
    public final ModConfigSpec.BooleanValue tameableFrog;
    public final ModConfigSpec.BooleanValue swingThroughPets;
    public final ModConfigSpec.BooleanValue rottenApple;
    public final ModConfigSpec.BooleanValue petBedRespawns;
    public final ModConfigSpec.BooleanValue collarTag;
    public final ModConfigSpec.BooleanValue rabbitsScareRavagers;
    public final ModConfigSpec.BooleanValue animalTamerVillager;
    public final ModConfigSpec.IntValue petstoreVillageWeight;

    // Loot
    public final ModConfigSpec.BooleanValue petCurseEnchantmentsLootOnly;
    public final ModConfigSpec.DoubleValue sinisterCarrotLootChance;
    public final ModConfigSpec.DoubleValue bubblingLootChance;
    public final ModConfigSpec.DoubleValue vampirismLootChance;
    public final ModConfigSpec.DoubleValue voidCloudLootChance;
    public final ModConfigSpec.DoubleValue oreScentingLootChance;
    public final ModConfigSpec.DoubleValue muffledLootChance;
    public final ModConfigSpec.DoubleValue blazingProtectionLootChance;

    // ATM10 Integration
    public final ModConfigSpec.DoubleValue enchantPowerMultiplier;
    public final ModConfigSpec.BooleanValue useConventionOreTags;

    // Per-enchantment enable/disable
    private final Map<String, ModConfigSpec.BooleanValue> enabledEnchantments = new HashMap<>();

    public DIConfig(final ModConfigSpec.Builder builder) {
        builder.push("general");
        trinaryCommandSystem = builder.comment("true if wolves, cats, parrots, foxes, axolotls, etc can be set to wander, sit or follow").define("trinary_command_system", true);
        tameableAxolotl = builder.comment("true if axolotls are fully tameable (must be tamed with tropical fish)").define("tameable_axolotls", true);
        tameableHorse = builder.comment("true if horses, donkeys, llamas, etc can be given enchants, beds, etc").define("tameable_horse", true);
        tameableFox = builder.comment("true if foxes are fully tameable (must be tamed via breeding)").define("tameable_fox", true);
        tameableRabbit = builder.comment("true if rabbits are fully tameable (must be tamed with hay blocks)").define("tameable_rabbit", true);
        tameableFrog = builder.comment("true if frogs are fully tameable (must be tamed with spider eyes)").define("tameable_frog", true);
        swingThroughPets = builder.comment("true if attacks pass through pets to hit mobs behind them").define("swing_through_pets", true);
        rottenApple = builder.comment("true if apples can turn into rotten apples on despawn").define("rotten_apple", true);
        petBedRespawns = builder.comment("true if mobs can respawn in pet beds the next morning after death").define("pet_bed_respawns", true);
        collarTag = builder.comment("true if collar tag functionality is enabled. Disabling removes the only way to enchant mobs!").define("collar_tags", true);
        rabbitsScareRavagers = builder.comment("true if rabbits scare ravagers").define("rabbits_scare_ravagers", true);
        animalTamerVillager = builder.comment("true if animal tamer villagers are enabled. Their work station is a pet bed").define("animal_tamer_villager", true);
        petstoreVillageWeight = builder.comment("the spawn weight of the pet store in villages, set to 0 to disable").defineInRange("petstore_village_weight", 17, 0, 1000);
        builder.pop();

        builder.push("loot");
        petCurseEnchantmentsLootOnly = builder.comment("true if pet curse enchantments only appear in loot, not the enchanting table").define("pet_curse_enchantments_loot_only", true);
        sinisterCarrotLootChance = builder.comment("chance of woodland mansion loot containing sinister carrot").defineInRange("sinister_carrot_loot_chance", 0.3D, 0.0, 1.0D);
        bubblingLootChance = builder.comment("chance of buried treasure loot containing Bubbling book").defineInRange("bubbling_loot_chance", 0.65D, 0.0, 1.0D);
        vampirismLootChance = builder.comment("chance of woodland mansion loot containing Vampire book").defineInRange("vampirism_loot_chance", 0.22D, 0.0, 1.0D);
        voidCloudLootChance = builder.comment("chance of end city loot containing Void Cloud book").defineInRange("void_cloud_loot_chance", 0.19D, 0.0, 1.0D);
        oreScentingLootChance = builder.comment("chance of mineshaft loot containing Ore Scenting book").defineInRange("ore_scenting_loot_chance", 0.15D, 0.0, 1.0D);
        muffledLootChance = builder.comment("chance of ancient city loot containing Muffled book").defineInRange("muffled_loot_chance", 0.19D, 0.0, 1.0D);
        blazingProtectionLootChance = builder.comment("chance of nether fortress loot containing Blazing Protection book").defineInRange("blazing_protection_loot_chance", 0.2D, 0.0, 1.0D);
        builder.pop();

        builder.push("atm10_integration");
        enchantPowerMultiplier = builder.comment("Multiplier for pet enchantment effectiveness. Increase for lategame modpacks like ATM10").defineInRange("enchant_power_multiplier", 1.0D, 0.1D, 5.0D);
        useConventionOreTags = builder.comment("Use c:ores convention tags for Ore Scenting (supports modded ores in ATM10)").define("use_convention_ore_tags", true);
        builder.pop();

        builder.push("enchantments");
        for (String name : ENCHANTMENT_NAMES) {
            String configName = name + "_enabled";
            enabledEnchantments.put(name, builder.comment("true if " + name.replace("_", " ") + " enchant is enabled").define(configName, true));
        }
        builder.pop();
    }

    public boolean isEnchantEnabled(String enchantmentName) {
        ModConfigSpec.BooleanValue entry = enabledEnchantments.get(enchantmentName);
        return entry == null || entry.get();
    }
}
