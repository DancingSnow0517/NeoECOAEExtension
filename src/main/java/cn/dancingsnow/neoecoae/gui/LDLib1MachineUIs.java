package cn.dancingsnow.neoecoae.gui;

import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.blocks.entity.ECOIntegratedWorkingStationBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationSystemBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingPatternBusBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingSystemBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOFluidInputHatchBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOFluidOutputHatchBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.storage.ECOStorageSystemBlockEntity;
import cn.dancingsnow.neoecoae.gui.ldlib1.MultiblockBuildUiAdapter;
import cn.dancingsnow.neoecoae.gui.ldlib1.NELDLib1Textures;
import cn.dancingsnow.neoecoae.gui.ldlib1.NELDLib1Widgets;
import com.lowdragmc.lowdraglib.gui.modular.IUIHolder;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/**
 * LDLib1 machine UI factory — temporary minimal UI during rebuild.
 *
 * <p>All machine UIs now show only the block name and a "rebuilding" notice.
 * Complex UI (builder, text panel, toolbar, floating windows, slots, tanks,
 * progress bars, pattern grids) is temporarily disabled while the LDLib1
 * widget system is rewritten from scratch.</p>
 *
 * <p>Business logic (storage, computation, crafting, etc.) is NOT modified.</p>
 */
public final class LDLib1MachineUIs {

    private LDLib1MachineUIs() {
    }

    // =========================================================================
    // Minimal UI helper — used by all machines during UI rebuild
    // =========================================================================

    private static ModularUI minimal(IUIHolder holder, Player player, Component title) {
        var ui = new ModularUI(176, 60, holder, player)
            .background(NELDLib1Textures.BACKGROUND);
        ui.widget(NELDLib1Widgets.title(8, 8, title));
        ui.widget(NELDLib1Widgets.label(8, 24,
            Component.translatable("gui.neoecoae.ui.rebuilding")));
        return ui;
    }

    // =========================================================================
    // Integrated Working Station
    // =========================================================================

    public static ModularUI createIntegratedWorkingStationUI(ECOIntegratedWorkingStationBlockEntity be, Player player) {
        return minimal(be, player, be.getBlockState().getBlock().getName());
    }

    // =========================================================================
    // Storage Controller
    // =========================================================================

    public static ModularUI createStorageSystemUI(ECOStorageSystemBlockEntity be, Player player) {
        return minimal(be, player,
            shortTitle("gui.neoecoae.ui.storage_system.short", be.getTier()));
    }

    // =========================================================================
    // Computation Controller
    // =========================================================================

    public static ModularUI createComputationSystemUI(ECOComputationSystemBlockEntity be, Player player) {
        return minimal(be, player,
            shortTitle("gui.neoecoae.ui.computation_system.short", be.getTier()));
    }

    // =========================================================================
    // Crafting Controller
    // =========================================================================

    public static ModularUI createCraftingControllerUI(ECOCraftingSystemBlockEntity be, Player player) {
        return minimal(be, player, be.getBlockState().getBlock().getName());
    }

    // =========================================================================
    // Smart Pattern Bus
    // =========================================================================

    public static ModularUI createCraftingPatternBusUI(ECOCraftingPatternBusBlockEntity be, Player player) {
        return minimal(be, player, be.getBlockState().getBlock().getName());
    }

    // =========================================================================
    // Fluid Input/Output Hatch
    // =========================================================================

    public static ModularUI createFluidHatchUI(ECOFluidInputHatchBlockEntity be, Player player, String titleKey) {
        return minimal(be, player, Component.translatable(titleKey));
    }

    public static ModularUI createFluidHatchUI(ECOFluidOutputHatchBlockEntity be, Player player, String titleKey) {
        return minimal(be, player, Component.translatable(titleKey));
    }

    // =========================================================================
    // Formatting helpers
    // =========================================================================

    /**
     * Build a short controller title using a translatable key with a tier format arg.
     * Example: shortTitle("gui.neoecoae.ui.storage_system.short", be.getTier())
     * produces "ECO - L9 Storage System" (en) / "ECO - L9 存储系统" (zh_cn).
     */
    private static Component shortTitle(String key, IECOTier tier) {
        return Component.translatable(key, tier.toString());
    }

    // =========================================================================
    // Adapter factories (preserved for future UI rewrite, not called by minimal UI)
    // =========================================================================

    private static MultiblockBuildUiAdapter storageAdapter(ECOStorageSystemBlockEntity be) {
        return new MultiblockBuildUiAdapter() {
            @Override public int getSelectedBuildLength() { return be.getSelectedBuildLength(); }
            @Override public int getPreviewMissingBlocks() { return be.getPreviewMissingBlocks(); }
            @Override public int getPreviewConflictBlocks() { return be.getPreviewConflictBlocks(); }
            @Override public int getPreviewReusedBlocks() { return be.getPreviewReusedBlocks(); }
            @Override public int getPreviewRequiredItems() { return be.getPreviewRequiredItems(); }
            @Override public Component getPreviewStatusComponent() { return be.getPreviewStatusComponent(); }
            @Override public void decreaseBuildLength() { be.decreaseBuildLength(); }
            @Override public void increaseBuildLength() { be.increaseBuildLength(); }
            @Override public void previewStructure(Player player) { be.previewStructure(player); }
            @Override public void autoBuild(Player player) { be.autoBuild(player); }
            @Override public boolean isBuildInProgress() { return be.isBuildInProgress(); }
            @Override public boolean isFormed() { return be.isFormed(); }
        };
    }

    private static MultiblockBuildUiAdapter computationAdapter(ECOComputationSystemBlockEntity be) {
        return new MultiblockBuildUiAdapter() {
            @Override public int getSelectedBuildLength() { return be.getSelectedBuildLength(); }
            @Override public int getPreviewMissingBlocks() { return be.getPreviewMissingBlocks(); }
            @Override public int getPreviewConflictBlocks() { return be.getPreviewConflictBlocks(); }
            @Override public int getPreviewReusedBlocks() { return be.getPreviewReusedBlocks(); }
            @Override public int getPreviewRequiredItems() { return be.getPreviewRequiredItems(); }
            @Override public Component getPreviewStatusComponent() { return be.getPreviewStatusComponent(); }
            @Override public void decreaseBuildLength() { be.decreaseBuildLength(); }
            @Override public void increaseBuildLength() { be.increaseBuildLength(); }
            @Override public void previewStructure(Player player) { be.previewStructure(player); }
            @Override public void autoBuild(Player player) { be.autoBuild(player); }
            @Override public boolean isBuildInProgress() { return be.isBuildInProgress(); }
            @Override public boolean isFormed() { return be.isFormed(); }
        };
    }

    private static MultiblockBuildUiAdapter craftingAdapter(ECOCraftingSystemBlockEntity be) {
        return new MultiblockBuildUiAdapter() {
            @Override public int getSelectedBuildLength() { return be.getSelectedBuildLength(); }
            @Override public int getPreviewMissingBlocks() { return be.getPreviewMissingBlocks(); }
            @Override public int getPreviewConflictBlocks() { return be.getPreviewConflictBlocks(); }
            @Override public int getPreviewReusedBlocks() { return be.getPreviewReusedBlocks(); }
            @Override public int getPreviewRequiredItems() { return be.getPreviewRequiredItems(); }
            @Override public Component getPreviewStatusComponent() { return be.getPreviewStatusComponent(); }
            @Override public void decreaseBuildLength() { be.decreaseBuildLength(); }
            @Override public void increaseBuildLength() { be.increaseBuildLength(); }
            @Override public void previewStructure(Player player) { be.previewStructure(player); }
            @Override public void autoBuild(Player player) { be.autoBuild(player); }
            @Override public boolean isBuildInProgress() { return be.isBuildInProgress(); }
            @Override public boolean isFormed() { return be.isFormed(); }
        };
    }
}
