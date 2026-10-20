package cn.dancingsnow.neoecoae.api.components;

import appeng.api.orientation.RelativeSide;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

public record AutoExportSides(Set<RelativeSide> sides) {

    public static final String NBT_KEY = "neoecoae_auto_export_sides";

    public AutoExportSides { sides = Set.copyOf(sides); }

    public void write(CompoundTag tag) {
        tag.putIntArray(NBT_KEY, sides.stream().mapToInt(Enum::ordinal).sorted().toArray());
    }

    public static AutoExportSides read(CompoundTag tag) {
        if (!tag.contains(NBT_KEY)) return null;
        if (!tag.contains(NBT_KEY, Tag.TAG_INT_ARRAY)) throw new IllegalArgumentException("Invalid auto-export side list");
        int[] entries = tag.getIntArray(NBT_KEY);
        if (entries.length > RelativeSide.values().length) throw new IllegalArgumentException("Too many auto-export sides");
        EnumSet<RelativeSide> sides = EnumSet.noneOf(RelativeSide.class);
        for (int entry : entries) {
            if (entry < 0 || entry >= RelativeSide.values().length) throw new IllegalArgumentException("Invalid auto-export side");
            sides.add(RelativeSide.values()[entry]);
        }
        return new AutoExportSides(sides);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof AutoExportSides other)) return false;
        return Objects.equals(sides, other.sides());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(sides);
    }
}
