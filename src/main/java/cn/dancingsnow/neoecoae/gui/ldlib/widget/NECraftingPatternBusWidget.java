package cn.dancingsnow.neoecoae.gui.ldlib.widget;

import static cn.dancingsnow.neoecoae.gui.ldlib.layout.NECraftingPatternBusLayout.*;

import appeng.core.definitions.AEItems;
import appeng.crafting.pattern.EncodedPatternItem;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingPatternBusBlockEntity;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NEForgeItemTransfer;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibAe2StyleRenderer;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibStyle;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NEPagedItemTransfer;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.widget.ButtonWidget;
import com.lowdragmc.lowdraglib.gui.widget.SlotWidget;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NECraftingPatternBusWidget extends NELDLibMachineWidget {
    private static final Logger LOGGER = LoggerFactory.getLogger(NeoECOAE.MOD_ID);
    private static final int PAGE_UPDATE_ID = 1;
    private static final int PAGE_ACTION_ID = 2;
    private static final int PAGE_BUTTON_Y = 4;
    private static final int PAGE_BUTTON_W = 12;
    private static final int PAGE_BUTTON_H = 14;
    private static final int PAGE_PREV_BUTTON_X = GUI_W - 34;
    private static final int PAGE_NEXT_BUTTON_X = GUI_W - 16;
    private static final int PAGE_TEXT_RIGHT_X = PAGE_PREV_BUTTON_X - 5;
    private static final int PAGE_TEXT_COLOR = 0xFFFFD24A;
    private static final float GHOST_ALPHA = 0.10F;

    private final ECOCraftingPatternBusBlockEntity bus;
    private final Inventory playerInventory;
    private final NEPagedItemTransfer pagedTransfer;
    private final ItemStack ghostPattern = AEItems.BLANK_PATTERN.stack();
    private final Map<String, ItemStack> patternDisplayCache = new HashMap<>();
    private final Set<String> failedPatternDisplayCache = new HashSet<>();

    private int currentPage;
    private int pageCount = 1;
    private ButtonWidget previousPageButton;
    private ButtonWidget nextPageButton;

    public NECraftingPatternBusWidget(ECOCraftingPatternBusBlockEntity bus, Inventory playerInventory) {
        super(bus.getBlockState().getBlock().getName(), GUI_W, GUI_H);
        this.bus = bus;
        this.playerInventory = playerInventory;
        this.pageCount = Math.max(1, bus.getPageCount());
        this.pagedTransfer = new NEPagedItemTransfer(
                new NEForgeItemTransfer(bus.itemHandler, bus::notifyPersistence),
                () -> currentPage,
                () -> pageCount,
                PATTERN_COLS * PATTERN_ROWS);
    }

    @Override
    protected boolean shouldAddTitleWidget() {
        return false;
    }

    @Override
    protected boolean shouldDrawBasePanel() {
        return false;
    }

    @Override
    protected void initLdWidgets() {
        for (int row = 0; row < PATTERN_ROWS; row++) {
            for (int col = 0; col < PATTERN_COLS; col++) {
                int slot = col + row * PATTERN_COLS;
                addWidget(aeSlot(
                        pagedTransfer,
                        slot,
                        PATTERN_BG_X + col * SLOT_SIZE,
                        PATTERN_BG_Y + row * SLOT_SIZE,
                        true,
                        true));
            }
        }

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addWidget(aeSlot(
                                playerInventory,
                                col + row * 9 + 9,
                                INV_BG_X + col * SLOT_SIZE,
                                INV_BG_Y + row * SLOT_SIZE,
                                true,
                                true)
                        .setLocationInfo(true, false));
            }
        }
        for (int col = 0; col < 9; col++) {
            addWidget(aeSlot(playerInventory, col, HOTBAR_BG_X + col * SLOT_SIZE, HOTBAR_BG_Y, true, true)
                    .setLocationInfo(true, true));
        }

        previousPageButton = (ButtonWidget) new ButtonWidget(
                PAGE_PREV_BUTTON_X,
                PAGE_BUTTON_Y,
                PAGE_BUTTON_W,
                PAGE_BUTTON_H,
                NELDLibStyle.aeToolbarButton(),
                click -> {
                    if (click.isRemote) {
                        writeClientAction(PAGE_ACTION_ID, buf -> buf.writeVarInt(currentPage - 1));
                    }
                });
        nextPageButton = (ButtonWidget) new ButtonWidget(
                PAGE_NEXT_BUTTON_X,
                PAGE_BUTTON_Y,
                PAGE_BUTTON_W,
                PAGE_BUTTON_H,
                NELDLibStyle.aeToolbarButton(),
                click -> {
                    if (click.isRemote) {
                        writeClientAction(PAGE_ACTION_ID, buf -> buf.writeVarInt(currentPage + 1));
                    }
                });
        addWidget(previousPageButton);
        addWidget(nextPageButton);
        updatePageButtons();
    }

    private SlotWidget aeSlot(
            com.lowdragmc.lowdraglib.side.item.IItemTransfer transfer,
            int index,
            int x,
            int y,
            boolean canTake,
            boolean canPut) {
        return new SlotWidget(transfer, index, x, y, canTake, canPut)
                .setBackgroundTexture(IGuiTexture.EMPTY)
                .setItemHook(this::patternDisplayStack);
    }

    private SlotWidget aeSlot(
            net.minecraft.world.Container container, int index, int x, int y, boolean canTake, boolean canPut) {
        return new SlotWidget(container, index, x, y, canTake, canPut).setBackgroundTexture(IGuiTexture.EMPTY);
    }

    @Override
    public void handleClientAction(int id, FriendlyByteBuf buffer) {
        if (id == PAGE_ACTION_ID) {
            changePage(buffer.readVarInt());
            return;
        }
        super.handleClientAction(id, buffer);
    }

    @Override
    public void writeInitialData(FriendlyByteBuf buffer) {
        refreshPageCount();
        buffer.writeVarInt(currentPage);
        buffer.writeVarInt(pageCount);
        super.writeInitialData(buffer);
    }

    @Override
    public void readInitialData(FriendlyByteBuf buffer) {
        currentPage = buffer.readVarInt();
        pageCount = Math.max(1, buffer.readVarInt());
        updatePageButtons();
        super.readInitialData(buffer);
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();
        int oldPage = currentPage;
        int oldPageCount = pageCount;
        refreshPageCount();
        if (oldPage != currentPage || oldPageCount != pageCount) {
            writePageUpdate();
        }
    }

    @Override
    public void readUpdateInfo(int id, FriendlyByteBuf buffer) {
        if (id == PAGE_UPDATE_ID) {
            currentPage = buffer.readVarInt();
            pageCount = Math.max(1, buffer.readVarInt());
            updatePageButtons();
            return;
        }
        super.readUpdateInfo(id, buffer);
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        updatePageButtons();
    }

    @Override
    protected void drawMachineBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        NELDLibAe2StyleRenderer.drawAeMainPanel(graphics, getPositionX(), getPositionY(), GUI_W, GUI_H);
        for (int row = 0; row < PATTERN_ROWS; row++) {
            for (int col = 0; col < PATTERN_COLS; col++) {
                NELDLibAe2StyleRenderer.drawAeSlot(
                        graphics, absX(PATTERN_BG_X + col * SLOT_SIZE), absY(PATTERN_BG_Y + row * SLOT_SIZE));
            }
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                NELDLibAe2StyleRenderer.drawAeSlot(
                        graphics, absX(INV_BG_X + col * SLOT_SIZE), absY(INV_BG_Y + row * SLOT_SIZE));
            }
        }
        for (int col = 0; col < 9; col++) {
            NELDLibAe2StyleRenderer.drawAeSlot(graphics, absX(HOTBAR_BG_X + col * SLOT_SIZE), absY(HOTBAR_BG_Y));
        }
        drawGhostPatterns(graphics);
    }

    @Override
    protected void drawMachineForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        drawTitleAndPage(graphics);
        drawLocalString(graphics, playerInventory.getDisplayName(), INV_LABEL_X, INV_LABEL_Y, TEXT_MUTED);
        drawPageButtonText(graphics);
    }

    @Override
    protected void drawMachineTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
        if (previousPageButton != null
                && previousPageButton.isVisible()
                && previousPageButton.isMouseOverElement(mouseX, mouseY)) {
            graphics.renderComponentTooltip(
                    font(),
                    java.util.List.of(Component.translatable("gui.neoecoae.pattern_bus.previous_page")),
                    mouseX,
                    mouseY);
            return;
        }
        if (nextPageButton != null && nextPageButton.isVisible() && nextPageButton.isMouseOverElement(mouseX, mouseY)) {
            graphics.renderComponentTooltip(
                    font(),
                    java.util.List.of(Component.translatable("gui.neoecoae.pattern_bus.next_page")),
                    mouseX,
                    mouseY);
        }
    }

    private void changePage(int targetPage) {
        refreshPageCount();
        if (targetPage < 0 || targetPage >= pageCount || targetPage == currentPage) {
            return;
        }
        currentPage = targetPage;
        updatePageButtons();
        writePageUpdate();
    }

    private void refreshPageCount() {
        pageCount = Math.max(1, bus.getPageCount());
        if (currentPage >= pageCount) {
            currentPage = pageCount - 1;
        }
        if (currentPage < 0) {
            currentPage = 0;
        }
    }

    private void writePageUpdate() {
        writeUpdateInfo(PAGE_UPDATE_ID, buf -> {
            buf.writeVarInt(currentPage);
            buf.writeVarInt(pageCount);
        });
    }

    private void updatePageButtons() {
        boolean visible = pageCount > 1;
        if (previousPageButton != null) {
            previousPageButton.setVisible(visible);
            previousPageButton.setActive(visible && currentPage > 0);
        }
        if (nextPageButton != null) {
            nextPageButton.setVisible(visible);
            nextPageButton.setActive(visible && currentPage + 1 < pageCount);
        }
    }

    private void drawGhostPatterns(GuiGraphics graphics) {
        RenderSystem.enableBlend();
        for (int i = 0; i < PATTERN_COLS * PATTERN_ROWS; i++) {
            if (pagedTransfer.getStackInSlot(i).isEmpty()) {
                int col = i % PATTERN_COLS;
                int row = i / PATTERN_COLS;
                RenderSystem.setShaderColor(1, 1, 1, GHOST_ALPHA);
                graphics.renderItem(
                        ghostPattern, absX(PATTERN_SLOT_X + col * SLOT_SIZE), absY(PATTERN_SLOT_Y + row * SLOT_SIZE));
                RenderSystem.setShaderColor(1, 1, 1, 1);
            }
        }
        RenderSystem.disableBlend();
    }

    private void drawTitleAndPage(GuiGraphics graphics) {
        boolean paged = pageCount > 1;
        String pageText = paged ? (currentPage + 1) + " / " + pageCount : "";
        int titleMaxWidth;
        if (paged) {
            int pageTextX = PAGE_TEXT_RIGHT_X - font().width(pageText);
            titleMaxWidth = Math.max(0, pageTextX - TITLE_X - 6);
        } else {
            titleMaxWidth = GUI_W - TITLE_X - 8;
        }
        Component displayTitle = truncateTitle(title, titleMaxWidth);
        graphics.drawString(font(), displayTitle, absX(TITLE_X), absY(TITLE_Y), TEXT_PRIMARY, false);
        if (paged) {
            int pageTextX = PAGE_TEXT_RIGHT_X - font().width(pageText);
            graphics.drawString(font(), pageText, absX(pageTextX), absY(TITLE_Y), PAGE_TEXT_COLOR, true);
        }
    }

    private void drawPageButtonText(GuiGraphics graphics) {
        if (previousPageButton != null && previousPageButton.isVisible()) {
            int color = previousPageButton.isActive() ? TEXT_PRIMARY : TEXT_MUTED;
            graphics.drawString(font(), "<", absX(PAGE_PREV_BUTTON_X + 3), absY(PAGE_BUTTON_Y + 3), color, false);
        }
        if (nextPageButton != null && nextPageButton.isVisible()) {
            int color = nextPageButton.isActive() ? TEXT_PRIMARY : TEXT_MUTED;
            graphics.drawString(font(), ">", absX(PAGE_NEXT_BUTTON_X + 3), absY(PAGE_BUTTON_Y + 3), color, false);
        }
    }

    private Component truncateTitle(Component text, int maxWidth) {
        String raw = text.getString();
        if (maxWidth <= 0) {
            return Component.empty();
        }
        if (font().width(raw) <= maxWidth) {
            return text;
        }
        String ellipsis = "...";
        int ellipsisWidth = font().width(ellipsis);
        if (maxWidth <= ellipsisWidth) {
            return Component.literal(font().plainSubstrByWidth(raw, maxWidth));
        }
        return Component.literal(font().plainSubstrByWidth(raw, maxWidth - ellipsisWidth) + ellipsis);
    }

    private ItemStack getPatternDisplay(ItemStack patternStack) {
        if (!(patternStack.getItem() instanceof EncodedPatternItem patternItem)) {
            return ItemStack.EMPTY;
        }
        String tagKey = patternStack.getTag() != null ? patternStack.getTag().toString() : "{}";
        String key = BuiltInRegistries.ITEM.getKey(patternStack.getItem()) + "|" + tagKey;
        if (failedPatternDisplayCache.contains(key)) {
            return ItemStack.EMPTY;
        }
        ItemStack cached = patternDisplayCache.get(key);
        if (cached != null) {
            return cached.copy();
        }
        try {
            ItemStack output = patternItem.getOutput(patternStack);
            if (!output.isEmpty()) {
                patternDisplayCache.put(key, output.copy());
                return output;
            }
        } catch (RuntimeException e) {
            failedPatternDisplayCache.add(key);
            LOGGER.debug("Unable to resolve encoded pattern output for smart pattern bus display: {}", key, e);
            return ItemStack.EMPTY;
        }
        return ItemStack.EMPTY;
    }

    private ItemStack patternDisplayStack(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof EncodedPatternItem)) {
            return stack;
        }
        ItemStack display = getPatternDisplay(stack);
        return display.isEmpty() ? stack : display;
    }
}
