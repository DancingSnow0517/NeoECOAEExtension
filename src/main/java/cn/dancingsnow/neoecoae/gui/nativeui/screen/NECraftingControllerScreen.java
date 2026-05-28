package cn.dancingsnow.neoecoae.gui.nativeui.screen;

import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingSystemBlockEntity;
import cn.dancingsnow.neoecoae.gui.nativeui.NENativeUiConstants;
import cn.dancingsnow.neoecoae.gui.nativeui.menu.NECraftingControllerMenu;
import cn.dancingsnow.neoecoae.network.NECraftingUiState;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Screen for the ECO Crafting Controller — machine running status only.
 * <p>
 * Building operations (preview, auto-build, length selection) have been
 * migrated to the {@link NEStructureTerminalScreen}, accessed via the
 * Structure Terminal item.
 * </p>
 */
public class NECraftingControllerScreen extends NEBaseMachineScreen<NECraftingControllerMenu> {

    private boolean hasCraftingState;
    private NECraftingUiState craftingState;

    public NECraftingControllerScreen(NECraftingControllerMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title, NEMachineScreenConfig.CRAFTING_CONTROLLER);
        this.imageWidth = 300;
        this.imageHeight = 170;
        this.craftingState = NECraftingUiState.empty(menu.getMachinePos());
    }

    /** Called from the network thread via {@link cn.dancingsnow.neoecoae.client.NEClientUiPacketHandlers}. */
    public void setCraftingUiState(NECraftingUiState state) {
        this.hasCraftingState = true;
        this.craftingState = state;
    }

    @Override
    protected void init() {
        super.init();
    }

    @Override
    protected void renderAdditionalLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        NECraftingUiState s;

        if (hasCraftingState) {
            s = this.craftingState;
        } else {
            ECOCraftingSystemBlockEntity be = getCraftingBE();
            if (be != null) {
                s = be.createCraftingUiState();
            } else {
                s = this.craftingState;
            }
        }

        final int x = NENativeUiConstants.TITLE_X;
        final int valueColor = NENativeUiConstants.PANEL_TEXT_SECONDARY;
        int y = 50;

        // Row 1: Formed / Active
        guiGraphics.drawString(font,
            Component.translatable("gui.neoecoae.machine.formed").append(": ").append(boolText(s.formed())),
            x, y, valueColor);
        guiGraphics.drawString(font,
            Component.translatable("gui.neoecoae.machine.active").append(": ").append(boolText(s.active())),
            x + 140, y, valueColor);
        y += 14;

        // Row 2: Workers / Parallel / Buses
        guiGraphics.drawString(font,
            Component.translatable("gui.neoecoae.machine.workers", s.workerCount()),
            x, y, valueColor);
        guiGraphics.drawString(font,
            Component.translatable("gui.neoecoae.machine.parallel", s.parallelCount()),
            x + 100, y, valueColor);
        guiGraphics.drawString(font,
            Component.translatable("gui.neoecoae.machine.patterns", s.patternBusCount()),
            x + 200, y, valueColor);
        y += 14;

        // Row 3: Threads
        guiGraphics.drawString(font,
            Component.translatable("gui.neoecoae.machine.threads_value", s.runningThreadCount(), s.threadCount()),
            x, y, valueColor);
        y += 14;

        // Row 4: Overclocked / Active Cooling
        guiGraphics.drawString(font,
            Component.translatable("gui.neoecoae.machine.overclocked").append(": ").append(boolText(s.overclocked())),
            x, y, valueColor);
        guiGraphics.drawString(font,
            Component.translatable("gui.neoecoae.machine.active_cooling").append(": ").append(boolText(s.activeCooling())),
            x + 140, y, valueColor);
        y += 14;

        // Row 5: Build hint
        guiGraphics.drawString(font,
            Component.translatable("gui.neoecoae.machine.use_structure_terminal"),
            x, y, NENativeUiConstants.PANEL_TEXT_HINT);
    }

    private ECOCraftingSystemBlockEntity getCraftingBE() {
        if (minecraft == null || minecraft.level == null) {
            return null;
        }
        BlockEntity be = minecraft.level.getBlockEntity(menu.getMachinePos());
        if (be instanceof ECOCraftingSystemBlockEntity crafting) {
            return crafting;
        }
        return null;
    }

    public NECraftingControllerMenu getMenu() {
        return menu;
    }
}
