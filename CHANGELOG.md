# Changelog

## 2.0.0-1.21.1 — 2026-08-22

First packaged release of the NeoForge 1.21.1 port.

The baseline is alex_the_668's Domestication Innovation **1.7.1 for Forge 1.20.1**. Everything below
landed after the initial port commits: a line-by-line parity audit against that upstream, fixes for
what the audit found, a performance pass, features adopted from two other community rewrites of the
same mod, and a headless test suite that keeps all of it honest.

---

### Fixed — parity gaps found by the upstream audit

The port's core mechanics were faithful; the wiring around them was not. Every item here was silently
broken before this release.

- **All 36 crafting recipes could not load.** They sat in the 1.20 `recipes/` directory in 1.20 JSON
  format. Migrated to `recipe/` with 1.21.1 syntax; `c:` convention tags replace the old `forge:` ones.
- **No pet enchantment could ever come from the enchanting table, a librarian, or chest loot.** The
  port shipped none of the vanilla `minecraft:enchantment` tags upstream relies on. Now ships
  `in_enchanting_table`, `tradeable`, `on_random_loot`, `treasure`, `curse` and `double_trade_price`,
  reproducing upstream's per-enchantment acquisition rules. Curses are real curses again — red
  tooltip, grindstone-proof.
- **The entire enchantment incompatibility matrix was missing.** Upstream's compatibility rules are
  now encoded as 32 `exclusive_set` tags, so conflicting enchantments can no longer stack from the
  table, an anvil, or a trade.
- **Pet data never reached clients that started tracking a pet.** Enchantment visuals and state were
  missing after every relog and whenever a pet came back into view. Pet data is now pushed on
  start-tracking, and payloads are skipped when nothing changed.
- **Animal Tamer collar trades ignored their own rules.** The rewritten trade had dropped rarity
  weighting, cost windows, the experience budget, compatibility filtering and the treasure/curse
  exclusion — selling loot-only enchantments at maximum level for scraps. Upstream's algorithm is
  restored.
- **Muffled hooked the wrong `gameEvent` overload**, so sculk sensors still heard muffled pets through
  the two-argument path.
- **Pets could teleport into lava, fire, cactus and magma.** The follow-goal destination check had
  been reduced to "is the block below not air"; the walkable path-type check is back, and teleport
  destinations are chunk-ticketed again.
- **Amphibious lost its water-teleport allowance**, stranding aquatic pets on shore.
- **Frogs and axolotls lost vanilla behavior**: their temptation items were replaced instead of merged
  (slime balls no longer tempted frogs), and tamed frogs stopped hunting entirely.
- **Sitting pets stood up on chunk reload** when the trinary command system was disabled.
- **Alex's Mobs pets had stay and follow inverted** — that mod numbers its commands differently. The
  compatibility path now translates in both directions, scoped to Alex's Mobs only so Ice and Fire
  dragons are not mistranslated.
- **The Wayward Lantern leaked permanently force-loaded chunks** and clobbered player forceloads by
  using the vanilla API; it now owns a proper ticket controller with stale-ticket cleanup.
- **Enchanted books were missing from the creative tab.**
- **The Command Drum's "wander" order force-stood vanilla pets** that had been told to sit by hand.
- **Feather on a Stick and the Deed of Ownership never changed model.** Their predicates were renamed
  in code but not in the model files.
- **Sneak-inspecting a pet lost its name** and no longer suppressed the vanilla nametag.
- **Ore Scenting outlined every ore in white** — the per-ore colour registry had become dead code.
- **Petshops**: the Animal Tamer spawned at level 2 and could never offer Novice trades; template air
  was not placed, so shop interiors could generate full of terrain; the villager always wore the
  plains outfit regardless of biome; the mob-list cache was not thread-safe.
- Retaliation fire ignored Fire Protection; the identical-collar guard was missing, so re-applying a
  collar consumed it; anvil collar merges lost upstream's sticky-abort behavior; rabbits no longer
  started tamed in "stay"; zombie pets kept their old command; loot books could roll maximum level.

### Fixed — crashes and silent data loss

- **The mod crashed every dedicated server**, and had since the first port commit. The constructor's
  `dist == CLIENT ? new ClientProxy() : ...` made the JVM verifier load the client class at
  verification time, which the server's class stripping rejects. All client wiring now lives behind
  common-typed signatures in `DIClientFactory`. This port now boots headless.
- **Bed respawns, Total Recall and zombie pets silently destroyed collars.** They rebuild the pet from
  an NBT snapshot, and attachment data is not part of that snapshot — so every rebuilt pet came back
  with no collar, no enchantments and no bed link. Snapshots now carry pet data explicitly.
- **A collar and pet duplication chain.** A pet whose chunk unloaded left a lantern retrieval request
  holding a full snapshot, and nothing purged that request when the pet later died, was recalled, or
  was bucketed — so the lantern could rebuild a second copy of the pet, with a second collar. Requests
  are now purged on all three paths, one lantern claims a request at a time, and snapshot respawn only
  happens once the chunks are genuinely loaded and the pet is genuinely gone.
- **Health Boost pets healed to full on every save and load.** Transient modifiers are not saved,
  vanilla clamps health before the mod reapplies them, and the fraction restore then scaled that
  clamped value back up. Health is now preserved exactly across a reload in both directions.
- **`ModifedToBeTameable.setTame(boolean)` was an `AbstractMethodError` waiting to happen** on wolves,
  cats and parrots — vanilla's method takes two arguments, so the interface method had no
  implementation on those species. It now defaults to the vanilla call.
- Live attachment tags were handed to network packets and encoded off-thread, racing game-thread
  writes; snapshots are copied first.
- The enchantment cache keyed entities by identity, but entity equality is id-based — so a client
  entity and the integrated server's entity with the same id shared one cache entry.

### Added — nine new pet enchantments

| Enchantment | Max | Effect |
|---|---|---|
| **Sonic Boom** | I | Every ten seconds your pet fires a Warden-style blast at its target for 10 damage, or at a whole crowd when hostiles swarm it |
| **Violent** | I | Every hit rolls a wheel: extra damage, poison, ignition, a triple debuff, a percentage-based hit, drunkenness — or, one time in a hundred, instant death |
| **Chaos** | I | Anything that hurts your pet gets **Drunk** and starts attacking whatever is nearest, including its own kind |
| **Paralysis** | III | Attackers are crippled with heavy Slowness, Mining Fatigue and Weakness |
| **Share** | III | Damage dealt to your pet splashes 30% onto every hostile nearby |
| **Tough** | IV | Straight armor and knockback resistance for the pet |
| **Insight** | III | In the dark, hostiles around your pet glow through walls |
| **XP Transfer** | I | Your pet vacuums up experience orbs and the experience goes to you |
| **Night Vision** | I | Standing near your pet grants Night Vision |

Each ships with acquisition rules, exclusivity, a config toggle, loot placements (ancient cities,
mineshafts, desert pyramids, end city treasure) and full English and Brazilian Portuguese text. The
new **Drunk** effect comes with its own icon.

### Added — datapack-driven taming and transformations

Two new datapack registries let a pack make **any mob tameable or convertible with a JSON file** —
no code:

```
data/<namespace>/domesticationinnovation/taming/<name>.json
data/<namespace>/domesticationinnovation/transformation/<name>.json
```

Taming entries take an entity (id, list or `#tag`), an ingredient, an optional chance (default 0.33)
and an optional NBT predicate. Transformation entries convert one mob into another on use, carrying
the pet's collar across. The field shapes are deliberately compatible with existing community packs.

Mobs tamed this way become **full pets** — commands, collars, enchantments, beds, lanterns, deeds —
through a generic owner layer, with defensive AI surgery that strips hostile targeting and installs
sit, follow and owner-defense goals. Two entity-type tags govern it: `taming_blacklist` (never
tameable; ships with the ender dragon, wither, warden, villagers and traders) and `uses_brain_ai`
(skip goal surgery). **Ocelot taming** ships as the single default entry.

### Added — trading, tooltips and integrations

- **Animal Tamers are now the pet-book vendor**, selling random pet enchantment books at every trade
  tier with vanilla-style pricing. **Librarians no longer sell pet books at all**, so their pools stop
  filling with enchantments that only work on collars.
- **Jade integration**: hovering your own pet shows its enchantments (curses in red) and the location
  of its bed. Both lines are individually toggleable in Jade's settings.
- **Enchanted books describe themselves** when no dedicated descriptions mod is installed.
- **Repurposed Structures**: petshops generate in all eleven of its village types when that mod is
  present, with a working per-village cap.
- **Enchanting Infuser** and the `c:enchantables` convention both accept collar tags.
- **Recall orbs** can be picked as a spawn egg carrying the captured pet, and the tooltip renders a
  live 3D preview of what is inside.

### Changed — behavior and quality of life

- **Collars now drop when a pet dies for good**, keeping their name and enchantments. Curse of
  Vanishing destroys the collar instead; Curse of Binding refuses to let a bound pet's collar be
  swapped.
- **Collar tags are far better at the enchanting table** — enchantability raised from 1 to 10, so
  multi-enchantment offers actually appear.
- **Injured pets disengage.** Below a configurable health fraction, pets stop fighting hostiles and
  iron golems so they can retreat instead of dying.
- **Pet beds belong to one pet at a time**, with claims validated on both sides and released properly
  when a pet dies for good.
- **Bed-anchored roaming**: pets in wander mode can be kept within a radius of their bed.
- **Sneaking bypasses pet interactions**, so command cycling no longer eats clicks meant for other
  mods. Collar application still works while sneaking.
- **Pet data survives axolotl bucketing** and the rotten apple / sinister carrot horse conversions.
- **The Infamy Curse draws every hostile type**, not just classic monsters (configurable).
- **In-game config screen** with proper labels and tooltips for every option, in English and
  Brazilian Portuguese.
- Thirteen new config options: `collar_drops_on_death`, `sneak_bypasses_pet_interactions`,
  `collar_tag_enchantability`, `tamer_sells_books`, `lantern_request_timeout_ticks`,
  `lantern_crash_safe_respawn`, `exclusive_pet_beds`, `infamy_curse_aggros_all_hostiles`,
  `data_driven_taming`, `injured_pets_stop_fighting`, `injured_health_ratio`,
  `disable_pet_teleportation`, `pet_roaming_radius`.

### Performance

- Enchantment lookups are decoded once per pet into a non-serialized attachment, replacing repeated
  NBT string parsing — roughly twenty tag scans per pet per tick previously.
- Pets with no collar skip enchantment work entirely, in both tick and render paths.
- Reading pet data no longer attaches (and saves) an empty tag to every living entity it touches.
- Server-only cooldowns no longer broadcast a packet every tick, and unchanged payloads are dropped.
- Intimidation's area scan runs on its cooldown instead of every tick; healer scans exit early;
  amphibious pathing respects the existing recalculation throttle.
- The jukebox follower caches its decoded disc, the zombie overlay render type is built once, and the
  world data no longer marks itself dirty on every read.

### Testing

A headless GameTest suite ships with the source: **19 tests**, run with

```
gradlew runGameTestServer
```

covering enchantment effects (attribute math, insight, retaliation, share splash, sonic boom, the
violent wheel), player interactions through real event dispatch (datapack taming, collar application,
curse refusal, command cycling, transformations), and the collar lifecycle (death drops, vanishing,
bed claiming, dawn respawn with enchantments intact, exclusive beds, zombie pets).

### Known issues

- **Worlds upgraded from the 1.20.1 version lose their pet data.** Upstream stored it through Citadel;
  this port uses NeoForge attachments and does not read the old format, so collars, enchantments and
  bed links from a 1.20.1 world will be gone. New worlds are unaffected.
- **Tough's knockback resistance maxes out at level I** — vanilla clamps that attribute at 1.0 and the
  enchantment grants well past it per level.
- **Enchanted books found in loot never roll their maximum level.** This matches upstream.
- **Ocelots tamed through the datapack path stand while sitting** — their model has no sitting pose.
- The Repurposed Structures files are only read when that mod is installed; without it they are inert.
