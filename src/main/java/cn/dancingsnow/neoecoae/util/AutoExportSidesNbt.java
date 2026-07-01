package cn.dancingsnow.neoecoae.util;

import appeng.api.orientation.RelativeSide;
import java.util.EnumSet;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;

public final class AutoExportSidesNbt {
    public static final String TAG_AUTO_EXPORT_SIDES = "auto_export_sides";

    private AutoExportSidesNbt() {}

    public static void saveToTag(CompoundTag tag, Set<RelativeSide> sides) {
        ListTag savedSides = new ListTag();
        for (RelativeSide side : RelativeSide.values()) {
            if (sides.contains(side)) {
                savedSides.add(IntTag.valueOf(side.ordinal()));
            }
        }
        tag.put(TAG_AUTO_EXPORT_SIDES, savedSides);
    }

    public static EnumSet<RelativeSide> loadFromTag(CompoundTag tag) {
        EnumSet<RelativeSide> sides = EnumSet.noneOf(RelativeSide.class);
        if (!hasSavedSides(tag)) {
            return sides;
        }

        ListTag savedSides = tag.getList(TAG_AUTO_EXPORT_SIDES, 3);
        RelativeSide[] values = RelativeSide.values();
        for (int i = 0; i < savedSides.size(); i++) {
            int sideIndex = savedSides.getInt(i);
            if (sideIndex >= 0 && sideIndex < values.length) {
                sides.add(values[sideIndex]);
            }
        }
        return sides;
    }

    public static boolean hasSavedSides(CompoundTag tag) {
        return tag.contains(TAG_AUTO_EXPORT_SIDES, 9);
    }
}
