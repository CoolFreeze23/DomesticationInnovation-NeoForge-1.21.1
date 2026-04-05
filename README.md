# Domestication Innovation — NeoForge 1.21.1

A port of [Domestication Innovation](https://www.curseforge.com/minecraft/mc-mods/domestication-innovation) (originally by alex_the_668) from Forge 1.20.1 to **NeoForge 1.21.1**.

This mod expands tameable mobs in Minecraft with a trinary command system (wander / stay / follow), new taming targets (Axolotl, Fox, Rabbit, Frog), pet enchantments via collar tags, and quality-of-life blocks like the Command Drum, Pet Bed, and Wayward Lantern. It also adds Animal Tamer villagers and petshop structures to villages.

## Requirements

- **Minecraft** 1.21.1
- **NeoForge** 21.1.77+
- **Java** 21

## Building

```bash
# Clone the repo
git clone https://github.com/CoolFreeze23/DomesticationInnovation-NeoForge-1.21.1.git
cd DomesticationInnovation-NeoForge-1.21.1

# Build (Linux / macOS)
./gradlew build

# Build (Windows)
gradlew.bat build
```

The built jar will be in `build/libs/`.

## Mod Compatibility

- Works with other mods that add tameable mobs (e.g. Alex's Mobs).
- Includes reflection-based compatibility for **Ice and Fire** dragons — the Command Drum, Wayward Lantern, and Pet Bed will use Ice and Fire's native `setCommand`/`getCommand` system when available.

## License

This is an unofficial port. All original credit goes to [alex_the_668](https://github.com/alex-the-668) for the original Domestication Innovation mod.
