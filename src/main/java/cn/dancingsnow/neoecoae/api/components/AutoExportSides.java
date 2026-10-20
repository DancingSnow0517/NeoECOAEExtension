package cn.dancingsnow.neoecoae.api.components;

import appeng.api.orientation.RelativeSide;
import cn.dancingsnow.neoecoae.util.CodecUtils;
import com.mojang.serialization.Codec;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record AutoExportSides(Set<RelativeSide> sides) {

    public static final Codec<AutoExportSides> CODEC = Codec.list(CodecUtils.RELATIVE_SIDE_CODEC, 0, 6)
        .xmap(
            from -> new AutoExportSides(toEnumSet(from)),
            to -> to.sides.stream().toList()
        );

    private static EnumSet<RelativeSide> toEnumSet(List<RelativeSide> sides) {
        if (sides.isEmpty()) {
            return EnumSet.noneOf(RelativeSide.class);
        }
        return EnumSet.copyOf(sides);
    }

    public static final StreamCodec<FriendlyByteBuf, AutoExportSides> STREAM_CODEC = StreamCodec.of(
        (buf, autoExportSides) -> {
            for (RelativeSide side : RelativeSide.values()) {
                buf.writeBoolean(autoExportSides.sides.contains(side));
            }
        },
        buf -> {
            EnumSet<RelativeSide> sides = EnumSet.noneOf(RelativeSide.class);
            for (RelativeSide side : RelativeSide.values()) {
                if (buf.readBoolean()) {
                    sides.add(side);
                }
            }
            return new AutoExportSides(sides);
        }
    );

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof AutoExportSides(Set<RelativeSide> sides1))) return false;
        return Objects.equals(sides, sides1);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(sides);
    }
}
