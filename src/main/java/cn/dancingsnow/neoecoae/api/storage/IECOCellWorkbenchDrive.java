package cn.dancingsnow.neoecoae.api.storage;

import org.jetbrains.annotations.Nullable;

/** A live installed cell exposed to the shared workbench, without an ECO block entity dependency. */
public interface IECOCellWorkbenchDrive extends IECOConfigurableCellDrive, ICellHost {
    @Override
    boolean canExtractCell();

    /** The live mounted inventory; the workbench persists it before exposing its installed stack. */
    @Nullable IECOStorageCell getCellInventory();

    /**
     * Persist contents, invalidate the old handler and refresh storage mounts after an in-place
     * filter, fuzzy-mode or upgrade change. Must not replace the installed stack or its UUID.
     */
    void reloadCellConfiguration();
}
