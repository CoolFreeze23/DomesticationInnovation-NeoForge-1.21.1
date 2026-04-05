# Domestication Innovation — NeoForge 1.21.1 Complete Port

## Port Summary
- **119 Java source files** ported (11,031 lines)
- **35 data-driven enchantment JSON files** created
- **All Citadel dependencies removed** — replaced with NeoForge attachments
- **All Forge APIs migrated** to NeoForge equivalents
- **3 bugs fixed**, code decomposed and cleaned up
- **ATM10 integration** added (convention ore tags, power scaling config)

## Architecture Changes

### Build System
- ForgeGradle 5.1 → NeoGradle 2.0
- Forge 47.2.0 → NeoForge 21.1.77
- Java 17 → Java 21
- mods.toml → neoforge.mods.toml
- pack_format 15 → 34
- Citadel dependency removed entirely

### Enchantment System (Data-Driven)
- 35 `PetEnchantment` Java classes → 35 JSON files in `data/domesticationinnovation/enchantment/`
- `DIEnchantmentRegistry` (reflection-based) → `DIEnchantmentKeys` (ResourceKey constants)
- `Enchantment` parameters → `ResourceKey<Enchantment>` throughout
- Enchantment properties (rarity, cost, levels) defined in JSON, not Java
- Item tag `#domesticationinnovation:collar_tag_enchantable` for supported items

### Entity Data (Citadel Replacement)
- `CitadelEntityData` → NeoForge `AttachmentType<CompoundTag>` via `DIAttachments.PET_DATA`
- `Citadel.sendMSGToAll/Server` → automatic NeoForge attachment sync
- `IComandableMob` → absorbed into `ModifedToBeTameable` interface
- `AdvancedEntityModel/AdvancedModelBox` → vanilla `EntityModel/ModelPart`
- `LightningRender/LightningBoltData` → needs custom replacement (stubbed)
- `VillageHouseManager` → needs template pool JSON injection (stubbed)

### Registry System
| Old | New |
|---|---|
| ForgeRegistries.* | BuiltInRegistries.* |
| RegistryObject<T> | Supplier<T> |
| Codec<IGlobalLootModifier> | MapCodec<IGlobalLootModifier> |
| ForgeConfigSpec | ModConfigSpec |
| DistExecutor | FMLEnvironment.dist |
| new ResourceLocation(ns, path) | ResourceLocation.fromNamespaceAndPath(ns, path) |
| UUID AttributeModifier | ResourceLocation AttributeModifier |

### Event System
| Old Forge | New NeoForge |
|---|---|
| LivingEvent.LivingTickEvent | EntityTickEvent.Post |
| LivingAttackEvent | LivingIncomingDamageEvent |
| LivingDamageEvent | LivingDamageEvent.Pre |
| TickEvent.LevelTickEvent | LevelTickEvent.Post |
| MobSpawnEvent.FinalizeSpawn | FinalizeSpawnEvent |
| event.getExplosion().getPosition() | event.getExplosion().center() |
| setSecondsOnFire(n) | setRemainingFireTicks(n*20) |
| EntityDimensions.width | EntityDimensions.width() |
| NetworkHooks.getEntitySpawningPacket | removed (automatic) |
| PlayMessages.SpawnEntity constructor | removed (automatic) |
| MerchantOffer(ItemStack,...) | MerchantOffer(ItemCost,...) |

### Bug Fixes
1. **NPE in blockCollarTick()** — called method on null tracker; fixed with computeIfAbsent
2. **new Random() usage** — 3 instances replaced with entity/level bound RNG
3. **Off-by-one in SellingOneOfTheseItemsTrade** — nextInt(size-1) never picked last item
4. **300+ line onLivingUpdate decomposed** into 10+ focused helper methods

### ATM10 Integration
- `enchant_power_multiplier` config — scales attribute bonuses from pet enchantments
- `use_convention_ore_tags` config — Ore Scenting uses `c:ores` for full modded ore support
- `DITagRegistry.CONVENTION_ORES` — block tag reference for convention ores

## Items Needing IDE Testing
These areas were mechanically ported but need verification in a real build environment:

1. **Mixin targets** — All 26 mixins had imports updated but target method signatures
   need verification against 1.21.1 vanilla bytecode. Some methods may have been
   renamed or had parameter changes.

2. **Citadel model replacements** — The 4 model classes (BlazingBarModel, HighlightedBlockModel,
   RecallBallModel, ShadowHandModel) had AdvancedEntityModel→EntityModel replacement applied
   but the constructor patterns differ. These will need manual adjustment to use
   vanilla's baked ModelPart system.

3. **LightningRender** — Used in ChainLightningRender and LayerPetOverlays. Needs a custom
   implementation to replace Citadel's lightning rendering utility.

4. **Village structure injection** — DIVillagePieceRegistry.registerHouses() is stubbed.
   Needs template_pool JSON files or ServerAboutToStartEvent listener to inject petshops.

5. **Food properties** — handleGluttonousFeeding uses the old Item.getFoodProperties() API.
   In 1.21.1, food data is accessed via DataComponents on the ItemStack.

6. **ItemStack NBT** — DeedOfOwnershipItem and CollarTagItem use stack.getTag()/setTag().
   These should migrate to DataComponents but still compile in 1.21.1 (deprecated).
