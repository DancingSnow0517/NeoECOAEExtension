package cn.dancingsnow.neoecoae.gui.storage;

import cn.dancingsnow.neoecoae.api.storage.ECOCellType;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** Public storage host GUI entry points and immutable configuration contracts. */
public final class StorageHostPanelUI {
    public static final int LEFT_PANEL_WIDTH = 176;
    public static final int RIGHT_PANEL_WIDTH = 156;
    public static final int PANEL_HEIGHT = 200;

    private StorageHostPanelUI() {
    }

    public record StorageTypeLine(
        ECOCellType type,
        int registryIndex,
        LongSupplier usedTypes,
        LongSupplier totalTypes,
        BooleanSupplier infiniteTypes,
        LongSupplier usedBytes,
        LongSupplier totalBytes,
        Supplier<String> infiniteBytesText,
        Supplier<String> infiniteBytesTooltipText,
        Supplier<String> infiniteBytesExactText
    ) {
    }

    public record Config(
        LongSupplier storedEnergy,
        LongSupplier maxEnergy,
        LongSupplier maxLoadUsedBytes,
        LongSupplier maxLoadTotalBytes,
        IntSupplier idleMatrices,
        LongSupplier performanceAverageNanos,
        List<StorageTypeLine> storageTypes,
        BooleanSupplier migratingToInfinite,
        IntSupplier infiniteMigrationProgress,
        Supplier<Component> infiniteDomainStatus,
        BooleanSupplier infiniteDomainFailed,
        BooleanSupplier displayInfiniteStorageControls,
        BooleanSupplier canExtractInfiniteComponents,
        IItemHandlerModifiable infiniteComponentInventory,
        Supplier<HolderLookup.Provider> registries,
        Supplier<List<StorageHostHugeStackList.Entry>> hugeStacks
    ) {
    }

    public static UIElement createLeftPanel(Config config) {
        return StorageCapacityPanelUI.createLeftPanel(config);
    }

    public static ScrollerView createRightPanel(Config config) {
        return StorageSystemLoadPanelUI.createRightPanel(config);
    }

    public static ScrollerView createEmptyPanel(int width) {
        return StorageSystemLoadPanelUI.createEmptyPanel(width);
    }
}
