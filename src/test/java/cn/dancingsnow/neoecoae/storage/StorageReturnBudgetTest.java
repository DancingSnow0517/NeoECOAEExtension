package cn.dancingsnow.neoecoae.storage;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StorageReturnBudgetTest {
    @Test void allCallersShareOneOperationLimitAndTheNextTickResumes() {
        var time = new AtomicLong();
        var budget = new StorageReturnBudget(time::get);
        for (int i = 0; i < 2048; i++) assertTrue(budget.acquire(10));
        assertFalse(budget.acquire(10));
        assertTrue(budget.acquire(11));
    }
    @Test void elapsedTimeStopsFurtherCallsWithoutReservingAnyResource() {
        var time = new AtomicLong();
        var budget = new StorageReturnBudget(time::get);
        assertTrue(budget.available(10));
        time.set(2_000_000);
        assertFalse(budget.acquire(10));
        assertTrue(budget.acquire(11));
    }
}
