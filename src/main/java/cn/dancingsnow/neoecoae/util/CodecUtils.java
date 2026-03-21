package cn.dancingsnow.neoecoae.util;

import appeng.api.orientation.RelativeSide;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

public class CodecUtils {
    public static Codec<RelativeSide> RELATIVE_SIDE_CODEC = Codec.INT.comapFlatMap(
        i -> {
            if (i < 0 || i >= RelativeSide.values().length) {
                return DataResult.error(() -> "Invalid RelativeSide index: " + i);
            }
            return DataResult.success(RelativeSide.values()[i]);
        },
        Enum::ordinal
    ).stable();
}
