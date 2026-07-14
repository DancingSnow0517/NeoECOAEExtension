package cn.dancingsnow.neoecoae.gui.ldlib.storage;

import net.minecraft.util.Mth;

/** Pure scrollbar geometry shared by rendering, dragging and unit tests. */
public final class NEStorageScrollbar {
    public static int thumbOffset(double scrollPixels, double maxScroll, int trackHeight) {
        if (maxScroll <= 0.0D) {
            return 0;
        }
        int travel = Math.max(0, trackHeight - NEStorageLayout.STORAGE_SCROLLBAR_THUMB_H);
        return (int) Math.round(travel * scrollPixels / maxScroll);
    }

    public static double scrollFromMouse(double mouseY, int trackY, int trackHeight, double maxScroll) {
        if (maxScroll <= 0.0D) {
            return 0.0D;
        }
        int travel = Math.max(1, trackHeight - NEStorageLayout.STORAGE_SCROLLBAR_THUMB_H);
        double relative = mouseY - trackY - NEStorageLayout.STORAGE_SCROLLBAR_THUMB_H / 2.0D;
        return Mth.clamp(relative * maxScroll / travel, 0.0D, maxScroll);
    }

    private NEStorageScrollbar() {}
}
