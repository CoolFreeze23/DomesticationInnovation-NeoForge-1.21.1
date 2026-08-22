package com.github.alexthe668.domesticationinnovation.gametest;

import com.github.alexthe668.domesticationinnovation.DomesticationMod;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * GameTest registration + scaffolding smoke test, mirroring the owner's
 * OreSpawn suite idioms. Registration is purely annotation-driven:
 * NeoForge scans mod classes for {@link GameTestHolder} (no
 * RegisterGameTestsEvent, no mods.toml entry needed) — the holder value
 * supplies both the test batch namespace and the template namespace.
 *
 * Template names are paths within the domesticationinnovation namespace
 * ("platform" -> domesticationinnovation:platform ->
 * data/domesticationinnovation/structure/platform.nbt);
 * {@link PrefixGameTestTemplate}(false) removes only the class-name prefix.
 *
 * The suite only runs when the namespace is enabled via the
 * {@code neoforge.enabledGameTestNamespaces} system property, which the
 * client and gameTestServer runs set in build.gradle.
 */
@GameTestHolder(DomesticationMod.MODID)
@PrefixGameTestTemplate(false)
public class DIGameTests {

    /**
     * Scaffolding smoke: proves the gametest source set, the platform
     * arena template, and the {@code runGameTestServer} task work
     * end-to-end before any real tests are written. Spawns a wolf on
     * the 9x9 stone platform and succeeds once it is present in the
     * structure bounds.
     */
    @GameTest(template = "platform")
    public void wolf_spawns_on_platform(GameTestHelper helper) {
        helper.spawn(EntityType.WOLF, new BlockPos(4, 2, 4));
        helper.succeedWhen(() -> helper.assertEntityPresent(EntityType.WOLF));
    }
}
