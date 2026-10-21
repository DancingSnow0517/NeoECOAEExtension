package cn.dancingsnow.neoecoae.crafting.execution.worker;

import static org.junit.jupiter.api.Assertions.*;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import cn.dancingsnow.neoecoae.api.me.bigorder.ECOExactInventory;
import cn.dancingsnow.neoecoae.platform.PlatformTestBootstrap;
import java.math.BigInteger;
import java.util.List;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ForgeExactLedgerPersistenceTest {
    @BeforeAll static void bootstrap() throws Exception { PlatformTestBootstrap.initializeKeyRegistry(); }

    @Test void overflowInventoryReloadKeepsExactTotalAndLongCompatibilityWindow() {
        var key = AEItemKey.of(Items.STONE);
        var inventory = new ECOExactInventory(ignored -> {});
        inventory.setEnabled(true);
        inventory.restore(java.util.Map.of(key, BigInteger.TEN.pow(40)));
        assertEquals(Long.MAX_VALUE, inventory.list.get(key));
        assertEquals(Long.MAX_VALUE, inventory.extract(key, Long.MAX_VALUE, Actionable.SIMULATE));
        assertEquals(BigInteger.TEN.pow(40), inventory.amount(key));
        var reopened = new ECOExactInventory(ignored -> {});
        reopened.readFromNBT(inventory.writeToNBT());
        assertTrue(reopened.isEnabled());
        assertEquals(inventory.snapshot(), reopened.snapshot());
        assertEquals(Long.MAX_VALUE, reopened.extract(key, Long.MAX_VALUE, Actionable.MODULATE));
        assertEquals(BigInteger.TEN.pow(40).subtract(BigInteger.valueOf(Long.MAX_VALUE)), reopened.amount(key));
    }

    @Test void unknownExactKeyRejectsReloadBeforeChangingExistingCustody() {
        var key = AEItemKey.of(Items.STONE);
        var inventory = new ECOExactInventory(ignored -> {});
        inventory.setEnabled(true);
        inventory.restore(java.util.Map.of(key, BigInteger.TEN.pow(40)));
        var saved = inventory.writeToNBT();
        saved.getCompound(0).putString("#c", "missing_mod:resource");
        assertThrows(IllegalArgumentException.class, () -> inventory.readFromNBT(saved));
        assertEquals(BigInteger.TEN.pow(40), inventory.amount(key));
    }

    @Test void invalidExactAmountCannotReplaceTheExistingLedgerWithALongWindow() {
        var key = AEItemKey.of(Items.STONE);
        var inventory = new ECOExactInventory(ignored -> {});
        inventory.setEnabled(true);
        inventory.restore(java.util.Map.of(key, BigInteger.TEN.pow(40)));
        var saved = inventory.writeToNBT();
        saved.getCompound(0).putString("ecoExactAmount", "-1");
        assertThrows(IllegalArgumentException.class, () -> inventory.readFromNBT(saved));
        assertEquals(BigInteger.TEN.pow(40), inventory.amount(key));
    }

    @Test void acceptedOutputWindowIsNotReplayedAfterSaveReload() {
        var key = AEItemKey.of(Items.DIAMOND);
        var crafts = BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.valueOf(13));
        var ledger = new ECOExactVirtualLedger(crafts, List.of(), List.of(new GenericStack(key, 1)), List.of());
        var received = new java.util.concurrent.atomic.AtomicReference<>(BigInteger.ZERO);
        assertFalse(ledger.drain(true, (what, amount) -> {
            received.updateAndGet(total -> total.add(BigInteger.valueOf(amount)));
            return amount;
        }, () -> {}));
        assertEquals(BigInteger.valueOf(Long.MAX_VALUE), received.get());
        var reopened = ECOExactVirtualLedger.read(ledger.write());
        assertEquals(BigInteger.valueOf(13), reopened.snapshot(true).get(key));
        assertTrue(reopened.drain(true, (what, amount) -> {
            received.updateAndGet(total -> total.add(BigInteger.valueOf(amount)));
            return amount;
        }, () -> {}));
        assertEquals(crafts, received.get());
    }

    @Test void invalidExternalAcceptanceCannotDebitExactCustody() {
        var key = AEItemKey.of(Items.DIAMOND);
        var ledger = new ECOExactVirtualLedger(BigInteger.TEN, List.of(), List.of(new GenericStack(key, 1)), List.of());
        assertThrows(IllegalStateException.class,
            () -> ledger.drain(true, (what, amount) -> amount + 1, () -> {}));
        assertThrows(IllegalStateException.class,
            () -> ledger.drainExact(true, (what, amount) -> amount.negate(), () -> {}));
        assertEquals(BigInteger.TEN, ledger.snapshot(true).get(key));
    }

    @Test void remaindersAndOutputsShareExactCustodyAcrossReload() {
        var key = AEItemKey.of(Items.BUCKET);
        var crafts = BigInteger.TEN.pow(25);
        var ledger = new ECOExactVirtualLedger(crafts, List.of(new GenericStack(AEItemKey.of(Items.WATER_BUCKET), 1)),
            List.of(new GenericStack(key, 2)), List.of(new GenericStack(key, 1)));
        var reopened = ECOExactVirtualLedger.read(ledger.write());
        assertEquals(crafts.multiply(BigInteger.valueOf(3)), reopened.snapshot(true).get(key));
        assertEquals(ledger.snapshot(false), reopened.snapshot(false));
    }
}
