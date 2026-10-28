package cn.dancingsnow.neoecoae.storage.bulk;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.api.storage.IECOBulkDisplayCell;
import java.math.BigInteger;
import java.util.List;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static cn.dancingsnow.neoecoae.storage.bulk.ECOBulkStorageCellTest.*;
import static org.junit.jupiter.api.Assertions.*;

class ECOBulkDisplayCellTest {
    @BeforeAll static void init() { ECOBulkStorageCellTest.bootstrap(); }
    @BeforeEach void install() { ECOCompressionService.install(List.of(iron(9))); }

    @Test void chainIsCompleteOrderedAndImmutable() {
        var cell = fixture(ingot).cell;
        assertEquals(List.of(nugget, ingot, block), cell.getCompressionChain(block));
        assertThrows(UnsupportedOperationException.class, () -> cell.getCompressionChain(ingot).clear());
        var paper = AEItemKey.of(Items.PAPER);
        assertEquals(List.of(paper), cell.getCompressionChain(paper));
    }

    @Test void effectiveCutoffUsesMarkerThenExplicitSetting() {
        var cell = fixture(ingot).cell;
        assertEquals(ingot, cell.getCompressionCutoff(nugget));
        cell.setCompressionCutoff(block);
        assertEquals(block, cell.getCompressionCutoff(nugget));
        assertEquals(block, cell.getCompressionCutoff(ingot));
    }

    @Test void existingExternalImplementationsHaveCompatibleDefaults() {
        IECOBulkDisplayCell external = (key, delta) -> key;
        assertEquals(List.of(ingot), external.getCompressionChain(ingot));
        assertEquals(ingot, external.getCompressionCutoff(ingot));
    }

    @Test void recoveringHistoricalStockReportsItsActualBaseCutoff() {
        var cell = fixture(ingot).cell;
        cell.insert(ingot, 10, Actionable.MODULATE, null);
        ECOCompressionService.install(List.of(iron(4)));
        var out = new KeyCounter();
        cell.getAvailableStacks(out);
        assertEquals(90L, out.get(nugget));
        assertEquals(0L, out.get(ingot));
        assertEquals(nugget, cell.getCompressionCutoff(ingot));
    }

    @Test void removedTopDenominationIsNotReportedAsEffectiveCutoff() {
        var cell = fixture(ingot).cell;
        cell.setCompressionCutoff(block);
        ECOCompressionService.install(List.of(new BulkUnits(List.of(nugget, ingot), List.of(BigInteger.ONE, BigInteger.valueOf(9)))));
        cell.insert(ingot, 10, Actionable.MODULATE, null);
        var out = new KeyCounter();
        cell.getAvailableStacks(out);
        assertEquals(10L, out.get(ingot));
        assertEquals(0L, out.get(block));
        assertEquals(ingot, cell.getCompressionCutoff(ingot));
    }

    @Test void staleCutoffOnEmptyCellUsesTheSameFallbackAsNewStock() {
        var f = fixture(ingot);
        f.cell.setCompressionCutoff(block);
        ECOCompressionService.install(List.of(new BulkUnits(List.of(nugget, ingot), List.of(BigInteger.ONE, BigInteger.valueOf(9)))));
        assertEquals(ingot, f.cell.getCompressionCutoff(nugget));
        assertEquals(ingot, f.restore(f.stack.copy()).getCompressionCutoff(nugget));
    }

    @Test void historicalRecoveryOverridesExplicitCutoffAndEndsWhenRecipesAreRestored() {
        var f = fixture(ingot);
        f.cell.insert(ingot, 10, Actionable.MODULATE, null);
        f.cell.setCompressionCutoff(block);
        ECOCompressionService.install(List.of(iron(4)));
        for (var key : List.of(nugget, ingot, block)) assertEquals(nugget, f.cell.getCompressionCutoff(key));
        assertEquals(nugget, f.restore(f.stack.copy()).getCompressionCutoff(block));
        ECOCompressionService.install(List.of(iron(9)));
        assertEquals(block, f.cell.getCompressionCutoff(ingot));
    }

    @Test void removedRecipesStillReportTheHistoricalBaseOfOccupiedStock() {
        var cell = fixture(ingot).cell;
        cell.insert(ingot, 10, Actionable.MODULATE, null);
        ECOCompressionService.install(List.of());
        assertEquals(nugget, cell.getCompressionCutoff(ingot));
        assertEquals(nugget, cell.getCompressionCutoff(block));
        var paper = AEItemKey.of(Items.PAPER);
        assertEquals(paper, cell.getCompressionCutoff(paper));
    }
}
