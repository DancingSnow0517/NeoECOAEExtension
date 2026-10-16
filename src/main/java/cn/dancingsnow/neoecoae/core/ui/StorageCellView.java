package cn.dancingsnow.neoecoae.core.ui;

/** Numeric cell information needed by the storage view; icons and interactions belong to the platform. */
public interface StorageCellView {
    boolean infinite();
    long usedBytes();
    long totalBytes();
}
