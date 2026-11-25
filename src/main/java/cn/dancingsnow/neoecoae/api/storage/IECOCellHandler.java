package cn.dancingsnow.neoecoae.api.storage;

import appeng.api.storage.cells.ISaveProvider;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public interface IECOCellHandler {

    boolean isCell(ItemStack stack);

    @Nullable IECOStorageCell getCellInventory(ItemStack is, @Nullable ISaveProvider host);

    default boolean isEmptyInfiniteCandidate(ItemStack stack, ISaveProvider host,
            cn.dancingsnow.neoecoae.api.IECOTier hostTier, @Nullable IECOStorageCell mounted) {
        return mounted instanceof IECOStorageMigrationCell migration && mounted.isInfiniteStorageEligible()
                && mounted.getTier().supportsFeature(cn.dancingsnow.neoecoae.api.capability.ECOFeature.INFINITE_STORAGE_CELL)
                && hostTier.compareTo(mounted.getTier()) >= 0 && migration.isEmptyForInfiniteStorage();
    }

    default void onCellInstalled(ItemStack stack, ISaveProvider host) {
    }

    default void releaseCellInventory(@Nullable ItemStack stack, @Nullable ISaveProvider host) {
    }

    default void clearRuntimeState() {
    }
}
