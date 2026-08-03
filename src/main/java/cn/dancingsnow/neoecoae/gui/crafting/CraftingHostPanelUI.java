package cn.dancingsnow.neoecoae.gui.crafting;

import cn.dancingsnow.neoecoae.multiblock.cluster.NECraftingNetworkCluster;
import cn.dancingsnow.neoecoae.gui.task.ComputationTaskEntry;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

public final class CraftingHostPanelUI {
    public static final int UI_WIDTH = 304;
    public static final int UI_HEIGHT = 208;

    private CraftingHostPanelUI() {
    }

    public record Config(
            Supplier<Component> title,
            IntSupplier networkMultiplier,
            BooleanSupplier networkConnected,
            IntSupplier networkFrequency,
            IntConsumer adjustNetworkFrequency,
            IntSupplier runStatus,
            BooleanSupplier overclocked,
            Runnable toggleOverclocked,
            BooleanSupplier activeCooling,
            Runnable toggleActiveCooling,
            IntSupplier occupiedCraftingSlots,
            IntSupplier maxCraftingSlots,
            IntSupplier maxBatchPerThread,
            Supplier<List<NECraftingNetworkCluster.HostBatchInfo>> hostBatchInfos,
            IntSupplier overflowThreads,
            IntSupplier effectiveOverclockTimes,
            LongSupplier performanceAverageNanos,
            LongSupplier energyUsage,
            IntSupplier coolantAmount,
            IntSupplier coolantCapacity,
            IntSupplier coolantMaxOverclock,
            Supplier<FluidStack> coolantFluid,
            Supplier<HolderLookup.Provider> registries,
            Supplier<List<ComputationTaskEntry>> tasks) {
    }

    public static UIElement create(Config config) {
        UIElement root = new UIElement()
                .addClasses("eco-host-panel", "eco-crafting-host")
                .layout(layout -> layout
                        .width(UI_WIDTH)
                        .height(UI_HEIGHT)
                        .flexDirection(FlexDirection.COLUMN));
        root.addChildren(CraftingHeaderUI.create(config), CraftingTopPanelsUI.create(config),
                CraftingBottomPanelsUI.create(config));
        return root;
    }
}
