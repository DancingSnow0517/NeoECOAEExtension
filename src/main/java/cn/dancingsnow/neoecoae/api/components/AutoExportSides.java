package cn.dancingsnow.neoecoae.api.components;

import appeng.api.orientation.RelativeSide;
import cn.dancingsnow.neoecoae.util.CodecUtils;
import com.mojang.serialization.Codec;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

public record AutoExportSides(Set<RelativeSide> sides) {

    public static final Codec<AutoExportSides> CODEC = Codec.list(CodecUtils.RELATIVE_SIDE_CODEC, 0, 6)
        .xmap(
            from -> new AutoExportSides(EnumSet.copyOf(from)),
            to -> to.sides.stream().toList()
        );

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
        if (!(o instanceof AutoExportSides other)) return false;
        return Objects.equals(sides, other.sides);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(sides);
    }
}
