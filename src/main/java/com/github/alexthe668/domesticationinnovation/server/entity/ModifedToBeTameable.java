package com.github.alexthe668.domesticationinnovation.server.entity;

import com.github.alexthe668.domesticationinnovation.DomesticationMod;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Interface applied to mobs that can be tamed by Domestication Innovation
 * (Axolotls, Foxes, Rabbits, Frogs, etc.) via mixin.
 *
 * In 1.21.1, this also absorbs the command functionality that was previously
 * handled by Citadel's IComandableMob interface.
 *
 * Command system: 0 = wander, 1 = stay, 2 = follow
 *
 * TamableAnimal subclasses (Cat, Wolf, Parrot) get sensible defaults that
 * delegate to the vanilla owner/sit API. Non-TamableAnimal mixins (Axolotl,
 * Fox, Rabbit, Frog) override these with their own entity-data-backed storage.
 */
public interface ModifedToBeTameable extends OwnableEntity {

    boolean isTame();
    void setTame(boolean value);

    @Nullable
    default UUID getTameOwnerUUID() {
        if (this instanceof TamableAnimal ta) return ta.getOwnerUUID();
        return null;
    }

    default void setTameOwnerUUID(@Nullable UUID uuid) {
        if (this instanceof TamableAnimal ta) ta.setOwnerUUID(uuid);
    }

    @Nullable
    default LivingEntity getTameOwner() {
        if (this instanceof TamableAnimal ta) return ta.getOwner();
        return null;
    }

    default boolean isStayingStill() {
        if (DomesticationMod.CONFIG.trinaryCommandSystem.get()) return getCommand() == 1;
        if (this instanceof TamableAnimal ta) return ta.isOrderedToSit();
        return false;
    }

    default boolean isFollowingOwner() {
        if (DomesticationMod.CONFIG.trinaryCommandSystem.get()) return getCommand() == 2;
        if (this instanceof TamableAnimal ta) return ta.isTame() && !ta.isOrderedToSit();
        return false;
    }

    default boolean isValidAttackTarget(LivingEntity target) {
        return false;
    }

    /** Get the current command state. 0 = wander, 1 = stay, 2 = follow */
    default int getCommand() { return 1; }

    /** Set the command state. */
    default void setCommand(int command) {}

    /**
     * Cycle command from player interaction.
     * Returns InteractionResult.SUCCESS for use in interaction handlers.
     */
    default InteractionResult playerSetCommand(Player player, LivingEntity pet) {
        int next = (getCommand() + 1) % 3;
        setCommand(next);
        player.displayClientMessage(Component.translatable("message.domesticationinnovation.command_" + next, pet.getName()), true);
        return InteractionResult.SUCCESS;
    }

    @Nullable
    default UUID getOwnerUUID() {
        return getTameOwnerUUID();
    }
}
