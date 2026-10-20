package cn.dancingsnow.neoecoae.gui.crafting;

import cn.dancingsnow.neoecoae.crafting.display.CraftingOutputDisplay;
import java.util.List;
import net.minecraftforge.fluids.FluidStack;

/** Values received for a single open controller menu; contains no widgets or renderer. */
final class CraftingControllerViewState {
    CraftingControllerPanelState state = CraftingControllerPanelState.UNFORMED;
    long energyUsage;
    long performanceAverageNanos;
    long queuedCraftCount;
    long batchCapacity;
    long ftParallelCapacity;
    long overflowCapacity;
    int coolantAmount;
    int coolantCapacity;
    int coolantMaxOverclock;
    int outputCapacity;
    int activeWorkerCores;
    int workerCores;
    int singleCoreCapacity;
    int theoreticalOverclockTimes;
    int effectiveOverclockTimes;
    int executionTicks;
    int networkMultiplier;
    int taskCount;
    int normalSwitchCount;
    int highEnergySwitchCount;
    boolean networkConnected;
    boolean overclocked;
    boolean virtualMode;
    boolean activeCooling;
    FluidStack coolantFluid = FluidStack.EMPTY;
    FluidStack outputFluid = FluidStack.EMPTY;

    List<CraftingOutputDisplay> workerOutputs = List.of();
}
