package cn.dancingsnow.neoecoae.api.components;

import appeng.api.orientation.RelativeSide;
import com.google.gson.JsonArray;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AutoExportSidesTest {

    @Test
    void decodesEmptySideList() {
        AutoExportSides decoded = AutoExportSides.CODEC.parse(JsonOps.INSTANCE, new JsonArray())
            .getOrThrow();

        assertEquals(Set.of(), decoded.sides());
    }

    @Test
    void decodesNonEmptySideList() {
        JsonArray sides = new JsonArray();
        sides.add(RelativeSide.TOP.ordinal());
        sides.add(RelativeSide.LEFT.ordinal());

        AutoExportSides decoded = AutoExportSides.CODEC.parse(JsonOps.INSTANCE, sides)
            .getOrThrow();

        assertEquals(EnumSet.of(RelativeSide.TOP, RelativeSide.LEFT), decoded.sides());
    }
}
