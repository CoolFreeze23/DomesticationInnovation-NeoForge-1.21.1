package com.github.alexthe668.domesticationinnovation.client.tooltip;

import com.mojang.blaze3d.platform.Lighting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import javax.annotation.Nullable;

/**
 * Draws a slowly spinning 3D model of the pet captured in a stack's snapshot.
 *
 * The display entity is rebuilt from NBT at most once per hovered snapshot: a
 * tooltip describes one stack at a time and its snapshot tag is the same
 * instance every frame, so a single-slot cache keyed on tag identity covers
 * the steady-state hover. A snapshot that fails to deserialize client-side is
 * remembered as a failure so it is not retried every frame.
 */
public class PetSnapshotTooltipRenderer implements ClientTooltipComponent {

    private static final int BOX_WIDTH = 56;
    private static final int BOX_HEIGHT = 60;
    private static final float SPIN_DEGREES_PER_SECOND = 40F;
    private static final Vector3f NO_OFFSET = new Vector3f();

    @Nullable
    private static CompoundTag lastSnapshot;
    @Nullable
    private static LivingEntity lastDisplayEntity;

    /**
     * Drops the cached display entity - and, through Entity.level(), the whole
     * ClientLevel it retains - on logout. Without this a player who hovered a
     * snapshot and then disconnected would pin the dead level until another
     * snapshot is hovered in a new world. Registered against
     * ClientPlayerNetworkEvent.LoggingOut in ClientProxy.clientInit.
     */
    public static void clearCache() {
        lastSnapshot = null;
        lastDisplayEntity = null;
    }

    @Nullable
    private final LivingEntity displayEntity;

    public PetSnapshotTooltipRenderer(PetSnapshotTooltip tooltip) {
        this.displayEntity = resolveDisplayEntity(tooltip);
    }

    @Nullable
    private static LivingEntity resolveDisplayEntity(PetSnapshotTooltip tooltip) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            lastSnapshot = null;
            lastDisplayEntity = null;
            return null;
        }
        // an entity cached before a dimension change holds a stale level - rebuild
        if (tooltip.snapshot() == lastSnapshot
                && (lastDisplayEntity == null || lastDisplayEntity.level() == level)) {
            return lastDisplayEntity;
        }
        lastSnapshot = tooltip.snapshot();
        lastDisplayEntity = buildDisplayEntity(tooltip, level);
        return lastDisplayEntity;
    }

    @Nullable
    private static LivingEntity buildDisplayEntity(PetSnapshotTooltip tooltip, ClientLevel level) {
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(tooltip.entityTypeId()).orElse(null);
        if (type == null) {
            return null;
        }
        try {
            Entity created = type.create(level);
            if (!(created instanceof LivingEntity living)) {
                return null;
            }
            living.readAdditionalSaveData(tooltip.snapshot().copy());
            // the snapshot may have been taken mid-combat; strip the transient
            // state so the preview is not tinted red or mid-death-flop
            living.hurtTime = 0;
            living.deathTime = 0;
            living.setYRot(0);
            living.setXRot(0);
            living.setYHeadRot(0);
            living.yHeadRotO = 0;
            living.yBodyRot = 0;
            living.yBodyRotO = 0;
            return living;
        } catch (Exception e) {
            // some modded entities cannot be rebuilt without a server; no preview then
            return null;
        }
    }

    @Override
    public int getHeight() {
        return displayEntity == null ? 0 : BOX_HEIGHT + 2;
    }

    @Override
    public int getWidth(Font font) {
        return displayEntity == null ? 0 : BOX_WIDTH;
    }

    @Override
    public void renderImage(Font font, int x, int y, GuiGraphics guiGraphics) {
        LivingEntity entity = this.displayEntity;
        if (entity == null) {
            return;
        }
        // one "tick" per rendered frame keeps idle animations moving
        entity.tickCount++;
        int scale = Math.round(30F / Math.max(1.0F, Math.max(entity.getBbWidth(), entity.getBbHeight())));
        float spin = (Util.getMillis() % 360_000L) / 1000F * SPIN_DEGREES_PER_SECOND;
        Quaternionf orientation = new Quaternionf().rotationZ(Mth.PI).rotateY(spin * Mth.DEG_TO_RAD);
        guiGraphics.enableScissor(x, y, x + BOX_WIDTH, y + BOX_HEIGHT);
        try {
            InventoryScreen.renderEntityInInventory(guiGraphics, x + BOX_WIDTH / 2, y + BOX_HEIGHT - 4,
                    scale, NO_OFFSET, orientation, null, entity);
        } catch (Exception e) {
            // renderer choked on the static display entity - forget it rather than fail every frame
            lastDisplayEntity = null;
            // the vanilla method aborted between setRenderShadow(false)/
            // setupForEntityInInventory() and their restores; repair the leaked
            // global state or shadows stay off for the rest of the session (the
            // unbalanced pushPose is confined to the per-frame GuiGraphics pose
            // stack and self-heals next frame)
            Minecraft.getInstance().getEntityRenderDispatcher().setRenderShadow(true);
            Lighting.setupFor3DItems();
        } finally {
            guiGraphics.disableScissor();
        }
    }
}
