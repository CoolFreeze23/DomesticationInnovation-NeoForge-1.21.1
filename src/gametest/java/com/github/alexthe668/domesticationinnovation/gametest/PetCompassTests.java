package com.github.alexthe668.domesticationinnovation.gametest;

import com.github.alexthe668.domesticationinnovation.DomesticationMod;
import com.github.alexthe668.domesticationinnovation.server.block.DIBlockRegistry;
import com.github.alexthe668.domesticationinnovation.server.block.PetBedBlockEntity;
import com.github.alexthe668.domesticationinnovation.server.entity.TameableUtils;
import com.github.alexthe668.domesticationinnovation.server.misc.PetCompassListPacket;
import com.github.alexthe668.domesticationinnovation.server.misc.PetCompassService;
import com.github.alexthe668.domesticationinnovation.server.misc.PetRecords;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

/**
 * Pet bed respawn timing and the pet compass. Owners are in-level ServerPlayers (the same build as
 * TamingInteractionTests): the compass resolves players and sends them packets, which a detached
 * mock can't take. Each test removes its player again.
 */
@GameTestHolder(DomesticationMod.MODID)
@PrefixGameTestTemplate(false)
public class PetCompassTests {

    /** Platform floor is the template's y=0 stone layer; entities stand at y=1. */
    private static final BlockPos BED_REL = new BlockPos(4, 1, 4);

    private static ServerPlayer owner(GameTestHelper helper, Vec3 relativePos) {
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(
                new GameProfile(UUID.randomUUID(), "di-compass-mock"), false);
        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                cookie.gameProfile(), cookie.clientInformation()) {
            @Override
            public boolean isSpectator() {
                return false;
            }
        };
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        player.setGameMode(GameType.SURVIVAL);
        Vec3 pos = helper.absoluteVec(relativePos);
        player.teleportTo(helper.getLevel(), pos.x, pos.y, pos.z, 0.0F, 0.0F);
        return player;
    }

    private static void removePlayer(ServerPlayer player) {
        player.getServer().getPlayerList().remove(player);
    }

    /**
     * The bed now checks every second whether a new day has begun since its pet died. Here the clock
     * jumps from the death straight to a later noon, never passing time-of-day 1 (the only moment the
     * old check looked at), and the wolf must still come back. Its name must come back as plain
     * text: as a translatable, "block.minecraft.stone" would read "Stone". Meanwhile the compass lists
     * the wolf as resting at its bed, and after the respawn lists the fresh wolf (new UUID) instead.
     * Own batch: the pinned clock is global.
     */
    @GameTest(template = "platform", timeoutTicks = 300, batch = "petCompassClock")
    public void bed_respawn_after_missed_dawn_keeps_plain_name(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.setBlock(BED_REL, DIBlockRegistry.WHITE_PET_BED.get());
        BlockPos absBed = helper.absolutePos(BED_REL);
        ServerPlayer owner = owner(helper, new Vec3(1.5, 1, 1.5));

        Wolf wolf = helper.spawnWithNoFreeWill(EntityType.WOLF, new BlockPos(2, 2, 2));
        wolf.tame(owner);
        wolf.setCustomName(Component.literal("block.minecraft.stone"));
        helper.assertTrue(PetBedBlockEntity.tryClaim(level, absBed, wolf), "initial bed claim refused");
        TameableUtils.setPetBedPos(wolf, absBed);
        TameableUtils.setPetBedDimension(wolf, level.dimension().toString());

        final UUID deadWolfId = wolf.getUUID();
        final long dayTimeBefore = level.dayTime();
        final boolean daylightBefore = level.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT);
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, level.getServer());
        wolf.hurt(wolf.damageSources().genericKill(), 1000.0F);
        helper.runAfterDelay(5, () -> {
            helper.assertFalse(wolf.isAlive(), "wolf survived a 1000-damage generic kill");
            List<PetCompassListPacket.Entry> entries = PetCompassService.entriesFor(owner);
            helper.assertTrue(entries.stream().anyMatch(e -> e.status() == PetCompassListPacket.RESTING && e.pos().equals(absBed)),
                    "the compass did not list the dead wolf as resting at its bed: " + entries);
            long laterNoon = (dayTimeBefore / 24000L + 2L) * 24000L + 6000L;
            level.setDayTime(laterNoon);
        });
        helper.succeedWhen(() -> {
            List<Wolf> respawned = level.getEntitiesOfClass(Wolf.class, helper.getBounds(),
                    w -> w.isAlive() && !w.getUUID().equals(deadWolfId));
            helper.assertTrue(respawned.size() == 1, "expected the wolf back at its bed, found " + respawned.size());
            Wolf fresh = respawned.get(0);
            String name = fresh.getCustomName() == null ? "(none)" : fresh.getCustomName().getString();
            helper.assertTrue("block.minecraft.stone".equals(name), "respawned wolf's name came back as " + name);
            List<PetCompassListPacket.Entry> entries = PetCompassService.entriesFor(owner);
            helper.assertTrue(entries.stream().noneMatch(e -> e.status() == PetCompassListPacket.RESTING),
                    "the resting entry outlived the respawn: " + entries);
            helper.assertTrue(entries.stream().anyMatch(e -> e.pet().equals(fresh.getUUID()) && e.status() == PetCompassListPacket.ACTIVE),
                    "the respawned wolf is not on the compass: " + entries);
            level.setDayTime(dayTimeBefore);
            level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(daylightBefore, level.getServer());
            fresh.discard();
            removePlayer(owner);
        });
    }

    /** Recall on a loaded pet in the same dimension: the pet ends up at the player. */
    @GameTest(template = "platform", timeoutTicks = 60)
    public void pet_compass_recalls_a_loaded_pet(GameTestHelper helper) {
        ServerPlayer owner = owner(helper, new Vec3(1.5, 1, 1.5));
        Wolf wolf = helper.spawnWithNoFreeWill(EntityType.WOLF, new BlockPos(7, 1, 7));
        wolf.tame(owner);
        PetRecords.get(helper.getLevel().getServer()).update(wolf);
        helper.assertTrue(wolf.distanceTo(owner) > 6.0F, "precondition: the wolf should start across the platform");

        PetCompassService.handleAction(owner, wolf.getUUID(), PetCompassService.RECALL);
        helper.succeedWhen(() -> {
            helper.assertTrue(wolf.distanceTo(owner) < 2.0F, "recalled wolf is still " + wolf.distanceTo(owner) + " blocks away");
            removePlayer(owner);
        });
    }

    /** Go to on a loaded pet: the player ends up at the pet, and the pet stays put. */
    @GameTest(template = "platform", timeoutTicks = 60)
    public void pet_compass_takes_the_player_to_a_pet(GameTestHelper helper) {
        ServerPlayer owner = owner(helper, new Vec3(1.5, 1, 1.5));
        Wolf wolf = helper.spawnWithNoFreeWill(EntityType.WOLF, new BlockPos(7, 1, 7));
        wolf.tame(owner);
        PetRecords.get(helper.getLevel().getServer()).update(wolf);
        final Vec3 wolfPos = wolf.position();

        PetCompassService.handleAction(owner, wolf.getUUID(), PetCompassService.GO_TO);
        helper.succeedWhen(() -> {
            helper.assertTrue(owner.distanceTo(wolf) < 2.0F, "player is still " + owner.distanceTo(wolf) + " blocks from the wolf");
            helper.assertTrue(wolf.position().distanceTo(wolfPos) < 0.5D, "the wolf moved instead of the player");
            removePlayer(owner);
        });
    }
}
