package com.github.alexthe668.domesticationinnovation.client.gui;

import com.github.alexthe668.domesticationinnovation.server.misc.PetCompassActionPacket;
import com.github.alexthe668.domesticationinnovation.server.misc.PetCompassListPacket;
import com.github.alexthe668.domesticationinnovation.server.misc.PetCompassService;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.Locale;

/** The pet compass: one row per pet with where it is, and buttons to go to it or bring it back. */
public class PetCompassScreen extends Screen {

    private static final int ROW_HEIGHT = 36;
    private static final int BUTTON_WIDTH = 56;

    private PetCompassListPacket data;
    private PetList list;

    public PetCompassScreen(PetCompassListPacket data) {
        super(Component.translatable("gui.domesticationinnovation.pet_compass.title"));
        this.data = data;
    }

    /** New data from the server (after a recall, say): rebuild the rows but keep the scroll position. */
    public void setData(PetCompassListPacket data) {
        this.data = data;
        double scroll = this.list != null ? this.list.getScrollAmount() : 0;
        this.rebuildWidgets();
        this.list.setScrollAmount(scroll);
    }

    @Override
    protected void init() {
        this.list = new PetList(this.minecraft, this.width, this.height - 64, 32);
        for (PetCompassListPacket.Entry entry : this.data.entries()) {
            this.list.addRow(new Row(entry));
        }
        this.addRenderableWidget(this.list);
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose())
                .bounds(this.width / 2 - 100, this.height - 27, 200, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 12, 0xFFFFFF);
        if (this.data.entries().isEmpty()) {
            for (int i = 0; i < 2; i++) {
                graphics.drawCenteredString(this.font, Component.translatable("gui.domesticationinnovation.pet_compass.empty_" + (i + 1)),
                        this.width / 2, this.height / 2 - 14 + i * 12, 0xA0A0A0);
            }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void send(PetCompassListPacket.Entry entry, byte action) {
        PacketDistributor.sendToServer(new PetCompassActionPacket(entry.pet(), action));
    }

    private static Component dimensionName(ResourceLocation dimension) {
        if (dimension.getNamespace().equals("minecraft")) {
            String key = "gui.domesticationinnovation.pet_compass.dimension." + dimension.getPath();
            if (Language.getInstance().has(key)) {
                return Component.translatable(key);
            }
        }
        StringBuilder name = new StringBuilder();
        for (String word : dimension.getPath().split("[_/]")) {
            if (!word.isEmpty()) {
                if (!name.isEmpty()) {
                    name.append(' ');
                }
                name.append(word.substring(0, 1).toUpperCase(Locale.ROOT)).append(word.substring(1));
            }
        }
        return Component.literal(name.toString());
    }

    private class PetList extends ContainerObjectSelectionList<Row> {

        PetList(Minecraft minecraft, int width, int height, int y) {
            super(minecraft, width, height, y, ROW_HEIGHT);
        }

        void addRow(Row row) {
            this.addEntry(row);
        }

        @Override
        public int getRowWidth() {
            return Math.min(360, this.width - 24);
        }

        @Override
        protected int getScrollbarPosition() {
            return this.getX() + this.width / 2 + this.getRowWidth() / 2 + 6;
        }
    }

    private class Row extends ContainerObjectSelectionList.Entry<Row> {

        private final PetCompassListPacket.Entry entry;
        private final Button goButton;
        private final Button recallButton;

        Row(PetCompassListPacket.Entry entry) {
            this.entry = entry;
            this.goButton = Button.builder(Component.translatable("gui.domesticationinnovation.pet_compass.go"), button -> {
                PetCompassScreen.this.send(entry, PetCompassService.GO_TO);
                PetCompassScreen.this.onClose();
            }).size(BUTTON_WIDTH, 18).build();
            this.recallButton = Button.builder(Component.translatable("gui.domesticationinnovation.pet_compass.recall"),
                    button -> PetCompassScreen.this.send(entry, PetCompassService.RECALL)).size(BUTTON_WIDTH, 18).build();
            boolean resting = entry.status() == PetCompassListPacket.RESTING;
            boolean reachable = sameDimension() || PetCompassScreen.this.data.allowCrossDimension();
            this.goButton.active = PetCompassScreen.this.data.allowGoTo() && !resting && reachable;
            this.recallButton.active = PetCompassScreen.this.data.allowRecall() && !resting && reachable;
        }

        private boolean sameDimension() {
            Minecraft minecraft = PetCompassScreen.this.minecraft;
            return minecraft.level != null && minecraft.level.dimension().location().equals(this.entry.dimension());
        }

        private Component statusLine() {
            MutableComponent line = switch (this.entry.status()) {
                case PetCompassListPacket.ACTIVE -> Component.translatable("gui.domesticationinnovation.pet_compass.status_active")
                        .withStyle(ChatFormatting.GREEN);
                case PetCompassListPacket.RESTING -> Component.translatable("gui.domesticationinnovation.pet_compass.status_resting")
                        .withStyle(ChatFormatting.RED);
                default -> Component.translatable("gui.domesticationinnovation.pet_compass.status_last_seen")
                        .withStyle(ChatFormatting.YELLOW);
            };
            line.append(Component.literal("  " + this.entry.pos().getX() + ", " + this.entry.pos().getY() + ", " + this.entry.pos().getZ())
                    .withStyle(ChatFormatting.GRAY));
            Minecraft minecraft = PetCompassScreen.this.minecraft;
            if (sameDimension() && minecraft.player != null) {
                int distance = (int) Math.sqrt(minecraft.player.distanceToSqr(this.entry.pos().getX() + 0.5D,
                        this.entry.pos().getY(), this.entry.pos().getZ() + 0.5D));
                line.append(Component.literal("  "))
                        .append(Component.translatable("gui.domesticationinnovation.pet_compass.distance", distance).withStyle(ChatFormatting.GRAY));
            } else {
                line.append(Component.literal("  ")).append(dimensionName(this.entry.dimension()).copy().withStyle(ChatFormatting.AQUA));
            }
            return line;
        }

        private FormattedCharSequence clip(Component text, int maxWidth) {
            return Language.getInstance().getVisualOrder(PetCompassScreen.this.font.substrByWidth(text, maxWidth));
        }

        @Override
        public void render(GuiGraphics graphics, int index, int top, int left, int width, int height, int mouseX, int mouseY,
                           boolean hovering, float partialTick) {
            int textWidth = width - BUTTON_WIDTH * 2 - 12;
            MutableComponent name = this.entry.name().copy().withStyle(ChatFormatting.WHITE);
            if (this.entry.health() >= 0) {
                name.append(Component.literal("  ")).append(Component.translatable("gui.domesticationinnovation.pet_compass.health",
                        (int) Math.ceil(this.entry.health()), (int) Math.ceil(this.entry.maxHealth())).withStyle(ChatFormatting.GRAY));
            }
            graphics.drawString(PetCompassScreen.this.font, clip(name, textWidth), left + 4, top + 6, 0xFFFFFF);
            graphics.drawString(PetCompassScreen.this.font, clip(statusLine(), textWidth), left + 4, top + 19, 0xFFFFFF);
            this.recallButton.setPosition(left + width - BUTTON_WIDTH - 2, top + 8);
            this.goButton.setPosition(left + width - BUTTON_WIDTH * 2 - 6, top + 8);
            this.goButton.render(graphics, mouseX, mouseY, partialTick);
            this.recallButton.render(graphics, mouseX, mouseY, partialTick);
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of(this.goButton, this.recallButton);
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of(this.goButton, this.recallButton);
        }
    }
}
