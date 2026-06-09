package cn.dancingsnow.neoecoae.gui.ldlib;

import cn.dancingsnow.neoecoae.gui.nativeui.NENativeUiConstants;
import cn.dancingsnow.neoecoae.gui.nativeui.menu.NEStructureTerminalMenu;
import cn.dancingsnow.neoecoae.multiblock.NEStructureTerminalUiState;
import cn.dancingsnow.neoecoae.multiblock.StructureTerminalHostType;
import cn.dancingsnow.neoecoae.multiblock.StructureTerminalMode;
import cn.dancingsnow.neoecoae.network.NENetwork;
import com.lowdragmc.lowdraglib.gui.texture.ColorRectAndBorderTexture;
import com.lowdragmc.lowdraglib.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.ResourceBorderTexture;
import com.lowdragmc.lowdraglib.gui.widget.ButtonWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.utils.Position;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * LDLib1 client screen for the handheld Structure Terminal.
 *
 * <p>The server-side item/menu/packet flow remains authoritative: all buttons
 * still send {@link NENetwork.NEStructureTerminalConfigActionPacket}, and this
 * screen only reflects the latest config packet pushed back by the server.
 */
public class NEStructureTerminalLDLibUI extends AbstractContainerScreen<NEStructureTerminalMenu> {
    private static final int WIDTH = 358;
    private static final int HEIGHT = 140;

    private static final int TEXT_PRIMARY = 0xFF404040;
    private static final int TEXT_MUTED = 0xFF707070;
    private static final int TEXT_VALUE = 0xFF315F92;
    private static final int TEXT_SUCCESS = 0xFF1A7A3A;
    private static final int TEXT_WARNING = 0xFFE0A020;
    private static final int TEXT_ERROR = 0xFFD94343;

    private static final int PANEL_X = 7;
    private static final int PANEL_Y = 24;
    private static final int PANEL_GAP = 7;
    private static final int PANEL_H = HEIGHT - PANEL_Y - 7;
    private static final int CONTROL_X = PANEL_X;
    private static final int CONTROL_Y = PANEL_Y;
    private static final int CONTROL_W = 122;
    private static final int CONTROL_H = PANEL_H;
    private static final int MATERIAL_X = CONTROL_X + CONTROL_W + PANEL_GAP;
    private static final int MATERIAL_Y = PANEL_Y;
    private static final int MATERIAL_W = WIDTH - MATERIAL_X - PANEL_X;
    private static final int MATERIAL_H = PANEL_H;

    private static final int BUTTON_H = 18;
    private static final int LENGTH_BUTTON_W = 22;
    private static final int CONTROL_ROW_W = 107;
    private static final int CONTROL_ROW_X = CONTROL_X + (CONTROL_W - CONTROL_ROW_W) / 2;
    private static final int LENGTH_VALUE_W = CONTROL_ROW_W - LENGTH_BUTTON_W * 2 - 14;
    private static final int LENGTH_ROW_Y = CONTROL_Y + 35;
    private static final int RESET_ROW_Y = LENGTH_ROW_Y + BUTTON_H + 7;
    private static final int MODE_ROW_Y = RESET_ROW_Y + BUTTON_H + 7;
    private static final int MODE_BUTTON_W = 31;
    private static final int MODE_GAP = 7;

    private static final int TARGET_BUTTON_W = 52;
    private static final int TARGET_BUTTON_H = 18;
    private static final int TARGET_BUTTON_GAP = 4;
    private static final int TARGET_BUTTON_Y = MATERIAL_Y + 20;

    private static final int MATERIAL_COLS = 10;
    private static final int MATERIAL_ROWS = 2;
    private static final int MATERIAL_SLOT_SIZE = 18;
    private static final int MATERIAL_GRID_Y = MATERIAL_Y + 54;

    private static final IGuiTexture BACKGROUND =
            new GuiTextureGroup(new ColorRectTexture(0xFFE8E8E8), ResourceBorderTexture.BORDERED_BACKGROUND.copy());
    private static final IGuiTexture BUTTON_TEXTURE =
            new GuiTextureGroup(new ColorRectAndBorderTexture(0xFFECEEF3, 0xFF8A96A8, 1.0F));
    private static final IGuiTexture BUTTON_HOVER_TEXTURE =
            new GuiTextureGroup(new ColorRectAndBorderTexture(0x00000000, 0xFF4F7FB6, 1.0F));

    private final List<Widget> ldWidgets = new ArrayList<>();
    private final List<ButtonRegion> buttonRegions = new ArrayList<>();

    private int displayBuildLength;
    private int minLength;
    private int maxLength;
    private StructureTerminalHostType selectedTarget;
    private StructureTerminalMode selectedMode;
    private List<NEStructureTerminalUiState.BuildMaterialEntry> materials;
    private int materialScrollOffset;
    private boolean hasActiveTarget;

    public NEStructureTerminalLDLibUI(NEStructureTerminalMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        this.imageWidth = WIDTH;
        this.imageHeight = HEIGHT;
        this.displayBuildLength = menu.getBuildLength();
        this.minLength = menu.getMinLength();
        this.maxLength = menu.getMaxLength();
        this.selectedTarget = menu.getHostType();
        this.selectedMode = menu.getOperationMode();
        this.materials = List.copyOf(menu.getMaterials());
        this.hasActiveTarget = selectedTarget != null;
    }

    public void setConfig(
            int length,
            int min,
            int max,
            StructureTerminalHostType target,
            StructureTerminalMode mode,
            List<NEStructureTerminalUiState.BuildMaterialEntry> materials) {
        boolean resetScroll = target != selectedTarget || materialsChanged(this.materials, materials);
        this.displayBuildLength = length;
        this.minLength = min;
        this.maxLength = max;
        this.selectedTarget = target;
        this.selectedMode = mode;
        this.hasActiveTarget = target != null;
        this.materials = List.copyOf(materials);
        this.menu.setClientConfig(length, min, max, target, mode, materials);
        this.materialScrollOffset = resetScroll ? 0 : clampMaterialScroll(this.materialScrollOffset);
    }

    @Override
    protected void init() {
        super.init();
        ldWidgets.clear();
        buttonRegions.clear();

        addLengthButton(
                CONTROL_ROW_X,
                LENGTH_ROW_Y,
                LENGTH_BUTTON_W,
                Component.literal("-"),
                NENetwork.NEStructureTerminalConfigActionPacket.Action.DECREASE);
        addLengthButton(
                CONTROL_ROW_X + LENGTH_BUTTON_W + 7 + LENGTH_VALUE_W + 7,
                LENGTH_ROW_Y,
                LENGTH_BUTTON_W,
                Component.literal("+"),
                NENetwork.NEStructureTerminalConfigActionPacket.Action.INCREASE);
        addActionButton(
                CONTROL_ROW_X,
                RESET_ROW_Y,
                CONTROL_ROW_W,
                BUTTON_H,
                Component.translatable("gui.neoecoae.structure_terminal.reset"),
                () -> false,
                NENetwork.NEStructureTerminalConfigActionPacket.Action.RESET);

        addModeButton(
                StructureTerminalMode.BUILD,
                0,
                Component.translatable("gui.neoecoae.structure_terminal.mode.build"),
                NENetwork.NEStructureTerminalConfigActionPacket.Action.SELECT_BUILD_MODE);
        addModeButton(
                StructureTerminalMode.MIRRORED_BUILD,
                1,
                Component.translatable("gui.neoecoae.structure_terminal.mode.mirrored_build"),
                NENetwork.NEStructureTerminalConfigActionPacket.Action.SELECT_MIRRORED_BUILD_MODE);
        addModeButton(
                StructureTerminalMode.DISMANTLE,
                2,
                Component.translatable("gui.neoecoae.structure_terminal.mode.dismantle"),
                NENetwork.NEStructureTerminalConfigActionPacket.Action.SELECT_DISMANTLE_MODE);

        addTargetButton(
                StructureTerminalHostType.CRAFTING,
                0,
                Component.translatable("gui.neoecoae.structure_terminal.target.crafting"),
                NENetwork.NEStructureTerminalConfigActionPacket.Action.SELECT_CRAFTING);
        addTargetButton(
                StructureTerminalHostType.STORAGE,
                1,
                Component.translatable("gui.neoecoae.structure_terminal.target.storage"),
                NENetwork.NEStructureTerminalConfigActionPacket.Action.SELECT_STORAGE);
        addTargetButton(
                StructureTerminalHostType.COMPUTATION,
                2,
                Component.translatable("gui.neoecoae.structure_terminal.target.computation"),
                NENetwork.NEStructureTerminalConfigActionPacket.Action.SELECT_COMPUTATION);
    }

    private void addLengthButton(
            int x, int y, int w, Component label, NENetwork.NEStructureTerminalConfigActionPacket.Action action) {
        addActionButton(x, y, w, BUTTON_H, label, () -> false, action);
    }

    private void addModeButton(
            StructureTerminalMode mode,
            int index,
            Component label,
            NENetwork.NEStructureTerminalConfigActionPacket.Action action) {
        addActionButton(
                CONTROL_ROW_X + index * (MODE_BUTTON_W + MODE_GAP),
                MODE_ROW_Y,
                MODE_BUTTON_W,
                BUTTON_H,
                label,
                () -> selectedMode == mode,
                action,
                () -> selectedMode = mode);
    }

    private void addTargetButton(
            StructureTerminalHostType target,
            int index,
            Component label,
            NENetwork.NEStructureTerminalConfigActionPacket.Action action) {
        int totalW = TARGET_BUTTON_W * 3 + TARGET_BUTTON_GAP * 2;
        int x = MATERIAL_X + (MATERIAL_W - totalW) / 2 + index * (TARGET_BUTTON_W + TARGET_BUTTON_GAP);
        addActionButton(
                x,
                TARGET_BUTTON_Y,
                TARGET_BUTTON_W,
                TARGET_BUTTON_H,
                label,
                () -> hasActiveTarget && selectedTarget == target,
                action,
                () -> {
                    hasActiveTarget = true;
                    selectedTarget = target;
                });
    }

    private void addActionButton(
            int x,
            int y,
            int w,
            int h,
            Component label,
            BooleanSupplier selectedSupplier,
            NENetwork.NEStructureTerminalConfigActionPacket.Action action) {
        addActionButton(x, y, w, h, label, selectedSupplier, action, () -> {});
    }

    private void addActionButton(
            int x,
            int y,
            int w,
            int h,
            Component label,
            BooleanSupplier selectedSupplier,
            NENetwork.NEStructureTerminalConfigActionPacket.Action action,
            Runnable optimisticClientUpdate) {
        ButtonWidget button = addLdWidget(new ButtonWidget(x, y, w, h, BUTTON_TEXTURE, click -> {
            optimisticClientUpdate.run();
            NENetwork.CHANNEL.sendToServer(new NENetwork.NEStructureTerminalConfigActionPacket(action));
        }));
        button.setHoverTexture(BUTTON_HOVER_TEXTURE);
        buttonRegions.add(new ButtonRegion(x, y, w, h, label, selectedSupplier, button));
    }

    private <W extends Widget> W addLdWidget(W widget) {
        widget.setClientSideWidget();
        widget.setParentPosition(new Position(leftPos, topPos));
        widget.initWidget();
        ldWidgets.add(widget);
        return widget;
    }

    @Override
    public void containerTick() {
        super.containerTick();
        for (Widget widget : ldWidgets) {
            widget.updateScreen();
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderMaterialItems(guiGraphics);
        renderButtonLabels(guiGraphics);
        renderTooltip(guiGraphics, mouseX, mouseY);
        renderStructureTerminalTooltips(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        BACKGROUND.draw(guiGraphics, mouseX, mouseY, leftPos, topPos, imageWidth, imageHeight);
        drawPanel(guiGraphics, CONTROL_X, CONTROL_Y, CONTROL_W, CONTROL_H);
        drawPanel(guiGraphics, MATERIAL_X, MATERIAL_Y, MATERIAL_W, MATERIAL_H);
        drawMaterialSlotGrid(guiGraphics);
        for (Widget widget : ldWidgets) {
            widget.drawInBackground(guiGraphics, mouseX, mouseY, partialTick);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(
                font,
                title,
                NENativeUiConstants.TITLE_X,
                NENativeUiConstants.TITLE_Y,
                NENativeUiConstants.MACHINE_TEXT_PRIMARY,
                false);

        drawCenteredFitted(
                guiGraphics,
                Component.literal("Length: " + displayBuildLength),
                CONTROL_X + 6,
                CONTROL_Y + 7,
                CONTROL_W - 12,
                TEXT_PRIMARY);
        drawCenteredFitted(
                guiGraphics,
                Component.literal("Min: " + minLength + "  Max: " + maxLength),
                CONTROL_X + 6,
                CONTROL_Y + 19,
                CONTROL_W - 12,
                TEXT_MUTED);

        String lengthValue = Integer.toString(displayBuildLength);
        int valueBoxX = CONTROL_ROW_X + LENGTH_BUTTON_W + 7;
        drawInsetValueLocal(guiGraphics, valueBoxX, LENGTH_ROW_Y, LENGTH_VALUE_W, BUTTON_H);
        drawCenteredFitted(
                guiGraphics, Component.literal(lengthValue), valueBoxX, LENGTH_ROW_Y + 5, LENGTH_VALUE_W, TEXT_VALUE);

        guiGraphics.drawString(
                font, Component.literal("Required Materials"), MATERIAL_X + 10, MATERIAL_Y + 8, TEXT_PRIMARY, false);
        if (materials.isEmpty()) {
            drawCenteredFitted(
                    guiGraphics,
                    Component.literal("No materials"),
                    MATERIAL_X,
                    MATERIAL_GRID_Y + 13,
                    MATERIAL_W,
                    TEXT_MUTED);
        }
        if (materials.size() > visibleMaterialSlots()) {
            int start = materialScrollOffset + 1;
            int end = Math.min(materialScrollOffset + visibleMaterialSlots(), materials.size());
            String pageText = start + "-" + end + "/" + materials.size();
            guiGraphics.drawString(
                    font,
                    Component.literal(pageText),
                    MATERIAL_X + MATERIAL_W - 10 - font.width(pageText),
                    MATERIAL_Y + MATERIAL_H - 13,
                    TEXT_MUTED,
                    false);
        }
    }

    private void renderButtonLabels(GuiGraphics g) {
        for (ButtonRegion region : buttonRegions) {
            int color = region.selectedSupplier().getAsBoolean() ? TEXT_SUCCESS : TEXT_VALUE;
            if (!region.button().isActive()) {
                color = TEXT_MUTED;
            }
            if (region.selectedSupplier().getAsBoolean()) {
                int x = leftPos + region.x();
                int y = topPos + region.y();
                g.fill(x + 3, y + region.h() - 4, x + region.w() - 3, y + region.h() - 3, TEXT_SUCCESS);
            }
            drawCenteredFitted(
                    g,
                    region.label(),
                    leftPos + region.x(),
                    topPos + region.y() + (region.h() - font.lineHeight) / 2,
                    region.w(),
                    color);
        }
    }

    private void renderMaterialItems(GuiGraphics g) {
        int count = Math.min(visibleMaterialSlots(), Math.max(0, materials.size() - materialScrollOffset));
        for (int i = 0; i < count; i++) {
            NEStructureTerminalUiState.BuildMaterialEntry entry = materials.get(materialScrollOffset + i);
            int x = leftPos + materialSlotX(i);
            int y = topPos + materialSlotY(i);
            g.renderItem(entry.item(), x + 1, y + 1);
            String text = "x" + formatCompactCount(entry.required());
            int textX = x + 17 - font.width(text);
            int textY = y + 10;
            g.pose().pushPose();
            g.pose().translate(0, 0, 200);
            g.drawString(font, Component.literal(text), textX, textY, materialCountColor(entry), true);
            g.pose().popPose();
        }
    }

    private void renderStructureTerminalTooltips(GuiGraphics g, int mouseX, int mouseY) {
        if (renderControlTooltip(g, mouseX, mouseY)) {
            return;
        }
        renderMaterialTooltip(g, mouseX, mouseY);
    }

    private boolean renderControlTooltip(GuiGraphics g, int mouseX, int mouseY) {
        if (isMouseIn(CONTROL_ROW_X, MODE_ROW_Y, MODE_BUTTON_W, BUTTON_H, mouseX, mouseY)) {
            g.renderComponentTooltip(
                    font,
                    List.of(Component.translatable("gui.neoecoae.structure_terminal.mode.build.tooltip")),
                    mouseX,
                    mouseY);
            return true;
        }
        if (isMouseIn(CONTROL_ROW_X + MODE_BUTTON_W + MODE_GAP, MODE_ROW_Y, MODE_BUTTON_W, BUTTON_H, mouseX, mouseY)) {
            g.renderComponentTooltip(
                    font,
                    List.of(Component.translatable("gui.neoecoae.structure_terminal.mode.mirrored_build.tooltip")),
                    mouseX,
                    mouseY);
            return true;
        }
        if (isMouseIn(
                CONTROL_ROW_X + (MODE_BUTTON_W + MODE_GAP) * 2, MODE_ROW_Y, MODE_BUTTON_W, BUTTON_H, mouseX, mouseY)) {
            g.renderComponentTooltip(
                    font,
                    List.of(Component.translatable("gui.neoecoae.structure_terminal.mode.dismantle.tooltip")),
                    mouseX,
                    mouseY);
            return true;
        }

        int totalW = TARGET_BUTTON_W * 3 + TARGET_BUTTON_GAP * 2;
        int startX = MATERIAL_X + (MATERIAL_W - totalW) / 2;
        if (isMouseIn(startX, TARGET_BUTTON_Y, TARGET_BUTTON_W, TARGET_BUTTON_H, mouseX, mouseY)) {
            g.renderComponentTooltip(
                    font,
                    List.of(Component.translatable("gui.neoecoae.structure_terminal.target.crafting.tooltip")),
                    mouseX,
                    mouseY);
            return true;
        }
        if (isMouseIn(
                startX + TARGET_BUTTON_W + TARGET_BUTTON_GAP,
                TARGET_BUTTON_Y,
                TARGET_BUTTON_W,
                TARGET_BUTTON_H,
                mouseX,
                mouseY)) {
            g.renderComponentTooltip(
                    font,
                    List.of(Component.translatable("gui.neoecoae.structure_terminal.target.storage.tooltip")),
                    mouseX,
                    mouseY);
            return true;
        }
        if (isMouseIn(
                startX + (TARGET_BUTTON_W + TARGET_BUTTON_GAP) * 2,
                TARGET_BUTTON_Y,
                TARGET_BUTTON_W,
                TARGET_BUTTON_H,
                mouseX,
                mouseY)) {
            g.renderComponentTooltip(
                    font,
                    List.of(Component.translatable("gui.neoecoae.structure_terminal.target.computation.tooltip")),
                    mouseX,
                    mouseY);
            return true;
        }
        return false;
    }

    private void renderMaterialTooltip(GuiGraphics g, int mouseX, int mouseY) {
        int index = materialIndexAt(mouseX, mouseY);
        if (index < 0 || index >= materials.size()) {
            return;
        }

        NEStructureTerminalUiState.BuildMaterialEntry entry = materials.get(index);
        int missing = entry.missing();
        g.renderTooltip(
                font,
                List.of(
                        entry.item().getHoverName(),
                        Component.literal("Required: " + entry.required()),
                        Component.literal("Available: " + entry.available()),
                        Component.literal("Missing: " + missing)),
                Optional.empty(),
                mouseX,
                mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (int i = ldWidgets.size() - 1; i >= 0; i--) {
            Widget widget = ldWidgets.get(i);
            if (widget.isVisible() && widget.isActive() && widget.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        for (int i = ldWidgets.size() - 1; i >= 0; i--) {
            Widget widget = ldWidgets.get(i);
            if (widget.isVisible() && widget.isActive() && widget.mouseReleased(mouseX, mouseY, button)) {
                return true;
            }
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (isInMaterialGrid(mouseX, mouseY) && materials.size() > visibleMaterialSlots()) {
            int next = materialScrollOffset + (delta < 0 ? MATERIAL_COLS : -MATERIAL_COLS);
            materialScrollOffset = clampMaterialScroll(next);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private void drawPanel(GuiGraphics g, int x, int y, int w, int h) {
        int left = leftPos + x;
        int top = topPos + y;
        g.fill(left, top, left + w, top + h, 0xFFC6CAD4);
        g.fill(left + 1, top + 1, left + w - 1, top + h - 1, 0xFFF5F6F8);
        g.fill(left + 2, top + 2, left + w - 2, top + h - 2, 0xFFE2E5EA);
    }

    private void drawInsetValueLocal(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0xFF8A96A8);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFFF5F6F8);
        g.fill(x + 2, y + 2, x + w - 2, y + h - 2, 0xFFD9DEE7);
    }

    private void drawMaterialSlotGrid(GuiGraphics g) {
        int count = visibleMaterialSlots();
        for (int i = 0; i < count; i++) {
            drawInventorySlot(g, leftPos + materialSlotX(i), topPos + materialSlotY(i));
        }
    }

    private void drawInventorySlot(GuiGraphics g, int x, int y) {
        g.fill(x, y, x + MATERIAL_SLOT_SIZE, y + MATERIAL_SLOT_SIZE, 0xFF9AA0AA);
        g.fill(x + 1, y + 1, x + MATERIAL_SLOT_SIZE - 1, y + MATERIAL_SLOT_SIZE - 1, 0xFFECEEF3);
        g.fill(x + 2, y + 2, x + MATERIAL_SLOT_SIZE - 2, y + MATERIAL_SLOT_SIZE - 2, 0xFFD7DAE2);
    }

    private int materialCountColor(NEStructureTerminalUiState.BuildMaterialEntry entry) {
        if (entry.available() >= entry.required()) {
            return TEXT_SUCCESS;
        }
        if (entry.available() > 0) {
            return TEXT_WARNING;
        }
        return TEXT_ERROR;
    }

    private boolean isInMaterialGrid(double mouseX, double mouseY) {
        int x = leftPos + MATERIAL_X;
        int y = topPos + MATERIAL_GRID_Y;
        return mouseX >= x && mouseX < x + MATERIAL_W && mouseY >= y && mouseY < y + MATERIAL_ROWS * MATERIAL_SLOT_SIZE;
    }

    private int materialIndexAt(double mouseX, double mouseY) {
        int slot = materialVisibleSlotAt(mouseX, mouseY);
        return slot < 0 ? -1 : materialScrollOffset + slot;
    }

    private int materialVisibleSlotAt(double mouseX, double mouseY) {
        for (int i = 0; i < visibleMaterialSlots(); i++) {
            int x = leftPos + materialSlotX(i);
            int y = topPos + materialSlotY(i);
            if (mouseX >= x && mouseX < x + MATERIAL_SLOT_SIZE && mouseY >= y && mouseY < y + MATERIAL_SLOT_SIZE) {
                return i;
            }
        }
        return -1;
    }

    private int materialSlotX(int visibleIndex) {
        int gridW = MATERIAL_COLS * MATERIAL_SLOT_SIZE;
        int startX = MATERIAL_X + (MATERIAL_W - gridW) / 2;
        return startX + (visibleIndex % MATERIAL_COLS) * MATERIAL_SLOT_SIZE;
    }

    private int materialSlotY(int visibleIndex) {
        return MATERIAL_GRID_Y + (visibleIndex / MATERIAL_COLS) * MATERIAL_SLOT_SIZE;
    }

    private int visibleMaterialSlots() {
        return MATERIAL_COLS * MATERIAL_ROWS;
    }

    private int clampMaterialScroll(int value) {
        int max = Math.max(0, materials.size() - visibleMaterialSlots());
        return Mth.clamp(value, 0, max);
    }

    private boolean isMouseIn(int x, int y, int w, int h, int mouseX, int mouseY) {
        return mouseX >= leftPos + x && mouseX < leftPos + x + w && mouseY >= topPos + y && mouseY < topPos + y + h;
    }

    private void drawCenteredFitted(GuiGraphics g, Component text, int x, int y, int w, int color) {
        int textWidth = font.width(text);
        int maxWidth = Math.max(1, w - 4);
        if (textWidth <= maxWidth) {
            g.drawString(font, text, x + (w - textWidth) / 2, y, color, false);
            return;
        }

        float scale = Math.max(0.55F, (float) maxWidth / (float) textWidth);
        g.pose().pushPose();
        g.pose().translate(x + w / 2.0F, y, 0);
        g.pose().scale(scale, scale, 1.0F);
        g.drawString(font, text, -textWidth / 2, 0, color, false);
        g.pose().popPose();
    }

    private static String formatCompactCount(int value) {
        if (value < 1000) {
            return Integer.toString(value);
        }
        if (value < 1_000_000) {
            return (value / 1000) + "K";
        }
        return (value / 1_000_000) + "M";
    }

    private static boolean materialsChanged(
            List<NEStructureTerminalUiState.BuildMaterialEntry> oldMaterials,
            List<NEStructureTerminalUiState.BuildMaterialEntry> newMaterials) {
        if (oldMaterials.size() != newMaterials.size()) {
            return true;
        }
        for (int i = 0; i < oldMaterials.size(); i++) {
            NEStructureTerminalUiState.BuildMaterialEntry oldEntry = oldMaterials.get(i);
            NEStructureTerminalUiState.BuildMaterialEntry newEntry = newMaterials.get(i);
            if (oldEntry.required() != newEntry.required() || oldEntry.available() != newEntry.available()) {
                return true;
            }
            ItemStack oldItem = oldEntry.item();
            ItemStack newItem = newEntry.item();
            if (!ItemStack.isSameItemSameTags(oldItem, newItem)) {
                return true;
            }
        }
        return false;
    }

    public NEStructureTerminalMenu getMenu() {
        return menu;
    }

    private record ButtonRegion(
            int x, int y, int w, int h, Component label, BooleanSupplier selectedSupplier, ButtonWidget button) {}
}
