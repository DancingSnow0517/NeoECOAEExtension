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
 * Screen for the ECO Crafting Controller with live read-only status.
 * <p>
 * Primary display path: S2C {@link NECraftingUiState} pushed from the server
 * menu tick. Before the first packet arrives the screen shows a brief fallback
 * read from the client-side BE (opening-time snapshot, not live).
 * </p>
 */
public class NECraftingControllerScreen extends NEBaseMachineScreen<NECraftingControllerMenu> {

    private boolean hasCraftingState;
    private NECraftingUiState craftingState;

    public NECraftingControllerScreen(NECraftingControllerMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title, NEMachineScreenConfig.CRAFTING_CONTROLLER);
        this.imageWidth = 320;
        this.imageHeight = 190;
        this.craftingState = NECraftingUiState.empty(menu.getMachinePos());
    }

    /** Called from the network thread via {@link cn.dancingsnow.neoecoae.client.NEClientUiPacketHandlers}. */
    public void setCraftingUiState(NECraftingUiState state) {
        this.hasCraftingState = true;
        this.craftingState = state;
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
        final int labelColor = 0xFF8A8AA0;
        final int valueColor = 0xFFC0C0D0;
        int y = 50;

        // Row 1: Formed / Active
        guiGraphics.drawString(font,
            Component.literal("Formed: " + s.formed()),
            x, y, valueColor);
        guiGraphics.drawString(font,
            Component.literal("Active: " + s.active()),
            x + 140, y, valueColor);
        y += 14;

        // Row 2: Workers / Parallel / Buses
        guiGraphics.drawString(font,
            Component.literal("Workers: " + s.workerCount()),
            x, y, valueColor);
        guiGraphics.drawString(font,
            Component.literal("Parallel: " + s.parallelCount()),
            x + 100, y, valueColor);
        guiGraphics.drawString(font,
            Component.literal("Patterns: " + s.patternBusCount()),
            x + 200, y, valueColor);
        y += 14;

        // Row 3: Threads
        guiGraphics.drawString(font,
            Component.literal("Threads: " + s.runningThreadCount() + " / " + s.threadCount()),
            x, y, valueColor);
        y += 14;

        // Row 4: Overclocked / Active Cooling
        guiGraphics.drawString(font,
            Component.literal("Overclocked: " + s.overclocked()),
            x, y, valueColor);
        guiGraphics.drawString(font,
            Component.literal("Active Cooling: " + s.activeCooling()),
            x + 140, y, valueColor);
        y += 14;

        // Row 5: Build Length
        guiGraphics.drawString(font,
            Component.literal("Build Length: " + s.selectedBuildLength()),
            x, y, valueColor);
        guiGraphics.drawString(font,
            Component.literal("Builder: " + (s.buildInProgress() ? "Running" : "Idle")),
            x + 140, y, valueColor);
        y += 14;

        // Row 6: Preview status
        Component statusComponent;
        try {
            statusComponent = Component.translatable(s.previewStatusKey(), s.previewStatusArg1(), s.previewStatusArg2());
        } catch (Exception ignored) {
            statusComponent = Component.literal(s.previewStatusKey());
        }
        guiGraphics.drawString(font,
            Component.literal("Preview: ").append(statusComponent),
            x, y, labelColor);
        y += 14;

        // Row 7: Missing / Conflicts / Reused / Required
        guiGraphics.drawString(font,
            Component.literal("M:" + s.previewMissingBlocks() +
                " C:" + s.previewConflictBlocks() +
                " R:" + s.previewReusedBlocks() +
                " Req:" + s.previewRequiredItems()),
            x, y, labelColor);
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
