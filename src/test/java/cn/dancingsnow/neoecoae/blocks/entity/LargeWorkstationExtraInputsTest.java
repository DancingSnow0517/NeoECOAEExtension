package cn.dancingsnow.neoecoae.blocks.entity;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import com.moakiee.ae2lt.me.key.LightningKey;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LargeWorkstationExtraInputsTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @Test void partialExtractionIsOwnedAndRetryOnlyRequestsRemainder() {
        var storage = mock(MEStorage.class);
        var source = mock(IActionSource.class);
        var missing = new KeyCounter();
        var owned = new KeyCounter();
        var changes = new AtomicInteger();
        missing.add(LightningKey.EXTREME_HIGH_VOLTAGE, 28);
        when(storage.extract(LightningKey.EXTREME_HIGH_VOLTAGE, 28, Actionable.MODULATE, source)).thenReturn(10L);
        when(storage.extract(LightningKey.EXTREME_HIGH_VOLTAGE, 18, Actionable.MODULATE, source)).thenReturn(18L);
        assertFalse(LargeWorkstationExtraInputs.acquire(missing, owned, storage, source, changes::incrementAndGet));
        assertEquals(10, owned.get(LightningKey.EXTREME_HIGH_VOLTAGE));
        assertEquals(18, missing.get(LightningKey.EXTREME_HIGH_VOLTAGE));
        assertTrue(LargeWorkstationExtraInputs.acquire(missing, owned, storage, source, changes::incrementAndGet));
        assertEquals(28, owned.get(LightningKey.EXTREME_HIGH_VOLTAGE));
        assertEquals(2, changes.get());
        assertTrue(LargeWorkstationExtraInputs.acquire(missing, owned, storage, source, changes::incrementAndGet));
        verify(storage).extract(LightningKey.EXTREME_HIGH_VOLTAGE, 28, Actionable.MODULATE, source);
        verify(storage).extract(LightningKey.EXTREME_HIGH_VOLTAGE, 18, Actionable.MODULATE, source);
        verifyNoMoreInteractions(storage);
    }

    @Test void missingLightningNeverCreatesOwnedResources() {
        var missing = new KeyCounter();
        var owned = new KeyCounter();
        missing.add(LightningKey.HIGH_VOLTAGE, 4096);
        assertFalse(LargeWorkstationExtraInputs.acquire(missing, owned, mock(MEStorage.class), mock(IActionSource.class),
            () -> fail("No mutation should be saved")));
        assertEquals(0, owned.get(LightningKey.HIGH_VOLTAGE));
        assertEquals(4096, missing.get(LightningKey.HIGH_VOLTAGE));
    }
}
