package cn.dancingsnow.neoecoae.gui.ldlib.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.dancingsnow.neoecoae.gui.ldlib.state.NEStorageUiState;
import cn.dancingsnow.neoecoae.gui.ldlib.state.NEStorageUiTypeState;
import java.math.BigInteger;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class NEStorageModelsTest {
    @Test
    void metricsKeepKnownTypesFirstAndIncludeCustomTypes() {
        var custom = type("test:mana", 3, 9, 30, 90, "300000000000000000000");
        var fluids = type("neoecoae:fluids", 2, 8, 20, 80, "20");
        var items = type("neoecoae:items", 1, 7, 10, 70, "10");

        var metrics = NEStorageMetricsModel.from(state(List.of(custom, fluids, items)));

        assertEquals("neoecoae:items", metrics.types().get(0).key());
        assertEquals("neoecoae:fluids", metrics.types().get(1).key());
        assertEquals("test:mana", metrics.types().get(2).key());
        assertEquals("300000000000000000000", metrics.types().get(2).usedAmount());
        assertEquals(3, NEStorageMetricsModel.activeMetrics(metrics).size());
    }

    @Test
    void totalsSaturateAndHugeAmountsRejectInvalidInput() {
        var state = state(List.of(
                type("neoecoae:items", Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE, "10"),
                type("neoecoae:fluids", 4, 5, 6, 7, "999999999999999999999999999999")));

        assertEquals(Long.MAX_VALUE, state.totalUsedTypes());
        assertEquals(Long.MAX_VALUE, state.totalTypes());
        assertEquals(Long.MAX_VALUE, state.totalUsedBytes());
        assertEquals(Long.MAX_VALUE, state.totalBytes());
        assertEquals(
                new BigInteger("1000000000000000000000000000009"), NEStorageTextFormatter.totalInfiniteAmount(state));
        assertEquals(BigInteger.ZERO, NEStorageTextFormatter.parseAmount("not-a-number"));
        assertEquals(BigInteger.ZERO, NEStorageTextFormatter.parseAmount("-2"));
    }

    @Test
    void usagePercentHandlesEmptyAndInfiniteRanges() {
        assertEquals(0.0D, NEStorageUsageModel.percent(5, 0));
        assertEquals(0.0D, NEStorageUsageModel.percent(-5, 100));
        assertEquals(0.5D, NEStorageUsageModel.percent(50, 100));
        assertEquals(1.0D, NEStorageUsageModel.percent(200, 100));
        assertTrue(NEStorageUsageModel.percent(Long.MAX_VALUE, Long.MAX_VALUE) <= 1.0D);
    }

    private static NEStorageUiState state(List<NEStorageUiTypeState> types) {
        return new NEStorageUiState(
                BlockPos.ZERO,
                types,
                List.of(),
                List.of(),
                0,
                1,
                0,
                0,
                0,
                0,
                false,
                false,
                false,
                false,
                0,
                0,
                true,
                true);
    }

    private static NEStorageUiTypeState type(
            String id, long usedTypes, long totalTypes, long usedBytes, long totalBytes, String usedAmount) {
        return new NEStorageUiTypeState(
                ResourceLocation.parse(id), id, usedTypes, totalTypes, usedBytes, totalBytes, usedAmount);
    }
}
