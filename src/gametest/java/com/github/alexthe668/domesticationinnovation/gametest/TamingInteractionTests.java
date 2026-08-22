package com.github.alexthe668.domesticationinnovation.gametest;

import com.github.alexthe668.domesticationinnovation.DomesticationMod;
import com.github.alexthe668.domesticationinnovation.server.enchantment.DIEnchantmentKeys;
import com.github.alexthe668.domesticationinnovation.server.entity.TameableUtils;
import com.github.alexthe668.domesticationinnovation.server.item.DIItemRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Ocelot;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.animal.Wolf;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.network.CommonListenerCookie;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Map;

/**
 * Player-interaction paths of CommonProxy.onInteractWithEntity, driven through
 * REAL event dispatch: {@code Player.interactOn} is patched by NeoForge to post
 * {@code PlayerInteractEvent.EntityInteract} before any vanilla mobInteract
 * runs, and GameTest mock players go through that exact patch. So every test
 * here exercises the full production entry point - event bus, handler
 * precedence, cancellation - not a unit-style shortcut into the handler.
 *
 * Determinism notes:
 * <ul>
 *   <li>The only random roll on these paths is the datapack taming chance
 *       (0.33 for the shipped ocelot entry). That test retries once per tick
 *       with a refilled hand; 200 attempts miss with p = 0.67^200 &lt; 1e-34,
 *       far below any practical flake threshold.</li>
 *   <li>All animals spawn with no free will so nothing walks out of the
 *       structure bounds mid-test.</li>
 *   <li>Pet state is injected through the mod's own APIs (TameableUtils /
 *       the PET_DATA attachment behind it), never raw NBT.</li>
 * </ul>
 *
 * The transformation registry ships EMPTY by default (the javadoc on
 * TransformationDefinition is explicit about that), so asserting against
 * shipped data is impossible. Instead the gametest source set - which
 * build.gradle registers as part of the domesticationinnovation mod in dev,
 * the same mechanism that serves the platform.nbt template - ships a
 * test-only entry (gametest_sheep_to_cow.json) through the real datapack
 * registry, so the transformation test covers JSON decode + registry load +
 * the interaction path end to end.
 */
@GameTestHolder(DomesticationMod.MODID)
@PrefixGameTestTemplate(false)
public class TamingInteractionTests {

    private static final BlockPos CENTER = new BlockPos(4, 2, 4);

    /**
     * Vanilla's makeMockServerPlayerInLevel hardcodes isCreative() = true, and
     * the mod deliberately skips item consumption for creative players - so
     * these tests build the same in-level mock without that override. Being in
     * the level's player list matters: ownership checks resolve the owner via
     * getPlayerByUUID, and a detached mock reads as an absent owner.
     */
    private static Player mockPlayerHolding(GameTestHelper helper, ItemStack stack) {
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(
                new GameProfile(UUID.randomUUID(), "di-survival-mock"), false);
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
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        return player;
    }

    private static ItemStack enchantedCollar(GameTestHelper helper) {
        ItemStack collar = new ItemStack(DIItemRegistry.COLLAR_TAG.get());
        var enchants = helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        collar.enchant(enchants.getOrThrow(DIEnchantmentKeys.HEALTH_BOOST), 2);
        collar.enchant(enchants.getOrThrow(DIEnchantmentKeys.SPEEDSTER), 1);
        return collar;
    }

    /**
     * (1) Datapack taming, the mod's own shipped ocelot entry (cat_food tag,
     * chance 0.33): repeated right-clicks with a cod must consume the cod on
     * EVERY matched attempt (the definition consumes before the roll, exactly
     * like the species paths) and eventually data-tame the ocelot to the
     * clicking player. Retrying once per tick beats the 0.33 chance
     * deterministically within the timeout (see class javadoc).
     */
    @GameTest(template = "platform", timeoutTicks = 200)
    public void datapack_taming_tames_ocelot(GameTestHelper helper) {
        final Ocelot ocelot = helper.spawnWithNoFreeWill(EntityType.OCELOT, CENTER);
        final Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        helper.assertFalse(TameableUtils.isDataTamed(ocelot), "wild ocelot must not start data-tamed");
        helper.onEachTick(() -> {
            if (TameableUtils.isDataTamed(ocelot)) {
                helper.assertTrue(player.getUUID().equals(TameableUtils.getOwnerUUIDOf(ocelot)),
                        "data-tamed ocelot must record the clicking player as owner");
                helper.assertTrue(TameableUtils.isTamed(ocelot),
                        "data-tamed ocelot must read as tamed through the config-filtered view");
                helper.succeed();
                return;
            }
            // Refill and click: one attempt (and one roll) per tick
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.COD));
            InteractionResult result = player.interactOn(ocelot, InteractionHand.MAIN_HAND);
            helper.assertTrue(result.consumesAction(),
                    "the taming definition matched a cod click, so the event must be cancelled SUCCESS (got "
                            + result + ")");
            helper.assertTrue(player.getMainHandItem().isEmpty(),
                    "every matched taming attempt must consume the cod, tame or not");
        });
    }

    /**
     * (2) Collar application: an enchanted collar tag used on your own tamed
     * wolf transfers name + enchantments onto the pet, flags the collar, and
     * consumes the item.
     */
    @GameTest(template = "platform", timeoutTicks = 100)
    public void collar_tag_applies_enchants(GameTestHelper helper) {
        Wolf wolf = helper.spawnWithNoFreeWill(EntityType.WOLF, CENTER);
        ItemStack collar = enchantedCollar(helper);
        collar.set(DataComponents.CUSTOM_NAME, Component.literal("Rex"));
        Player player = mockPlayerHolding(helper, collar);
        wolf.tame(player);

        InteractionResult result = player.interactOn(wolf, InteractionHand.MAIN_HAND);

        helper.assertTrue(result.consumesAction(),
                "collar application must cancel the event SUCCESS (got " + result + ")");
        helper.assertTrue(TameableUtils.hasCollar(wolf), "pet must carry the collar flag after application");
        Map<ResourceLocation, Integer> enchants = TameableUtils.getEnchantsRaw(wolf);
        helper.assertTrue(enchants != null
                        && Integer.valueOf(2).equals(enchants.get(DIEnchantmentKeys.HEALTH_BOOST.location()))
                        && Integer.valueOf(1).equals(enchants.get(DIEnchantmentKeys.SPEEDSTER.location()))
                        && enchants.size() == 2,
                "pet must store exactly the collar's enchants (health_boost 2, speedster 1), got " + enchants);
        helper.assertTrue(wolf.hasCustomName() && "Rex".equals(wolf.getCustomName().getString()),
                "collar custom name must transfer to the pet");
        helper.assertTrue(player.getMainHandItem().isEmpty(),
                "a survival player's collar tag must be consumed on application");
        helper.succeed();
    }

    /**
     * (3) Binding curse refusal: a pet whose stored collar carries
     * minecraft:binding_curse refuses a replacement collar - the event is
     * cancelled FAIL, the new tag is NOT consumed, and the stored enchants
     * stay exactly as they were.
     */
    @GameTest(template = "platform", timeoutTicks = 100)
    public void binding_curse_refuses_new_collar(GameTestHelper helper) {
        Wolf wolf = helper.spawnWithNoFreeWill(EntityType.WOLF, CENTER);
        Player player = mockPlayerHolding(helper, enchantedCollar(helper));
        wolf.tame(player);
        // Inject the cursed collar through the mod's own storage API
        // (addEnchant also raises the collar flag, matching a real application)
        TameableUtils.addEnchant(wolf, ResourceLocation.withDefaultNamespace("binding_curse"), 1);
        helper.assertTrue(TameableUtils.hasCollar(wolf), "curse setup must leave the pet wearing a collar");

        InteractionResult result = player.interactOn(wolf, InteractionHand.MAIN_HAND);

        helper.assertFalse(result.consumesAction(),
                "a binding-cursed collar must refuse the swap with FAIL (got " + result + ")");
        helper.assertTrue(player.getMainHandItem().is(DIItemRegistry.COLLAR_TAG.get())
                        && player.getMainHandItem().getCount() == 1,
                "the refused collar tag must NOT be consumed");
        Map<ResourceLocation, Integer> enchants = TameableUtils.getEnchantsRaw(wolf);
        helper.assertTrue(enchants != null && enchants.size() == 1
                        && Integer.valueOf(1).equals(enchants.get(ResourceLocation.withDefaultNamespace("binding_curse"))),
                "the pet's stored enchants must be untouched by the refused swap, got " + enchants);
        helper.succeed();
    }

    /**
     * (4) Identical-collar no-op: applying a collar with the same custom name
     * and the same enchant set the pet already wears is refused (FAIL) without
     * consuming the item - the anti-dupe/no-op branch.
     */
    @GameTest(template = "platform", timeoutTicks = 100)
    public void identical_collar_is_noop(GameTestHelper helper) {
        Wolf wolf = helper.spawnWithNoFreeWill(EntityType.WOLF, CENTER);
        ItemStack collar = new ItemStack(DIItemRegistry.COLLAR_TAG.get());
        var enchantLookup = helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        collar.enchant(enchantLookup.getOrThrow(DIEnchantmentKeys.SPEEDSTER), 1);
        collar.set(DataComponents.CUSTOM_NAME, Component.literal("Rex"));
        Player player = mockPlayerHolding(helper, collar);
        wolf.tame(player);
        // Pet already wears exactly this collar: same name, same single enchant
        wolf.setCustomName(Component.literal("Rex"));
        TameableUtils.addEnchant(wolf, DIEnchantmentKeys.SPEEDSTER.location(), 1);

        InteractionResult result = player.interactOn(wolf, InteractionHand.MAIN_HAND);

        helper.assertFalse(result.consumesAction(),
                "an identical collar must be a FAIL no-op (got " + result + ")");
        helper.assertTrue(player.getMainHandItem().is(DIItemRegistry.COLLAR_TAG.get())
                        && player.getMainHandItem().getCount() == 1,
                "the identical collar tag must NOT be consumed");
        Map<ResourceLocation, Integer> enchants = TameableUtils.getEnchantsRaw(wolf);
        helper.assertTrue(enchants != null && enchants.size() == 1
                        && Integer.valueOf(1).equals(enchants.get(DIEnchantmentKeys.SPEEDSTER.location())),
                "the pet's enchants must be unchanged by the no-op, got " + enchants);
        helper.succeed();
    }

    /**
     * (5) Generic trinary command cycling: empty-hand main-hand clicks on a
     * data-tamed mob with NO native taming API (a cow) advance the generic
     * command. The mod seeds a fresh data-tame at 1 ("stay") to match the
     * species paths, and each click applies (cur+1)%3 - so the observed cycle
     * is 1 -> 2 -> 0 -> 1, covering every state and the wrap. (The task
     * sketch's 0->1->2 assumed a 0 start; the mod deliberately seeds 1, so
     * the assertions follow the mod's real contract.)
     */
    @GameTest(template = "platform", timeoutTicks = 100)
    public void generic_command_cycles(GameTestHelper helper) {
        Cow cow = helper.spawnWithNoFreeWill(EntityType.COW, CENTER);
        Player player = mockPlayerHolding(helper, ItemStack.EMPTY);
        TameableUtils.setDataTameOwner(cow, player.getUUID());
        helper.assertTrue(TameableUtils.isDataTamed(cow), "cow must be data-tamed after setDataTameOwner");
        helper.assertTrue(TameableUtils.tryGetCommand(cow) == 1,
                "a fresh data-tame must seed command 1 (stay), got " + TameableUtils.tryGetCommand(cow));

        int[] expected = {2, 0, 1};
        for (int step = 0; step < expected.length; step++) {
            InteractionResult result = player.interactOn(cow, InteractionHand.MAIN_HAND);
            helper.assertTrue(result.consumesAction(),
                    "command-cycling click " + step + " must cancel SUCCESS (got " + result + ")");
            int command = TameableUtils.tryGetCommand(cow);
            helper.assertTrue(command == expected[step],
                    "click " + step + " must cycle the command to " + expected[step] + ", got " + command);
        }
        helper.succeed();
    }

    /**
     * (6) Datapack transformation, via the test-only sheep->cow entry shipped
     * in the gametest resources (see class javadoc for why): a golden-carrot
     * click on a data-tamed sheep consumes the carrot, discards the sheep, and
     * spawns a cow that keeps the custom name, the PET_DATA ownership record,
     * and the health FRACTION (the handler restores fraction * new max after
     * the copied save data settles the result's attributes).
     */
    @GameTest(template = "platform", timeoutTicks = 100)
    public void transformation_applies_datapack_entry(GameTestHelper helper) {
        Sheep sheep = helper.spawnWithNoFreeWill(EntityType.SHEEP, CENTER);
        Player player = mockPlayerHolding(helper, new ItemStack(Items.GOLDEN_CARROT));
        TameableUtils.setDataTameOwner(sheep, player.getUUID());
        sheep.setCustomName(Component.literal("Dolly"));
        sheep.setHealth(sheep.getMaxHealth() * 0.5F);

        InteractionResult result = player.interactOn(sheep, InteractionHand.MAIN_HAND);

        helper.assertTrue(result.consumesAction(),
                "a matched transformation must cancel the event CONSUME (got " + result
                        + ") - is the gametest datapack entry loading?");
        helper.assertTrue(player.getMainHandItem().isEmpty(),
                "the survival player's trigger item must be consumed");
        helper.assertEntityNotPresent(EntityType.SHEEP);
        helper.assertEntityPresent(EntityType.COW);

        var cows = helper.getLevel().getEntitiesOfClass(Cow.class, helper.getBounds());
        helper.assertTrue(cows.size() == 1, "exactly one result cow expected, found " + cows.size());
        Cow cowResult = cows.get(0);
        helper.assertTrue(cowResult.hasCustomName() && "Dolly".equals(cowResult.getCustomName().getString()),
                "the transformation must carry the custom name to the result");
        helper.assertTrue(TameableUtils.isDataTamed(cowResult)
                        && player.getUUID().equals(TameableUtils.getOwnerUUIDOf(cowResult)),
                "the transformation must carry the PET_DATA ownership record to the result");
        float fraction = cowResult.getHealth() / cowResult.getMaxHealth();
        helper.assertTrue(Math.abs(fraction - 0.5F) < 1.0E-3F,
                "the result must keep the 50% health fraction, got " + fraction
                        + " (" + cowResult.getHealth() + "/" + cowResult.getMaxHealth() + ")");
        helper.succeed();
    }
}
