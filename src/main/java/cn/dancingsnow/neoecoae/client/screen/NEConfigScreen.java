package cn.dancingsnow.neoecoae.client.screen;

import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.config.NEConfig;
import cn.dancingsnow.neoecoae.util.NETextFormat;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

public class NEConfigScreen extends Screen {
    private static final int LABEL_WIDTH = 170;
    private static final int FIELD_WIDTH = 120;

    private final Screen parent;
    private EditBox craftingLength;
    private EditBox computationLength;
    private EditBox storageLength;
    private Button capacityButton;
    private boolean increaseCapacity;
    private Component error = Component.empty();

    public NEConfigScreen(Screen parent) {
        super(Component.translatable("screen.neoecoae.config.title"));
        this.parent = parent;
        this.increaseCapacity = NEConfig.isIncreaseStorageCellCapacity();
    }

    @Override
    protected void init() {
        int x = this.width / 2 - (LABEL_WIDTH + FIELD_WIDTH) / 2;
        int y = 56;

        this.craftingLength = createIntBox(x + LABEL_WIDTH, y, NEConfig.craftingSystemMaxLength);
        this.computationLength = createIntBox(x + LABEL_WIDTH, y + 28, NEConfig.computationSystemMaxLength);
        this.storageLength = createIntBox(x + LABEL_WIDTH, y + 56, NEConfig.storageSystemMaxLength);

        addRenderableWidget(this.craftingLength);
        addRenderableWidget(this.computationLength);
        addRenderableWidget(this.storageLength);

        this.capacityButton = Button.builder(capacityText(), button -> {
                    this.increaseCapacity = !this.increaseCapacity;
                    button.setMessage(capacityText());
                })
                .bounds(x + LABEL_WIDTH, y + 84, FIELD_WIDTH, 20)
                .build();
        addRenderableWidget(this.capacityButton);

        int buttonY = this.height - 34;
        addRenderableWidget(Button.builder(Component.translatable("screen.neoecoae.config.save"), button -> save())
                .bounds(this.width / 2 - 155, buttonY, 150, 20)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("screen.neoecoae.config.cancel"), button -> close())
                .bounds(this.width / 2 + 5, buttonY, 150, 20)
                .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 15, 0xFFFFFFFF);

        List<FormattedCharSequence> notice = this.font.split(
                Component.translatable("screen.neoecoae.config.restart_notice").withStyle(ChatFormatting.GOLD),
                Math.max(120, this.width - 40));
        int noticeY = 30;
        for (FormattedCharSequence line : notice) {
            graphics.drawCenteredString(this.font, line, this.width / 2, noticeY, 0xFFFFD166);
            noticeY += 10;
        }

        int x = this.width / 2 - (LABEL_WIDTH + FIELD_WIDTH) / 2;
        int y = 62;
        drawLabel(graphics, "screen.neoecoae.config.craftingSystemMaxLength", x, y);
        drawLabel(graphics, "screen.neoecoae.config.computationSystemMaxLength", x, y + 28);
        drawLabel(graphics, "screen.neoecoae.config.storageSystemMaxLength", x, y + 56);
        drawLabel(graphics, "screen.neoecoae.config.increaseCapacity", x, y + 84);

        if (!this.error.getString().isEmpty()) {
            graphics.drawCenteredString(this.font, this.error, this.width / 2, this.height - 48, 0xFFFF5555);
        }

        super.render(graphics, mouseX, mouseY, partialTick);

        if (this.capacityButton != null && this.capacityButton.isMouseOver(mouseX, mouseY)) {
            graphics.renderComponentTooltip(this.font, capacityTooltip(), mouseX, mouseY);
        }
    }

    @Override
    public void onClose() {
        close();
    }

    private EditBox createIntBox(int x, int y, int value) {
        EditBox box = new EditBox(this.font, x, y, FIELD_WIDTH, 20, Component.empty());
        box.setFilter(text -> text.isEmpty() || text.chars().allMatch(Character::isDigit));
        box.setMaxLength(9);
        box.setValue(Integer.toString(value));
        return box;
    }

    private void drawLabel(GuiGraphics graphics, String key, int x, int y) {
        graphics.drawString(
                this.font, Component.translatable(key).withStyle(ChatFormatting.GRAY), x, y, 0xFFA0A0A0, false);
    }

    private Component capacityText() {
        return Component.translatable(
                this.increaseCapacity
                        ? "screen.neoecoae.config.increaseCapacity.on"
                        : "screen.neoecoae.config.increaseCapacity.off");
    }

    private List<Component> capacityTooltip() {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("screen.neoecoae.config.increaseCapacity.tooltip.title")
                .withStyle(ChatFormatting.GRAY));

        for (ECOTier tier : ECOTier.values()) {
            long originalBytes = tier.getStorageTotalBytes();
            long expandedBytes = NEConfig.getExpandedEcoStorageCellCapacity(tier, originalBytes);
            lines.add(Component.translatable(
                            "screen.neoecoae.config.increaseCapacity.tooltip.storage",
                            formatStorageTier(tier),
                            NETextFormat.formatBytes(originalBytes),
                            NETextFormat.formatBytes(expandedBytes))
                    .withStyle(ChatFormatting.AQUA));
        }

        return lines;
    }

    private static String formatStorageTier(ECOTier tier) {
        return "LE" + tier.name().substring(1);
    }

    private void save() {
        try {
            int crafting = parsePositiveInt(this.craftingLength.getValue());
            int computation = parsePositiveInt(this.computationLength.getValue());
            int storage = parsePositiveInt(this.storageLength.getValue());
            NEConfig.applyClientConfig(crafting, computation, storage, this.increaseCapacity);
            close();
        } catch (NumberFormatException ignored) {
            this.error = Component.translatable("screen.neoecoae.config.invalid");
        }
    }

    private static int parsePositiveInt(String value) {
        if (value == null || value.isBlank()) {
            throw new NumberFormatException("empty");
        }
        int parsed = Integer.parseInt(value);
        if (parsed <= 0) {
            throw new NumberFormatException("non-positive");
        }
        return parsed;
    }

    private void close() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }
}
