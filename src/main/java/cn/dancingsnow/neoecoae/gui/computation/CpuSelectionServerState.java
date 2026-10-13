package cn.dancingsnow.neoecoae.gui.computation;

import cn.dancingsnow.neoecoae.crafting.execution.ECOCraftingCPU;

/** Menu-side CPU selection shared by the CPU list and status views; both drop requests while the menu cannot interact. */
abstract class CpuSelectionServerState {
    protected final ComputationHostPanelUI.Config config;
    protected final CpuSelectionState<ECOCraftingCPU> selection;

    CpuSelectionServerState(ComputationHostPanelUI.Config config, int rows, CpuSelectionState.Identities<ECOCraftingCPU> identities) {
        this.config = config;
        this.selection = new CpuSelectionState<>(rows, identities);
    }

    protected void refresh() {
        selection.update(config.cpus().get(), ECOCraftingCPU::isBusy,
            cpu -> cpu.getName() == null ? null : cpu.getName().getString());
    }

    /** Returns false without touching selection when the menu cannot interact; otherwise syncs the CPU list first. */
    protected boolean beginInteraction() {
        if (!config.canInteract().getAsBoolean()) return false;
        refresh();
        return true;
    }
}
