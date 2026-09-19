package cn.dancingsnow.neoecoae.crafting.execution.bigorder;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ECOBigCraftingLedgerTest {
    @Test
    void largeOrderSurvivesRestartAndCompletesAcrossLongBoundary() {
        BigInteger total =
                BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.TWO).add(BigInteger.valueOf(7));
        var ledger = new ECOBigCraftingLedger(total);
        UUID first = UUID.randomUUID();
        ledger.bind(first, ledger.nextBatch(Long.MAX_VALUE));
        ledger = ECOBigCraftingLedger.read(ledger.write());
        assertEquals(total, ledger.total());
        assertEquals(total, ledger.remaining());
        assertEquals(Long.MAX_VALUE, ledger.batch());
        assertTrue(ledger.complete(first));
        assertFalse(ledger.complete(first));
        assertEquals(BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.valueOf(7)), ledger.remaining());
        UUID second = UUID.randomUUID();
        ledger.bind(second, ledger.nextBatch(Long.MAX_VALUE));
        assertTrue(ledger.complete(second));
        assertEquals(7, ledger.nextBatch(Long.MAX_VALUE));
        UUID third = UUID.randomUUID();
        ledger.bind(third, 7);
        assertTrue(ledger.complete(third));
        assertEquals(BigInteger.ZERO, ledger.remaining());
        assertEquals(BigInteger.ZERO, ECOBigCraftingLedger.read(ledger.write()).remaining());
    }

    @Test
    void rejectedOrUnrelatedCompletionCannotConsumeTheOrder() {
        var ledger = new ECOBigCraftingLedger(BigInteger.TEN.pow(40));
        UUID job = UUID.randomUUID();
        ledger.bind(job, 123);
        assertFalse(ledger.complete(UUID.randomUUID()));
        assertEquals(BigInteger.TEN.pow(40), ledger.remaining());
        assertThrows(IllegalStateException.class, () -> ledger.nextBatch(5));
        assertThrows(IllegalArgumentException.class, () -> ledger.bind(UUID.randomUUID(), 5));
    }

    @Test
    void corruptPersistedAccountingIsRejected() {
        var ledger = new ECOBigCraftingLedger(BigInteger.TEN);
        var tag = ledger.write();
        tag.putString("remaining", "11");
        assertThrows(IllegalArgumentException.class, () -> ECOBigCraftingLedger.read(tag));
        tag.putString("remaining", "5");
        tag.putUUID("job", UUID.randomUUID());
        tag.putLong("batch", 6);
        assertThrows(IllegalArgumentException.class, () -> ECOBigCraftingLedger.read(tag));
    }
}
