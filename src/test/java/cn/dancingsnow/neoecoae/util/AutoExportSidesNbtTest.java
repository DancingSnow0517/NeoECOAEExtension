package cn.dancingsnow.neoecoae.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import appeng.api.orientation.RelativeSide;
import java.util.EnumSet;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

class AutoExportSidesNbtTest {
    @Test
    void savesAndLoadsSelectedRelativeSides() {
        CompoundTag tag = new CompoundTag();
        EnumSet<RelativeSide> sides = EnumSet.of(RelativeSide.FRONT, RelativeSide.LEFT, RelativeSide.TOP);

        AutoExportSidesNbt.saveToTag(tag, sides);

        assertTrue(AutoExportSidesNbt.hasSavedSides(tag));
        assertEquals(sides, AutoExportSidesNbt.loadFromTag(tag));
    }

    @Test
    void preservesExplicitlyEmptySideSet() {
        CompoundTag tag = new CompoundTag();

        AutoExportSidesNbt.saveToTag(tag, EnumSet.noneOf(RelativeSide.class));

        assertTrue(AutoExportSidesNbt.hasSavedSides(tag));
        assertEquals(EnumSet.noneOf(RelativeSide.class), AutoExportSidesNbt.loadFromTag(tag));
    }

    @Test
    void ignoresInvalidSideOrdinals() {
        CompoundTag tag = new CompoundTag();
        ListTag savedSides = new ListTag();
        savedSides.add(IntTag.valueOf(RelativeSide.BACK.ordinal()));
        savedSides.add(IntTag.valueOf(-1));
        savedSides.add(IntTag.valueOf(RelativeSide.values().length));
        tag.put(AutoExportSidesNbt.TAG_AUTO_EXPORT_SIDES, savedSides);

        assertEquals(EnumSet.of(RelativeSide.BACK), AutoExportSidesNbt.loadFromTag(tag));
    }

    @Test
    void absentTagIsNotImportedAsSettings() {
        CompoundTag tag = new CompoundTag();

        assertFalse(AutoExportSidesNbt.hasSavedSides(tag));
        assertEquals(EnumSet.noneOf(RelativeSide.class), AutoExportSidesNbt.loadFromTag(tag));
    }
}
